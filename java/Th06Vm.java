import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * In-process Java interpreter for the original TH06 ECL scripts. Ported from the MIT implementation
 * credited in licenses/Touhou6-engine-MIT.txt. Float arithmetic, RNG draw order, callback
 * boundaries and pool limits are explicit so replay does not depend on Swing timing or external
 * processes.
 */
final class Th06Vm {
    static final float PI = (float) Math.PI, TAU = PI * 2, W = 384, H = 448;
    static final int[] BASES = {14, 30, 46, 62, 78, 94, 110, 118, 122, 146};

    static float sin(float a) {
        return (float) Math.sin(a);
    }

    static float cos(float a) {
        return (float) Math.cos(a);
    }

    static float atan(float y, float x) {
        return (float) Math.atan2(y, x);
    }

    static float sqrt(float a) {
        return (float) Math.sqrt(a);
    }

    static float norm(float a) {
        if (!Float.isFinite(a)) return a;
        while (a > PI) a -= TAU;
        while (a < -PI) a += TAU;
        return a;
    }

    static float clamp(float a, float lo, float hi) {
        return Math.max(lo, Math.min(hi, a));
    }

    static int clamp(int a, int lo, int hi) {
        return Math.max(lo, Math.min(hi, a));
    }

    static float rank(float lo, float hi, int rank) {
        return rank * (hi - lo) / 32f + lo;
    }

    static int rank(int lo, int hi, int rank) {
        return rank * (hi - lo) / 32 + lo;
    }

    static final class Rng {
        int seed;

        Rng(int seed) {
            this.seed = seed & 65535;
        }

        int u16() {
            int a = ((seed ^ 0x9630) - 0x6553) & 65535;
            return seed = (((a & 0xc000) >>> 14) + a * 4) & 65535;
        }

        int u32() {
            return (u16() << 16) | u16();
        }

        float unit() {
            return (float) Integer.toUnsignedLong(u32()) / (float) 0xffffffffL;
        }

        int u32Range(int range) {
            return range == 0 ? 0 : Integer.remainderUnsigned(u32(), range);
        }

        int u16Range(int range) {
            return range == 0 ? 0 : u16() % range;
        }

        float range(float range) {
            return unit() * range;
        }
    }

    static final class Script {
        final ByteBuffer data;
        final int[] subs;
        final int timeline;
        final Map<Integer, Ins> instructions = new HashMap<>();

        Script(Path path) throws IOException {
            byte[] bytes = Files.readAllBytes(path);
            new Th06Ecl(bytes); // Validate all code boundaries before executing anything.
            data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            subs = new int[Short.toUnsignedInt(data.getShort(0))];
            timeline = data.getInt(4);
            for (int n = 0; n < subs.length; n++) subs[n] = data.getInt(16 + n * 4);
        }

        Ins at(int pc) {
            if (pc < 0 || pc + 12 > data.limit()) return null;
            return instructions.computeIfAbsent(
                    pc,
                    p -> {
                        int size = Short.toUnsignedInt(data.getShort(p + 6));
                        if (size < 12 || p + size > data.limit())
                            throw new IllegalStateException("Invalid ECL PC " + p);
                        return new Ins(
                                data,
                                p,
                                data.getInt(p),
                                Short.toUnsignedInt(data.getShort(p + 4)),
                                size,
                                Byte.toUnsignedInt(data.get(p + 9)));
                    });
        }
    }

    record Ins(ByteBuffer bytes, int pc, int time, int op, int size, int mask) {
        int i(int n) {
            return bytes.getInt(pc + 12 + n * 4);
        }

        float f(int n) {
            return bytes.getFloat(pc + 12 + n * 4);
        }

        int s(int byteOffset) {
            return bytes.getShort(pc + 12 + byteOffset);
        }

        int b(int byteOffset) {
            return Byte.toUnsignedInt(bytes.get(pc + 12 + byteOffset));
        }
    }

    static final class Context {
        int pc, time, sub, cmp, repeat = -1;
        int[] ints = new int[8];
        float[] floats = new float[4];

        Context copy() {
            Context c = new Context();
            c.pc = pc;
            c.time = time;
            c.sub = sub;
            c.cmp = cmp;
            c.repeat = repeat;
            c.ints = ints.clone();
            c.floats = floats.clone();
            return c;
        }
    }

    static final class Props {
        int sprite, color, count1 = 1, count2 = 1, mode, flags, sfx = -1;
        float[] pos = new float[2], exF = new float[4];
        int[] exI = new int[4];
        float a1, a2, s1, s2;
    }

    static final class Shot {
        int id, sprite, color, delay, oob, timer, flags, interval, repeats, count, fade;
        float[] pos, accel = new float[2], exF = new float[2];
        float angle, speed, width, height, factor;
    }

    static final class Laser {
        boolean live = true;
        float[] pos = new float[2];
        float angle, speed, start, end, length, width;
        int lead, duration, fade, hitStart, state, timer, color;
    }

    record Spawn(int sub, float[] pos, int life, int item, int score, boolean mirror) {}

    static final class Effect {
        final Th06Anm.Runner runner;
        final int kind;
        int timer, pop = -1;
        boolean bubble;

        Effect(Th06Anm.Runner runner, int kind, boolean bubble) {
            this.runner = runner;
            this.kind = kind;
            this.bubble = bubble;
        }
    }

    static final class World {
        final Rng rng;
        final int difficulty, character, shot;
        int nextBullet, rank = 16, randomItem, randomTable;
        float[] player = {192, 384}, heights = new float[10], widths = new float[10];
        final List<Shot> bullets = new ArrayList<>();
        final List<Laser> lasers = new ArrayList<>();
        final List<Integer> spells = new ArrayList<>();
        final ArrayDeque<Spawn> spawns = new ArrayDeque<>();
        final List<Effect> effects = new ArrayList<>();
        final Map<Integer, List<Th06Anm.Instruction>> effectScripts;
        int endedCards;
        boolean killTrash, bossPresent, timeStopped, bomb, cancel;

        World(
                int difficulty,
                int seed,
                int route,
                Map<Integer, List<Th06Anm.Instruction>> effectScripts) {
            this.difficulty = difficulty;
            rng = new Rng(seed);
            character = route / 2;
            shot = route % 2;
            this.effectScripts = effectScripts;
        }

        int effect(int kind, boolean bubble) {
            int script =
                    switch (kind) {
                        case 0, 1, 2, 3 -> kind + 3;
                        case 4, 5, 6, 7, 8, 9, 10, 11 -> kind + 5;
                        case 12 -> 17;
                        case 13, 14, 15 -> 18;
                        case 17 -> 7;
                        case 18 -> 8;
                        case 19 -> 19;
                        default -> 0;
                    };
            var code = effectScripts.get(script);
            if (code == null) return -1;
            Effect e = new Effect(new Th06Anm.Runner(code), kind, bubble);
            for (int i = 0; i < effects.size(); i++)
                if (effects.get(i) == null) {
                    effects.set(i, e);
                    return i;
                }
            effects.add(e);
            return effects.size() - 1;
        }

        void particles(int kind, int count) {
            for (int i = 0; i < Math.max(0, count); i++) effect(kind, false);
        }

        void updateEffects() {
            for (int i = 0; i < effects.size(); i++) {
                Effect e = effects.get(i);
                if (e == null) continue;
                // Presentation positions are not rendered. Preserve lifetime and every RNG draw.
                if (e.timer == 0) {
                    if (e.kind >= 3 && e.kind <= 11) {
                        rng.unit();
                        rng.unit();
                    } else if (e.kind == 17 || e.kind == 18) rng.unit();
                }
                if (e.kind >= 13 && e.kind <= 15 && e.pop >= 0 && ++e.pop >= 16) {
                    effects.set(i, null);
                    continue;
                }
                e.runner.tick();
                e.timer++;
                if (e.runner.dead) effects.set(i, null);
            }
        }

        int laser(Laser l) {
            for (int i = 0; i < lasers.size(); i++)
                if (!lasers.get(i).live) {
                    lasers.set(i, l);
                    return i;
                }
            if (lasers.size() >= 64) return -1;
            lasers.add(l);
            return lasers.size() - 1;
        }

        void clear() {
            bullets.clear();
            for (Laser l : lasers) l.live = false;
        }
    }

    final Script script;
    final World world;
    final List<Mob> enemies = new ArrayList<>();
    final List<Integer> generations = new ArrayList<>();
    final Map<Integer, List<Th06Anm.Instruction>> animations = new HashMap<>();
    final Map<Integer, Th06Anm.Sprite> sizes = new HashMap<>();
    final Map<Integer, Float> fractionalDamage = new HashMap<>();
    int frame;
    boolean mainStarted, closed;

    Th06Vm(Path path, int entry, int difficulty, int seed, int route) throws IOException {
        script = new Script(path);
        Path dir = path.getParent();
        Th06Anm bulletAnm = Th06Anm.read(dir.resolve("etama3.anm"));
        world = new World(difficulty, seed, route, Th06Anm.read(dir.resolve("etama4.anm")).scripts);
        for (int i = 0; i < 10; i++) {
            var sp = bulletAnm.sprites.get(BASES[i]);
            world.heights[i] = sp == null ? 8 : sp.height();
            world.widths[i] = sp == null ? 8 : sp.width();
        }
        String stage = path.getFileName().toString().replaceAll("\\D", "");
        for (String name : new String[] {"stg" + stage + "enm.anm", "stg" + stage + "enm2.anm"}) {
            Path file = dir.resolve(name);
            if (!Files.isRegularFile(file)) continue;
            Th06Anm a = Th06Anm.read(file);
            animations.putAll(a.scripts);
            sizes.putAll(a.sprites);
        }
        float[] pos = {192, 96, 0};
        int life = -1;
        ByteBuffer data = script.data;
        for (int off = script.timeline, guard = 0;
                guard++ < 20000 && off >= 0 && off + 8 <= data.limit(); ) {
            int time = data.getShort(off),
                    op = data.getShort(off + 4),
                    size = data.getShort(off + 6);
            if (time < 0 || size < 8 || off + size > data.limit()) break;
            if (op >= 0 && op <= 7 && data.getShort(off + 2) == entry && size >= 28) {
                pos =
                        new float[] {
                            data.getFloat(off + 8), data.getFloat(off + 12), data.getFloat(off + 16)
                        };
                if (op % 2 == 0) life = data.getShort(off + 20);
                break;
            }
            off += size;
        }
        for (float p : pos)
            if (!Float.isFinite(p) || p < -900) {
                pos = new float[] {192, 96, 0};
                break;
            }
        insert(spawn(new Spawn(entry, pos, life, -1, -1, false)));
    }

    Mob spawn(Spawn req) {
        if (req.sub < 0 || req.sub >= script.subs.length) return null;
        Mob e = new Mob();
        if (req.life >= 0) e.life = req.life;
        e.pos = req.pos.clone();
        e.mirror = req.mirror;
        e.item = req.item;
        e.ctx.pc = script.subs[req.sub];
        e.ctx.sub = req.sub;
        e.run(script, world);
        if (req.life >= 0) e.life = req.life;
        if (req.score >= 0) e.score = req.score;
        e.healthStart = e.life;
        return e;
    }

    void insert(Mob e) {
        if (e == null) return;
        for (int i = 0; i < enemies.size(); i++)
            if (!enemies.get(i).occupied) {
                enemies.set(i, e);
                generations.set(i, generations.get(i) + 1);
                return;
            }
        if (enemies.size() >= 256) throw new IllegalStateException("Enemy pool exhausted");
        enemies.add(e);
        generations.add(1);
    }

    int id(int slot) {
        return (generations.get(slot) << 8) | slot;
    }

    void flush() {
        int guard = 0;
        while (!world.spawns.isEmpty()) {
            if (++guard >= 1024) throw new IllegalStateException("Recursive enemy spawn");
            insert(spawn(world.spawns.removeFirst()));
        }
    }

    void killTrash() {
        if (!world.killTrash) return;
        world.killTrash = false;
        for (Mob e : enemies)
            if (e.occupied && !e.boss) {
                e.life = 0;
                if (!e.interactable && e.deathCallback >= 0) {
                    e.callSub(script, e.deathCallback);
                    e.deathCallback = -1;
                }
            }
    }

    void step(
            float px, float py, boolean bomb, Map<Integer, Double> damage, Set<Integer> consumed) {
        if (closed) throw new IllegalStateException("Closed TH06 simulation");
        world.player = new float[] {px, py};
        world.bomb = bomb;
        if (bomb) world.clear();
        for (var hit : damage.entrySet()) {
            int slot = hit.getKey() & 255;
            float amount = hit.getValue().floatValue();
            if (slot >= enemies.size() || id(slot) != hit.getKey()) continue;
            Mob e = enemies.get(slot);
            if (e.occupied
                    && e.interactable
                    && e.damageable
                    && Float.isFinite(amount)
                    && amount > 0) {
                float remainder = fractionalDamage.getOrDefault(hit.getKey(), 0f) + amount;
                int applied = (int) Math.floor(remainder);
                fractionalDamage.put(hit.getKey(), remainder - applied);
                e.damage(applied);
            }
        }
        world.bullets.removeIf(b -> consumed.contains(b.id));
        frame++;
        if (frame >= 120 && frame % 60 == 0 && !mainStarted) enemies.get(0).interruptIntro(script);
        for (int i = 0; i < enemies.size(); i++) {
            Mob e = enemies.get(i);
            if (!e.occupied) continue;
            if (e.life <= 0 && e.interactable) {
                e.deathEffects(world);
                e.onDeath(script, world);
            }
            if (!e.occupied) continue;
            e.move();
            Th06Anm.Sprite size = e.anm == null ? null : sizes.get(e.anm.sprite);
            if (!e.bounds(size == null ? 0 : size.width(), size == null ? 0 : size.height())) {
                e.despawn(world);
                continue;
            }
            e.callbacks(script, world);
            killTrash();
            e.run(script, world);
            e.clamp();
            killTrash();
            flush();
            if (!world.timeStopped) e.bossTimer++;
            if (e.anmDirty) {
                e.anmDirty = false;
                var code = animations.get(e.anmScript);
                e.anm = code == null ? null : new Th06Anm.Runner(code);
            }
            if (e.pendingInterrupt >= 0 && e.anm != null) e.anm.interrupt(e.pendingInterrupt);
            e.pendingInterrupt = -1;
            if (e.anm != null) {
                e.anm.tick();
                e.anm.resolve(world.rng);
            }
            for (int s = 0; s < 8; s++) {
                if (e.subDirty[s]) {
                    e.subDirty[s] = false;
                    var code = animations.get(e.subScripts[s]);
                    e.subAnm[s] = code == null ? null : new Th06Anm.Runner(code);
                }
                if (e.subAnm[s] != null) {
                    e.subAnm[s].tick();
                    e.subAnm[s].resolve(world.rng);
                }
            }
        }
        flush();
        world.updateEffects();
        physics(world);
        for (Mob e : enemies)
            if (e.boss && e.occupied && e.damageable && e.interactable && e.life > 0)
                mainStarted = true;
    }

    Th06Runtime.Snapshot snapshot() {
        Mob root = enemies.get(0);
        boolean done =
                mainStarted
                        && (!root.occupied
                                || (root.life <= 1
                                        && !root.damageable
                                        && !root.collidable
                                        && !world.bossPresent));
        if (world.cancel) {
            world.clear();
            world.cancel = false;
        }
        List<Th06Runtime.ActorState> actors = new ArrayList<>();
        for (int i = 0; i < enemies.size(); i++) {
            Mob e = enemies.get(i);
            if (!e.occupied) continue;
            int floor = Math.max(0, e.lifeThreshold);
            int flags =
                    (e.interactable ? 1 : 0)
                            | (e.damageable ? 2 : 0)
                            | (e.collidable ? 4 : 0)
                            | (e.invisible ? 8 : 0)
                            | (e.boss ? 16 : 0);
            actors.add(
                    new Th06Runtime.ActorState(
                            id(i),
                            e.pos[0],
                            e.pos[1],
                            Math.max(0, e.life - floor),
                            Math.max(1, Math.max(e.healthStart, e.life) - floor),
                            e.hitbox[0],
                            e.hitbox[1],
                            flags));
        }
        List<Th06Runtime.BulletState> shots = new ArrayList<>(world.bullets.size());
        for (Shot b : world.bullets)
            shots.add(
                    new Th06Runtime.BulletState(
                            b.id,
                            b.pos[0],
                            b.pos[1],
                            b.angle,
                            b.sprite,
                            b.width,
                            b.height,
                            b.delay == 0 && b.fade == 0));
        List<Th06Runtime.Beam> beams = new ArrayList<>();
        for (Laser l : world.lasers)
            if (l.live) {
                float x1 = l.pos[0] + cos(l.angle) * l.start,
                        y1 = l.pos[1] + sin(l.angle) * l.start,
                        x2 = l.pos[0] + cos(l.angle) * l.end,
                        y2 = l.pos[1] + sin(l.angle) * l.end;
                beams.add(
                        new Th06Runtime.Beam(
                                Th06Runtime.LEFT + x1 * Th06Runtime.SCALE,
                                Th06Runtime.TOP + y1 * Th06Runtime.SCALE,
                                Th06Runtime.LEFT + x2 * Th06Runtime.SCALE,
                                Th06Runtime.TOP + y2 * Th06Runtime.SCALE,
                                l.width * Th06Runtime.SCALE,
                                l.state == 1 || (l.state == 0 && l.timer >= l.hitStart),
                                l.color));
            }
        var snapshot =
                new Th06Runtime.Snapshot(
                        done ? 1 : 0,
                        root.phase,
                        actors,
                        shots,
                        beams,
                        List.copyOf(world.spells),
                        world.endedCards);
        world.spells.clear();
        world.endedCards = 0;
        return snapshot;
    }

    void close() {
        closed = true;
        world.clear();
        world.effects.clear();
    }

    static final class Mob {
        Context ctx = new Context();
        Context[] stack = new Context[8];
        int depth;
        float[] pos = new float[3],
                hitbox = {12, 12},
                axis = new float[2],
                offset = new float[2],
                interp = new float[2],
                interpStart = new float[2];
        float[] lower = new float[2],
                upper = {W, H},
                starAngle = new float[6],
                starEnemy = new float[2],
                starPlayer = new float[2];
        float angle, angular, speed, acceleration, rankLow = -.5f, rankHigh = .5f, wingAngle;
        int rank1Low, rank1High, rank2Low, rank2High, moveTimer, moveStart, mode, ease;
        int life = 1,
                healthStart = 1,
                score = 100,
                bossTimer,
                phase,
                interval,
                shootTimer,
                wingTimer,
                batTimer;
        int[] interrupts = new int[8],
                lasers = new int[32],
                poses = new int[5],
                subScripts = new int[8];
        boolean[] subDirty = new boolean[8];
        Th06Anm.Runner[] subAnm = new Th06Anm.Runner[8];
        Th06Anm.Runner anm;
        int runInterrupt = -1,
                laserStore,
                deathCallback = -1,
                lifeThreshold = -1,
                lifeSub = -1,
                timerThreshold = -1,
                timerSub = -1;
        int deathMode,
                item = -1,
                deathAnm1,
                deathAnm2,
                anmScript = -1,
                poseState = 255,
                pendingInterrupt = -1,
                spellCount;
        boolean occupied = true,
                interactable = true,
                collidable = true,
                damageable = true,
                boss,
                invisible,
                shootDisabled,
                mirror,
                clampPos,
                noStack,
                survival,
                beenInside,
                deathFx,
                anmDirty;
        Props props = new Props();
        List<Integer> effects = new ArrayList<>();

        Mob() {
            Arrays.fill(interrupts, -1);
            Arrays.fill(lasers, -1);
            Arrays.fill(poses, -1);
            Arrays.fill(subScripts, -1);
            for (int i = 0; i < 8; i++) stack[i] = new Context();
        }

        void callSub(Script script, int sub) {
            if (sub >= 0 && sub < script.subs.length) {
                ctx.pc = script.subs[sub];
                ctx.time = 0;
                ctx.sub = sub;
            }
        }

        int integer(int raw, World w) {
            if (raw <= -10001 && raw >= -10004) return ctx.ints[-10001 - raw];
            if (raw <= -10005 && raw >= -10008) return (int) ctx.floats[-10005 - raw];
            if (raw <= -10009 && raw >= -10012) return ctx.ints[-10009 - raw + 4];
            return switch (raw) {
                case -10013 -> w.difficulty;
                case -10014 -> w.rank;
                case -10022 -> bossTimer;
                case -10024 -> life;
                case -10025 -> 0;
                default -> raw;
            };
        }

        float number(float raw, World w) {
            int id = (int) raw;
            if (raw != Math.floor(raw) || id < -10025 || id > -10001) return raw;
            if (id >= -10012 && id <= -10001) {
                if (id >= -10004) return ctx.ints[-10001 - id];
                if (id >= -10008) return ctx.floats[-10005 - id];
                return ctx.ints[-10009 - id + 4];
            }
            return switch (id) {
                case -10013 -> w.difficulty;
                case -10014 -> w.rank;
                case -10015 -> pos[0];
                case -10016 -> pos[1];
                case -10017 -> pos[2];
                case -10018 -> w.player[0];
                case -10019 -> w.player[1];
                case -10020 -> 0;
                case -10021 -> aim(w);
                case -10023 -> {
                    float dx = w.player[0] - pos[0], dy = w.player[1] - pos[1];
                    yield sqrt(dx * dx + dy * dy);
                }
                default -> raw;
            };
        }

        void set(int id, int integer, float number) {
            if (id >= -10004 && id <= -10001) ctx.ints[-10001 - id] = integer;
            else if (id >= -10008 && id <= -10005) ctx.floats[-10005 - id] = number;
            else if (id >= -10012 && id <= -10009) ctx.ints[-10009 - id + 4] = integer;
            else if (id >= -10017 && id <= -10015) pos[-10015 - id] = number;
            else if (id == -10022) bossTimer = integer;
            else if (id == -10024) life = integer;
        }

        float aim(World w) {
            return atan(w.player[1] - pos[1], w.player[0] - pos[0]);
        }

        void damage(int amount) {
            int next = (int) Math.max(Integer.MIN_VALUE, (long) life - amount);
            life =
                    lifeThreshold > 0 && life > lifeThreshold && next <= lifeThreshold
                            ? lifeThreshold - 1
                            : next;
        }

        void interruptIntro(Script script) {
            if (!boss) return;
            int start = script.subs[ctx.sub], end = script.data.limit();
            for (int off : script.subs) if (off > start) end = Math.min(end, off);
            for (int off = start; off < end; ) {
                Ins i = script.at(off);
                if (i == null) break;
                if (i.op == 109 && i.i(1) == 0 && interrupts[0] >= 0) {
                    runInterrupt = 0;
                    break;
                }
                off += i.size;
            }
        }

        void run(Script script, World w) {
            for (int guard = 0; guard < 10000; guard++) {
                if (runInterrupt >= 0) {
                    Ins i = script.at(ctx.pc);
                    if (i == null) return;
                    ctx.pc += i.size;
                    if (!noStack) stack[depth] = ctx.copy();
                    callSub(script, interrupts[runInterrupt & 7]);
                    if (depth < 7) depth++;
                    runInterrupt = -1;
                    continue;
                }
                Ins i = script.at(ctx.pc);
                if (i == null) {
                    despawn(w);
                    return;
                }
                if (ctx.time == i.time) {
                    if ((i.mask & (1 << w.difficulty)) != 0) {
                        int flow = exec(script, w, i);
                        if (flow == 1) continue;
                        if (flow == 2) {
                            despawn(w);
                            return;
                        }
                    }
                    ctx.pc += i.size;
                    continue;
                }
                if (mode == 1) {
                    angle = norm(angle + angular);
                    speed += acceleration;
                    axis = new float[] {cos(angle) * speed, sin(angle) * speed};
                } else if (mode == 2) {
                    moveTimer--;
                    float f = (float) moveTimer / moveStart;
                    if (f >= 1) f = 1;
                    f =
                            switch (ease) {
                                case 0 -> 1 - f;
                                case 1 -> 1 - f * f;
                                case 2 -> 1 - f * f * f * f;
                                case 3 -> (1 - f) * (1 - f);
                                default -> (1 - f) * (1 - f) * (1 - f) * (1 - f);
                            };
                    axis =
                            new float[] {
                                f * interp[0] + interpStart[0] - pos[0],
                                f * interp[1] + interpStart[1] - pos[1]
                            };
                    angle = atan(axis[1], axis[0]);
                    if (moveTimer <= 0) {
                        mode = 0;
                        pos[0] = interpStart[0] + interp[0];
                        pos[1] = interpStart[1] + interp[1];
                        axis = new float[2];
                    }
                }
                if (life > 0) {
                    if (interval > 0 && ++shootTimer >= interval) {
                        props.pos = new float[] {pos[0] + offset[0], pos[1] + offset[1]};
                        fire(w, props);
                        shootTimer = 0;
                    }
                    if (ctx.repeat >= 0) extra(ctx.repeat, 0, w);
                }
                ctx.time++;
                return;
            }
            throw new IllegalStateException("ECL instruction budget exhausted in sub " + ctx.sub);
        }

        int jump(Ins i) {
            ctx.time = i.i(0);
            ctx.pc += i.i(1);
            return 1;
        }

        int call(Script script, Ins i) {
            ctx.pc += i.size;
            if (!noStack) stack[depth] = ctx.copy();
            callSub(script, i.i(0));
            if (!noStack && depth < 7) depth++;
            ctx.ints[0] = i.i(1);
            ctx.floats[0] = i.f(2);
            return 1;
        }

        void interpolate(int duration) {
            interpStart = new float[] {pos[0], pos[1]};
            moveStart = moveTimer = Math.max(1, duration);
            mode = 2;
        }

        Laser laser(World w, int slot) {
            int index = lasers[Th06Vm.clamp(slot, 0, 31)];
            return index >= 0 && index < w.lasers.size() ? w.lasers.get(index) : null;
        }

        int exec(Script script, World w, Ins i) {
            int op = i.op;
            switch (op) {
                case 0:
                    break;
                case 1:
                    return 2;
                case 2:
                    return jump(i);
                case 3:
                    {
                        int v = integer(i.i(2), w) - 1;
                        set(i.i(2), v, v);
                        if (v > 0) return jump(i);
                        break;
                    }
                case 4:
                    {
                        int v = integer(i.i(1), w);
                        set(i.i(0), v, v);
                        break;
                    }
                case 5:
                    {
                        float v = number(i.f(1), w);
                        set(i.i(0), (int) v, v);
                        break;
                    }
                case 6:
                case 7:
                    {
                        int v =
                                w.rng.u32Range(Math.max(0, integer(i.i(1), w)))
                                        + (op == 7 ? integer(i.i(2), w) : 0);
                        set(i.i(0), v, v);
                        break;
                    }
                case 8:
                case 9:
                    {
                        float v =
                                w.rng.range(number(i.f(1), w)) + (op == 9 ? number(i.f(2), w) : 0);
                        set(i.i(0), (int) v, v);
                        break;
                    }
                case 10:
                case 11:
                case 12:
                    {
                        float v = pos[op - 10];
                        set(i.i(0), (int) v, v);
                        break;
                    }
                case 13:
                case 14:
                case 15:
                case 16:
                case 17:
                    {
                        int a = integer(i.i(1), w), b = integer(i.i(2), w);
                        int v =
                                switch (op) {
                                    case 13 -> a + b;
                                    case 14 -> a - b;
                                    case 15 -> a * b;
                                    case 16 -> b == 0 ? 0 : a / b;
                                    default -> b == 0 ? 0 : a % b;
                                };
                        set(i.i(0), v, v);
                        break;
                    }
                case 18:
                case 19:
                    {
                        int v = integer(i.i(0), w) + (op == 18 ? 1 : -1);
                        set(i.i(0), v, v);
                        break;
                    }
                case 20:
                case 21:
                case 22:
                case 23:
                case 24:
                    {
                        float a = number(i.f(1), w), b = number(i.f(2), w);
                        float v =
                                switch (op) {
                                    case 20 -> a + b;
                                    case 21 -> a - b;
                                    case 22 -> a * b;
                                    case 23 -> b == 0 ? 0 : a / b;
                                    default -> b == 0 ? 0 : a % b;
                                };
                        set(i.i(0), (int) v, v);
                        break;
                    }
                case 25:
                    {
                        float v =
                                atan(
                                        number(i.f(4), w) - number(i.f(2), w),
                                        number(i.f(3), w) - number(i.f(1), w));
                        set(i.i(0), (int) v, v);
                        break;
                    }
                case 26:
                    {
                        float v = norm(number(i.f(0), w));
                        set(i.i(0), (int) v, v);
                        break;
                    }
                case 27:
                    {
                        int a = integer(i.i(0), w), b = integer(i.i(1), w);
                        ctx.cmp = a == b ? 0 : a < b ? -1 : 1;
                        break;
                    }
                case 28:
                    {
                        float a = number(i.f(0), w), b = number(i.f(1), w);
                        ctx.cmp = a == b ? 0 : a < b ? -1 : 1;
                        break;
                    }
                case 29:
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                    {
                        boolean yes =
                                switch (op) {
                                    case 29 -> ctx.cmp < 0;
                                    case 30 -> ctx.cmp <= 0;
                                    case 31 -> ctx.cmp == 0;
                                    case 32 -> ctx.cmp > 0;
                                    case 33 -> ctx.cmp >= 0;
                                    default -> ctx.cmp != 0;
                                };
                        if (yes) return jump(i);
                        break;
                    }
                case 35:
                    return call(script, i);
                case 36:
                    if (depth > 0) depth--;
                    ctx = stack[depth].copy();
                    return 1;
                case 37:
                case 38:
                case 39:
                case 40:
                case 41:
                case 42:
                    {
                        int a = integer(i.i(3), w), b = i.i(4);
                        boolean yes =
                                switch (op) {
                                    case 37 -> a < b;
                                    case 38 -> a <= b;
                                    case 39 -> a == b;
                                    case 40 -> a > b;
                                    case 41 -> a >= b;
                                    default -> a != b;
                                };
                        if (yes) return call(script, i);
                        break;
                    }
                case 43:
                    for (int k = 0; k < 3; k++) pos[k] = number(i.f(k), w);
                    clamp();
                    break;
                case 44:
                    axis = new float[] {number(i.f(0), w), number(i.f(1), w)};
                    mode = 0;
                    break;
                case 45:
                    angle = number(i.f(0), w);
                    speed = number(i.f(1), w);
                    mode = 1;
                    break;
                case 46:
                    angular = number(i.f(0), w);
                    mode = 1;
                    break;
                case 47:
                    speed = number(i.f(0), w);
                    mode = 1;
                    break;
                case 48:
                    acceleration = number(i.f(0), w);
                    mode = 1;
                    break;
                case 49:
                case 50:
                    {
                        angle = w.rng.range(i.f(1) - i.f(0)) + i.f(0);
                        if (op == 50) {
                            if (pos[0] < lower[0] + 96) {
                                if (angle > PI / 2) angle = PI - angle;
                                else if (angle < -PI / 2) angle = -PI - angle;
                            }
                            if (pos[0] > upper[0] - 96) {
                                if (angle < PI / 2 && angle >= 0) angle = PI - angle;
                                else if (angle > -PI / 2 && angle <= 0) angle = -PI - angle;
                            }
                            if (pos[1] < lower[1] + 48 && angle < 0) angle = -angle;
                            if (pos[1] > upper[1] - 48 && angle > 0) angle = -angle;
                        }
                        break;
                    }
                case 51:
                    angle = aim(w) + i.f(0);
                    speed = number(i.f(1), w);
                    mode = 1;
                    break;
                case 52:
                case 53:
                case 54:
                case 55:
                    {
                        int duration = i.i(0);
                        float a = number(i.f(1), w), s = i.f(2);
                        interp =
                                new float[] {
                                    cos(a) * s * duration / 2f, sin(a) * s * duration / 2f
                                };
                        interpolate(duration);
                        ease = op - 51;
                        break;
                    }
                case 56:
                case 57:
                case 58:
                case 59:
                case 60:
                    interp = new float[] {number(i.f(1), w) - pos[0], number(i.f(2), w) - pos[1]};
                    interpolate(i.i(0));
                    ease = op - 56;
                    axis = new float[2];
                    break;
                case 61:
                case 62:
                case 63:
                case 64:
                    {
                        int duration = i.i(0);
                        interp =
                                new float[] {
                                    cos(angle) * speed * duration / 2f,
                                    sin(angle) * speed * duration / 2f
                                };
                        interpolate(duration);
                        ease = op - 60;
                        break;
                    }
                case 65:
                    lower = new float[] {i.f(0), i.f(1)};
                    upper = new float[] {i.f(2), i.f(3)};
                    clampPos = true;
                    break;
                case 66:
                    clampPos = false;
                    break;
                case 67:
                case 68:
                case 69:
                case 70:
                case 71:
                case 72:
                case 73:
                case 74:
                case 75:
                    {
                        props.sprite = i.s(0);
                        props.color = integer(i.s(2), w);
                        props.mode = op - 67;
                        props.count1 =
                                (short)
                                        Math.max(
                                                1,
                                                integer(i.i(1), w)
                                                        + rank(rank1Low, rank1High, w.rank));
                        props.count2 =
                                (short)
                                        Math.max(
                                                1,
                                                integer(i.i(2), w)
                                                        + rank(rank2Low, rank2High, w.rank));
                        float s = number(i.f(3), w);
                        if (s != 0) s = Math.max(.3f, s + rank(rankLow, rankHigh, w.rank));
                        props.s1 = s;
                        props.s2 =
                                Math.max(
                                        .3f,
                                        number(i.f(4), w) + rank(rankLow, rankHigh, w.rank) / 2);
                        props.a1 = norm(number(i.f(5), w));
                        props.a2 = number(i.f(6), w);
                        props.flags = i.i(7);
                        props.pos = new float[] {pos[0] + offset[0], pos[1] + offset[1]};
                        if (!shootDisabled) fire(w, props);
                        break;
                    }
                case 76:
                case 77:
                    interval = i.i(0);
                    interval += rank(interval / 5, -interval / 5, w.rank);
                    if (op == 76) shootTimer = 0;
                    else if (interval != 0) shootTimer = w.rng.u32Range(interval);
                    break;
                case 78:
                    shootDisabled = true;
                    break;
                case 79:
                    shootDisabled = false;
                    break;
                case 80:
                    props.pos = new float[] {pos[0] + offset[0], pos[1] + offset[1]};
                    fire(w, props);
                    break;
                case 81:
                    offset = new float[] {number(i.f(0), w), number(i.f(1), w)};
                    break;
                case 82:
                    for (int k = 0; k < 4; k++) {
                        props.exI[k] = integer(i.i(k), w);
                        props.exF[k] = number(i.f(k + 4), w);
                    }
                    break;
                case 83:
                    w.cancel = true;
                    break;
                case 84:
                    if (i.i(0) >= 0) {
                        props.sfx = i.i(0);
                        props.flags |= 0x200;
                    } else props.flags &= ~0x200;
                    break;
                case 85:
                case 86:
                    {
                        Laser l = new Laser();
                        l.pos = new float[] {pos[0] + offset[0], pos[1] + offset[1]};
                        l.angle = number(i.f(1), w) + (op == 86 ? aim(w) : 0);
                        l.speed = number(i.f(2), w);
                        l.start = number(i.f(3), w);
                        l.end = number(i.f(4), w);
                        l.length = number(i.f(5), w);
                        l.width = i.f(6);
                        l.lead = i.i(7);
                        l.duration = i.i(8);
                        l.fade = i.i(9);
                        l.hitStart = i.i(10);
                        l.state = l.lead == 0 ? 1 : 0;
                        l.color = i.s(2);
                        lasers[laserStore & 31] = w.laser(l);
                        break;
                    }
                case 87:
                    laserStore = Math.max(0, integer(i.i(0), w));
                    break;
                case 88:
                case 89:
                case 90:
                case 91:
                case 92:
                    {
                        Laser l = laser(w, i.i(0));
                        if (op == 91) ctx.cmp = l != null && l.live ? 0 : 1;
                        else if (l != null) {
                            if (op == 88) l.angle += number(i.f(1), w);
                            else if (op == 89)
                                l.angle =
                                        atan(w.player[1] - l.pos[1], w.player[0] - l.pos[0])
                                                + number(i.f(1), w);
                            else if (op == 90)
                                l.pos = new float[] {pos[0] + i.f(1), pos[1] + i.f(2)};
                            else if (l.live && l.state < 2) {
                                l.state = 2;
                                l.timer = 0;
                            }
                        }
                        break;
                    }
                case 93:
                    resetEffects(w);
                    w.spells.add(i.s(2));
                    w.cancel = true;
                    resetRank();
                    break;
                case 94:
                    resetEffects(w);
                    w.endedCards++;
                    break;
                case 95:
                    w.spawns.add(
                            new Spawn(
                                    i.i(0),
                                    new float[] {
                                        number(i.f(1), w), number(i.f(2), w), number(i.f(3), w)
                                    },
                                    i.s(16),
                                    i.s(18),
                                    i.i(5),
                                    false));
                    break;
                case 96:
                    w.killTrash = true;
                    break;
                case 97:
                    anmScript = i.i(0);
                    anmDirty = true;
                    break;
                case 98:
                    for (int k = 0; k < 5; k++) poses[k] = i.s(k * 2);
                    poseState = 255;
                    break;
                case 99:
                    {
                        int slot = i.i(0);
                        if (slot >= 0 && slot < 8) {
                            subScripts[slot] = i.i(1);
                            subDirty[slot] = true;
                        }
                        break;
                    }
                case 100:
                    deathAnm1 = i.size > 12 ? i.b(0) : 0;
                    deathAnm2 = i.size > 13 ? i.b(1) : 0;
                    break;
                case 101:
                    boss = i.i(0) >= 0;
                    w.bossPresent = boss;
                    break;
                case 102:
                    {
                        int slot = w.effect(13, true);
                        if (slot >= 0) effects.add(slot);
                        break;
                    }
                case 103:
                    hitbox = new float[] {i.f(0), i.f(1)};
                    break;
                case 104:
                    collidable = i.i(0) != 0;
                    break;
                case 105:
                    damageable = i.i(0) != 0;
                    break;
                case 106:
                    break; // Sound-only event.
                case 107:
                    deathMode = i.i(0) & 255;
                    break;
                case 108:
                    deathCallback = i.i(0);
                    break;
                case 109:
                    interrupts[i.i(1) & 7] = i.i(0);
                    break;
                case 110:
                    runInterrupt = i.i(0);
                    return 1;
                case 111:
                    phase++;
                    life = i.i(0);
                    healthStart = life;
                    break;
                case 112:
                    bossTimer = i.i(0);
                    break;
                case 113:
                    lifeThreshold = i.i(0);
                    break;
                case 114:
                    lifeSub = i.i(0);
                    break;
                case 115:
                    timerThreshold = i.i(0);
                    bossTimer = 0;
                    break;
                case 116:
                    timerSub = i.i(0);
                    break;
                case 117:
                    interactable = i.i(0) != 0;
                    break;
                case 118:
                    w.particles(i.i(0), i.i(1));
                    break;
                case 119:
                    for (int k = 0; k < i.i(0); k++) {
                        w.rng.range(144);
                        w.rng.range(144);
                    }
                    break;
                case 120:
                    break; // Rotation is presentation only.
                case 121:
                    extra(i.i(0), i.i(1), w);
                    break;
                case 122:
                    ctx.repeat = Math.max(-1, i.i(0));
                    break;
                case 123:
                    ctx.time += integer(i.i(0), w);
                    break;
                case 124:
                case 125:
                case 127:
                case 129:
                    break; // Items, background and debug have no consumers here.
                case 126:
                    spellCount = i.i(0);
                    break;
                case 128:
                    pendingInterrupt = i.i(0);
                    break;
                case 130:
                    noStack = i.i(0) != 0;
                    break;
                case 131:
                    rankLow = i.f(0);
                    rankHigh = i.f(1);
                    rank1Low = i.i(2);
                    rank1High = i.i(3);
                    rank2Low = i.i(4);
                    rank2High = i.i(5);
                    break;
                case 132:
                    invisible = i.i(0) != 0;
                    break;
                case 133:
                    timerSub = deathCallback;
                    bossTimer = 0;
                    break;
                case 134:
                    Arrays.fill(lasers, -1);
                    break;
                case 135:
                    survival = i.i(0) != 0;
                    break;
                default:
                    throw new IllegalStateException("Unsupported ECL opcode " + op);
            }
            return 0;
        }

        void move() {
            pos[0] += mirror ? -axis[0] : axis[0];
            pos[1] += axis[1];
            clamp();
            if (poses[3] >= 0) {
                int state = axis[0] < 0 ? 1 : axis[0] > 0 ? 2 : 0;
                if (state != poseState) {
                    int script =
                            state == 1
                                    ? poses[3]
                                    : state == 2
                                            ? poses[4]
                                            : poseState == 255
                                                    ? poses[0]
                                                    : poseState == 1 ? poses[1] : poses[2];
                    if (script >= 0) {
                        anmScript = script;
                        anmDirty = true;
                    }
                    poseState = state;
                }
            }
        }

        void clamp() {
            if (clampPos) {
                pos[0] = Th06Vm.clamp(pos[0], lower[0], upper[0]);
                pos[1] = Th06Vm.clamp(pos[1], lower[1], upper[1]);
            }
        }

        boolean bounds(float width, float height) {
            boolean inside =
                    pos[0] + width / 2 >= 0
                            && pos[0] - width / 2 <= W
                            && pos[1] + height / 2 >= 0
                            && pos[1] - height / 2 <= H;
            if (!beenInside && inside) beenInside = true;
            return !(beenInside && !inside);
        }

        void callbacks(Script script, World w) {
            if (lifeThreshold >= 0 && life < lifeThreshold) {
                life = lifeThreshold;
                healthStart = life;
                callSub(script, lifeSub);
                lifeThreshold = -1;
                timerSub = deathCallback;
                resetRank();
                depth = 0;
                w.killTrash = true;
            }
            if (survival && timerThreshold >= 0 && bossTimer >= timerThreshold) {
                if (lifeThreshold > 0) {
                    life = lifeThreshold;
                    healthStart = life;
                    lifeThreshold = -1;
                }
                callSub(script, timerSub);
                timerThreshold = -1;
                timerSub = deathCallback;
                bossTimer = 0;
                resetRank();
                depth = 0;
                w.killTrash = true;
            }
        }

        void resetRank() {
            rankLow = -.5f;
            rankHigh = .5f;
            rank1Low = rank1High = rank2Low = rank2High = 0;
        }

        void resetEffects(World w) {
            for (int slot : effects)
                if (slot < w.effects.size()) {
                    Effect e = w.effects.get(slot);
                    if (e != null && e.bubble && e.pop < 0) e.pop = 0;
                }
            effects.clear();
        }

        void deathEffects(World w) {
            if (deathFx) return;
            deathFx = true;
            int a1 = deathAnm1, a2 = deathAnm2 + 4;
            if (deathMode == 3) {
                w.particles(a1, 1);
                w.particles(a1, 1);
                w.particles(a1, 1);
            } else if (item >= 0) w.particles(a2, 3);
            else if (item == -1) {
                if (w.randomItem % 3 == 0) {
                    w.particles(a2, 6);
                    w.randomTable = (w.randomTable + 1) % 16;
                }
                w.randomItem++;
            }
            w.particles(a1, 1);
            w.particles(a2, 4);
        }

        void despawn(World w) {
            occupied = false;
            interactable = false;
            if (boss) {
                w.bossPresent = false;
                w.timeStopped = false;
            }
        }

        void onDeath(Script script, World w) {
            resetEffects(w);
            lifeThreshold = timerThreshold = -1;
            switch (deathMode) {
                case 3:
                    life = 1;
                    damageable = false;
                    deathMode = 0;
                    w.bossPresent = false;
                    break;
                case 1:
                    interactable = false;
                    break;
                case 0:
                    occupied = false;
                    break;
                default:
                    break;
            }
            if (deathMode != 3) life = 0;
            if (boss && (deathMode == 0 || deathMode == 1)) {
                w.bossPresent = false;
                w.timeStopped = false;
            }
            if (deathCallback >= 0) {
                resetRank();
                depth = 0;
                callSub(script, deathCallback);
                deathCallback = -1;
            }
        }

        void extra(int index, int param, World w) {
            switch (index) {
                case 0:
                    for (Shot b : w.bullets) {
                        if (param == 0) b.speed = 0;
                        else if (param == 1) {
                            b.flags |= 0x10;
                            b.interval = 220;
                            b.timer = 0;
                            float a = w.rng.unit() * TAU - PI;
                            b.accel = new float[] {cos(a) * .01f, sin(a) * .01f};
                        }
                    }
                    break;
                case 1:
                    {
                        float s = param;
                        props.pos =
                                new float[] {
                                    w.rng.range(s) + pos[0] - s / 2,
                                    w.rng.range(s * .75f) + pos[1] - s * .75f / 2
                                };
                        fire(w, props);
                        break;
                    }
                case 2:
                    {
                        int t = ctx.ints[2], total = ctx.ints[3];
                        if (t >= total) {
                            ctx.repeat = -1;
                            return;
                        }
                        float step = 4 * PI / 5;
                        if (t == 0) {
                            starEnemy = new float[] {pos[0], pos[1]};
                            starPlayer = w.player.clone();
                            starAngle[0] = w.rng.unit() * TAU - PI;
                            starAngle[1] = norm(starAngle[0] + step);
                        }
                        if (t % 30 == 0) {
                            starAngle[0] = starAngle[1];
                            for (int n = 1; n < 6; n++)
                                starAngle[n] = norm(starAngle[n - 1] + step);
                        }
                        if (t % 6 == 0) {
                            float progress = (float) t / total,
                                    td0 = progress * .1f,
                                    pp = progress + .5f;
                            float bx = (starPlayer[0] - starEnemy[0]) * td0 + starEnemy[0],
                                    by = (starPlayer[1] - starEnemy[1]) * td0 + starEnemy[1];
                            props.a1 = PI / 3 * pp;
                            float r = ctx.floats[3];
                            for (int n = 0; n < 5; n++) {
                                float td = (t % 30) / 30f, a0 = starAngle[n], a1 = starAngle[n + 1];
                                float x0 = cos(a0) * r,
                                        y0 = sin(a0) * r,
                                        x1 = cos(a1) * r,
                                        y1 = sin(a1) * r;
                                props.pos =
                                        new float[] {
                                            bx + (x1 - x0) * td + x0, by + (y1 - y0) * td + y0
                                        };
                                float backup = props.s1;
                                props.s1 = w.rng.range(props.s2) + props.s1;
                                fire(w, props);
                                props.s1 = backup;
                                props.a1 -= PI / 6 * pp;
                            }
                        }
                        ctx.ints[2]++;
                        break;
                    }
                case 3:
                    {
                        int[][][] table = {{{0, 3, 1}, {2, 3, 4}}, {{1, 4, 0}, {4, 2, 3}}};
                        int[] v = table[Math.min(w.character, 1)][Math.min(w.shot, 1)];
                        ctx.ints[1] = v[0];
                        ctx.ints[2] = v[1];
                        ctx.ints[3] = v[2];
                        break;
                    }
                case 4:
                    {
                        if (param < 2) w.timeStopped = (param & 255) != 0;
                        else {
                            boolean normal = w.difficulty <= 1;
                            int left = normal ? 14 : 52;
                            for (Shot b : w.bullets)
                                if (b.height >= 30 && b.color != 5 && w.rng.u16() % 4 == 0) {
                                    int base = Math.max(0, b.sprite - b.color);
                                    b.color = 5;
                                    b.sprite = base + 5;
                                    float dx = b.pos[0] - w.player[0], dy = b.pos[1] - w.player[1];
                                    b.angle =
                                            sqrt(dx * dx + dy * dy) > 128
                                                    ? (normal
                                                            ? w.rng.unit() * (3 * PI / 4) + PI / 4
                                                            : w.rng.unit() * TAU)
                                                    : atan(dy, dx) + PI / 2 + w.rng.range(TAU);
                                    if (--left == 0) break;
                                }
                        }
                        ctx.ints[2] = 0;
                        break;
                    }
                case 5:
                    {
                        int t = ctx.ints[2];
                        if (t % 9 == 0) {
                            int pp = t / 9;
                            boolean normal = w.difficulty <= 1;
                            Props p = new Props();
                            p.sprite = 8;
                            p.mode = 0;
                            p.count1 = normal ? 1 : 3;
                            p.a2 = PI / 6;
                            p.s1 = 2;
                            p.color = 3;
                            float mx = w.player[0] - pos[0],
                                    my = w.player[1] - pos[1],
                                    len = sqrt(mx * mx + my * my),
                                    ix = len > 0 ? mx / len : 0,
                                    iy = len > 0 ? my / len : 0;
                            float in = (pp & 1) != 0 ? -256 : 256;
                            ix *= in;
                            iy *= in;
                            float out = .5f - pp * .5f / 9;
                            mx *= out;
                            my *= out;
                            float ox = mx + ix, oy = my + iy;
                            ix = -ix;
                            iy = -iy;
                            float c = cos(PI / 4), s = sin(PI / 4), old = ix;
                            ix = ix * c + iy * s;
                            iy = -old * s + iy * c;
                            float co = cos(-PI / 18), so = sin(-PI / 18), a = -PI / 4;
                            for (int n = 0; n < 9; n++) {
                                old = ix;
                                ix = ix * co + iy * so;
                                iy = -old * so + iy * co;
                                p.pos = new float[] {ix + pos[0] + ox, iy + pos[1] + oy};
                                p.s1 = 2;
                                if ((pp & 1) != 0 && normal) p.a1 = a;
                                p.color = 3;
                                fire(w, p);
                                a += PI / 18;
                            }
                        }
                        ctx.ints[2]++;
                        break;
                    }
                case 6:
                    batWing(w);
                    break;
                case 7:
                    {
                        float random = w.rng.range(TAU);
                        boolean normal = w.difficulty <= 1;
                        for (int outer = 0; outer < 2; outer++) {
                            float a = outer == 0 ? -PI + random : 7 * -PI / 8 + random,
                                    diff = outer == 0 ? PI / 4 : -PI / 4;
                            float[][] pv = new float[8][2];
                            for (int n = 0; n < 8; n++) {
                                pv[n] = new float[] {pos[0] + cos(a) * 32, pos[1] + sin(a) * 32};
                                a += PI / 4;
                            }
                            a = outer == 0 ? -PI + random : 7 * -PI / 8 + random;
                            for (int inner = 0; inner < 3; inner++) {
                                float length = inner < 2 ? 112 : 480;
                                for (float[] point : pv) {
                                    if (param == 0) {
                                        Laser l = new Laser();
                                        l.pos = point.clone();
                                        l.angle = a;
                                        l.end = l.length = normal ? length : 440;
                                        l.width = normal ? 28 : 20;
                                        l.lead = inner * 16 + 60;
                                        l.duration = 90 - inner * 16;
                                        l.fade = 16;
                                        l.hitStart = 50;
                                        l.color = normal ? 2 : 8;
                                        w.laser(l);
                                    } else {
                                        props.pos = point.clone();
                                        fire(w, props);
                                    }
                                    point[0] += cos(a) * length;
                                    point[1] += sin(a) * length;
                                    a += PI / 4;
                                }
                                a += diff - TAU;
                            }
                        }
                        break;
                    }
                case 8:
                    {
                        List<float[]> positions = new ArrayList<>();
                        for (Shot b : w.bullets)
                            if (b.fade == 0 && b.height >= 30) positions.add(b.pos.clone());
                        for (float[] point : positions) {
                            Props p = new Props();
                            p.pos = point;
                            p.sprite = 3;
                            p.color = 1;
                            p.a1 = w.rng.range(TAU) - PI;
                            p.flags = 8;
                            p.mode = 1;
                            fire(w, p);
                        }
                        ctx.ints[3] = positions.size();
                        break;
                    }
                case 9:
                case 11:
                    {
                        float random = w.rng.range(TAU) - PI;
                        w.particles(12, 1);
                        for (Shot b : w.bullets) {
                            if (b.fade != 0 || b.height >= 30 || b.speed != 0) continue;
                            int base = Math.max(0, b.sprite - b.color);
                            b.flags |= 0x10;
                            b.color = 2;
                            b.sprite = base + 2;
                            b.speed = .01f;
                            b.timer = 0;
                            b.interval = 120;
                            float dx = pos[0] - b.pos[0],
                                    dy = pos[1] - b.pos[1],
                                    d2 = dx * dx + dy * dy;
                            float a =
                                    index == 9
                                            ? (d2 > .1f ? sqrt(d2) : 0) * PI / 256 + random
                                            : w.rng.range(TAU) - PI;
                            b.accel = new float[] {cos(a) * .01f, sin(a) * .01f};
                        }
                        break;
                    }
                case 15:
                    {
                        List<float[]> big = new ArrayList<>();
                        for (Shot b : w.bullets)
                            if (b.fade == 0 && b.height >= 30) big.add(b.pos.clone());
                        for (float[] point : big) {
                            float enemyAngle = atan(point[1] - pos[1], point[0] - pos[0]);
                            for (Shot b : w.bullets) {
                                if (b.fade != 0 || b.height >= 30 || b.speed != 0) continue;
                                float dx = b.pos[0] - point[0], dy = b.pos[1] - point[1];
                                if (sqrt(dx * dx + dy * dy) >= 64) continue;
                                b.flags |= 0x10;
                                b.speed = .01f;
                                b.timer = 0;
                                b.interval = 120;
                                b.angle =
                                        (atan(b.pos[1] - pos[1], b.pos[0] - pos[0]) - enemyAngle)
                                                        * 2.2f
                                                + enemyAngle;
                                b.accel = new float[] {cos(b.angle) * .01f, sin(b.angle) * .01f};
                                int base = Math.max(0, b.sprite - b.color);
                                b.color++;
                                b.sprite = base + b.color;
                            }
                        }
                        batTransform(w);
                        ctx.ints[3] = big.size();
                        break;
                    }
                case 12:
                    for (int slot = 0; slot < 8; slot++) {
                        Laser l = laser(w, slot);
                        if (l != null && l.live) {
                            props.pos =
                                    new float[] {
                                        pos[0] + cos(l.angle) * 64, pos[1] + sin(l.angle) * 64
                                    };
                            fire(w, props);
                        }
                    }
                    break;
                case 13:
                    {
                        int n = param;
                        if (n > 0 && ctx.ints[3] % 6 == 0) {
                            float a = ctx.floats[2], r = ctx.floats[3], off = ctx.floats[1];
                            for (int k = 0; k < n; k++) {
                                props.pos = new float[] {cos(a) * r + 192, sin(a) * r + 224};
                                props.a1 = a + off;
                                fire(w, props);
                                a += TAU / n;
                            }
                        }
                        ctx.ints[3]++;
                        break;
                    }
                case 14:
                    ctx.ints[3] = 0;
                    for (int slot = 0; slot < 8; slot++) {
                        Laser l = laser(w, slot);
                        if (l == null || !l.live) continue;
                        for (float pm = l.start; l.end > pm; pm += 48) {
                            props.pos =
                                    new float[] {
                                        cos(l.angle) * pm + l.pos[0], sin(l.angle) * pm + l.pos[1]
                                    };
                            fire(w, props);
                        }
                        ctx.ints[3]++;
                    }
                    break;
                case 16:
                    {
                        int remaining = bossTimer >= 7200 ? 0 : life;
                        if (param == 0) {
                            ctx.floats[3] = 2 - remaining / 6000f;
                            ctx.ints[5] = remaining * 240 / 6000 + 40;
                        } else {
                            float m = 320 - remaining * 160f / 6000;
                            ctx.floats[2] = w.rng.range(m) + (192 - m / 2);
                            m = 128 - remaining * 64f / 6000;
                            ctx.floats[3] = w.rng.range(m) + (96 - m / 2);
                        }
                        break;
                    }
                case 10:
                    batTransform(w);
                    break;
                default:
                    throw new IllegalStateException("Unsupported boss ex-instruction " + index);
            }
        }

        void batWing(World w) {
            if (invisible) return;
            float deg = PI / 180;
            wingAngle += deg;
            if (wingAngle >= 45 * deg) wingAngle -= 90 * deg;
            int t = wingTimer;
            if (t > 120 || (t > 60 && t % 2 == 0) || (t > 30 && t % 4 == 0) || t % 8 == 0) {
                int bam = t % 16;
                bam = w.rng.u16Range(bam / 2) + bam / 2;
                // Two moving glow effects; motion is presentation-only, RNG and lifetime are
                // preserved.
                for (int side = 0; side < 2; side++) {
                    w.rng.unit();
                    w.particles(19, 1);
                }
            }
            wingTimer++;
        }

        void batTransform(World w) {
            if (life <= 0) return;
            batWing(w);
            if (w.bomb) {
                if (poses[3] >= 0) {
                    anmScript = 165;
                    anmDirty = true;
                    poses[3] = -1;
                }
                interactable = false;
                batTimer = 60;
            } else if (batTimer > 0 && --batTimer == 0) {
                if (poses[3] < 0) {
                    anmScript = 160;
                    anmDirty = true;
                    poses[3] = 161;
                }
                interactable = true;
            }
        }
    }

    static void fire(World w, Props p) {
        float aimed = atan(w.player[1] - p.pos[1], w.player[0] - p.pos[0]);
        for (int row = 0; row < p.count2; row++)
            for (int col = 0; col < p.count1; col++) {
                if (w.bullets.size() >= 640) return;
                float a = 0, s = p.s1 - (p.s1 - p.s2) * row / p.count2;
                switch (p.mode) {
                    case 0:
                    case 1:
                        a +=
                                (p.count1 & 1) != 0
                                        ? ((col + 1) / 2) * p.a2
                                        : (col / 2) * p.a2 + p.a2 * .5f;
                        if ((col & 1) != 0) a = -a;
                        if (p.mode == 0) a += aimed;
                        a += p.a1;
                        break;
                    case 2:
                    case 3:
                        if (p.mode == 2) a += aimed;
                        a += col * TAU / p.count1;
                        a += row * p.a2 + p.a1;
                        break;
                    case 4:
                    case 5:
                        if (p.mode == 4) a += aimed;
                        a += PI / p.count1;
                        a += col * TAU / p.count1;
                        a += p.a1;
                        break;
                    case 6:
                        a = w.rng.range(p.a1 - p.a2) + p.a2;
                        break;
                    case 7:
                        s = w.rng.range(p.s1 - p.s2) + p.s2;
                        a += col * TAU / p.count1;
                        a += row * p.a2 + p.a1;
                        break;
                    default:
                        a = w.rng.range(p.a1 - p.a2) + p.a2;
                        s = w.rng.range(p.s1 - p.s2) + p.s2;
                        break;
                }
                a = norm(a);
                Shot b = new Shot();
                b.pos = p.pos.clone();
                b.angle = a;
                b.speed = s;
                b.id = ++w.nextBullet;
                int type = clamp(p.sprite, 0, 9),
                        base = p.sprite >= 0 && p.sprite < 10 ? BASES[p.sprite] : 30;
                b.color = Math.max(0, p.color);
                b.sprite = base + b.color;
                b.width = w.widths[type];
                b.height = w.heights[type];
                b.flags = p.flags;
                b.factor = 1;
                boolean big = p.sprite >= 6;
                if ((p.flags & 2) != 0) {
                    b.delay = big ? 32 : 10;
                    b.factor = .5f;
                } else if ((p.flags & 4) != 0) {
                    b.delay = big ? 32 : 16;
                    b.factor = 1 / 2.5f;
                } else if ((p.flags & 8) != 0) {
                    b.delay = 32;
                    b.factor = 1 / 3f;
                }
                if ((p.flags & 0x10) != 0) {
                    float dir = p.exF[1] <= -999 ? a : p.exF[1];
                    b.accel = new float[] {cos(dir) * p.exF[0], sin(dir) * p.exF[0]};
                    b.interval = p.exI[0] > 0 ? p.exI[0] : 99999;
                } else if ((p.flags & 0x20) != 0) {
                    b.exF = new float[] {p.exF[0], p.exF[1]};
                    b.interval = p.exI[0];
                }
                if ((p.flags & 0x1c0) != 0) {
                    b.exF = new float[] {p.exF[0], p.exF[1] >= 0 ? p.exF[1] : s};
                    b.interval = p.exI[0];
                    b.repeats = p.exI[1];
                }
                if ((p.flags & 0xc00) != 0) {
                    b.exF[1] = p.exF[0] >= 0 ? p.exF[0] : s;
                    b.repeats = p.exI[0];
                }
                w.bullets.add(b);
            }
    }

    static void physics(World w) {
        if (w.timeStopped) return;
        for (Shot b : w.bullets) {
            if (b.fade > 0) {
                b.pos[0] += cos(b.angle) * b.speed * .5f;
                b.pos[1] += sin(b.angle) * b.speed * .5f;
                if (--b.fade == 0) b.oob = Integer.MAX_VALUE;
                continue;
            }
            b.timer++;
            if ((b.flags & 1) != 0) {
                if (b.timer <= 16) {
                    float boost = 5 - b.timer * 5f / 16;
                    b.pos[0] += cos(b.angle) * boost;
                    b.pos[1] += sin(b.angle) * boost;
                } else b.flags &= ~1;
            } else if ((b.flags & 0x10) != 0) {
                if (b.timer >= b.interval) b.flags &= ~0x10;
                else {
                    float vx = cos(b.angle) * b.speed + b.accel[0],
                            vy = sin(b.angle) * b.speed + b.accel[1];
                    b.speed = sqrt(vx * vx + vy * vy);
                    b.angle = atan(vy, vx);
                }
            } else if ((b.flags & 0x20) != 0) {
                if (b.timer >= b.interval) b.flags &= ~0x20;
                else {
                    b.angle += b.exF[1];
                    b.speed += b.exF[0];
                }
            }
            float move = b.speed;
            int mode = b.flags & 0x1c0;
            if (mode != 0) {
                if (b.interval <= 0 || b.timer >= b.interval * (b.count + 1)) {
                    if (++b.count >= b.repeats) b.flags &= ~mode;
                    b.angle =
                            (mode & 0x40) != 0
                                    ? b.angle + b.exF[0]
                                    : (mode & 0x100) != 0
                                            ? b.exF[0]
                                            : atan(w.player[1] - b.pos[1], w.player[0] - b.pos[0])
                                                    + b.exF[0];
                    b.speed = b.exF[1];
                    move = b.speed;
                } else {
                    float t = b.timer - (float) (b.interval * b.count);
                    move = b.speed - t * b.speed / b.interval;
                }
            } else if ((b.flags & 0xc00) != 0 && !inside(b)) {
                boolean bottom = (b.flags & 0x400) != 0;
                if (b.pos[0] < 0 || b.pos[0] >= W) b.angle = norm(-b.angle - PI);
                if (b.pos[1] < 0 || (bottom && b.pos[1] >= H)) b.angle = -b.angle;
                b.speed = b.exF[1];
                move = b.speed;
                if (++b.count >= b.repeats) b.flags &= ~0xc00;
            }
            float factor = b.delay > 0 ? b.factor : 1;
            if (b.delay > 0) b.delay--;
            b.pos[0] += cos(b.angle) * move * factor;
            b.pos[1] += sin(b.angle) * move * factor;
            if (factor != 1 && b.delay == 0) {
                b.pos[0] += cos(b.angle) * move;
                b.pos[1] += sin(b.angle) * move;
            }
            if (b.delay == 0) {
                if (inside(b)) b.oob = 0;
                else if ((b.flags & 0xdc0) == 0 && b.oob == 0) b.oob = Integer.MAX_VALUE;
                else b.oob++;
            }
        }
        w.bullets.removeIf(b -> b.oob >= 256);
        for (Laser l : w.lasers) {
            if (!l.live) continue;
            l.end += l.speed;
            if (l.length < l.end - l.start) l.start = l.end - l.length;
            if (l.start < 0) l.start = 0;
            l.timer++;
            if (l.state == 0) {
                if (l.timer >= l.lead) {
                    l.state = 1;
                    l.timer = 0;
                }
            } else if (l.state == 1) {
                if (l.timer >= l.duration) {
                    l.state = 2;
                    l.timer = 0;
                }
            } else if (l.timer >= l.fade) l.live = false;
        }
    }

    static boolean inside(Shot b) {
        return b.pos[0] + b.width / 2 >= 0
                && b.pos[0] - b.width / 2 <= W
                && b.pos[1] + b.height / 2 >= 0
                && b.pos[1] - b.height / 2 <= H;
    }
}
