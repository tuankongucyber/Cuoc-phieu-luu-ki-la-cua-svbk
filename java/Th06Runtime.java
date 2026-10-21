import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/** In-process, frame-stepped Java ECL simulation. No wall-clock gameplay advances. */
public final class Th06Runtime implements AutoCloseable {
    public static final double SCALE = 1.34, LEFT = 242.72, TOP = 112;
    public static final Rectangle ARENA = new Rectangle((int) LEFT, (int) TOP, (int) (384 * SCALE), (int) (448 * SCALE));
    public final Th06BossId identity;
    public final int route;
    private final Th06Vm simulation;
    private final Map<Integer, Double> damage = new LinkedHashMap<>();
    private final Map<Integer, Actor> actors = new LinkedHashMap<>();
    private final Map<Integer, Bullet> bullets = new HashMap<>();
    public final List<Beam> beams = new ArrayList<>();
    private boolean bomb, closed, activeCard;
    private int phaseSerial, hitsAtStart, bombsAtStart;
    public long frameCount;
    public double lastStepMillis;
    public final Set<Integer> observedSpellIds = new LinkedHashSet<>();
    public String failure = "";
    public Th06Runtime(Th06BossId identity, Difficulty difficulty, long seed, int route) throws IOException {
        if (route < 0 || route > 3) throw new IllegalArgumentException("Unknown TH06 shot route");
        this.identity = identity; this.route = route;
        Path script = Th06Data.prepare(identity.stage);
        simulation = new Th06Vm(script, identity.entry, identity.stage == 7 ? 4 : difficulty.ordinal(), (int) (seed & 65535), route);
    }
    public void damage(int actor, double amount) { if (!closed) damage.merge(actor, amount, Double::sum); }
    boolean isRunning() { return !closed; }
    public void bomb() { bomb = true; beams.clear(); }
    public void tick(GameWorld world) {
        if (closed) return;
        try {
            Set<Integer> consumed = new HashSet<>();
            for (var item : bullets.entrySet()) if (item.getValue().dead) consumed.add(item.getKey());
            Map<Integer, Double> hits = new LinkedHashMap<>(damage); damage.clear();
            boolean usedBomb = bomb; bomb = false;
            float px = (float) ((world.player.getCenterX() - LEFT) / SCALE), py = (float) ((world.player.getCenterY() - TOP) / SCALE);
            long start = System.nanoTime();
            simulation.step(px, py, usedBomb, hits, consumed);
            Snapshot snapshot = simulation.snapshot();
            lastStepMillis = (System.nanoTime() - start) / 1_000_000.0; frameCount++;
            apply(world, snapshot);
        } catch (Exception error) {
            failure = "Bộ chạy TH06 đã dừng: " + error.getClass().getSimpleName();
            close(); world.notice(failure, 600); world.lastDamageSource = failure; world.state = GameWorld.State.DEFEAT; world.input.clear();
        }
    }
    private void apply(GameWorld world, Snapshot snapshot) {
        phaseSerial = snapshot.phase;
        if (snapshot.endedCards > 0) finishCard(world);
        for (int spell : snapshot.spells) {
            finishCard(world); activeCard = true;
            hitsAtStart = world.player.hitsTaken; bombsAtStart = world.player.bombsUsed;
            observedSpellIds.add(spell);
        }
        Set<Integer> live = new HashSet<>();
        for (ActorState state : snapshot.actors) {
            live.add(state.id); Actor actor = actors.computeIfAbsent(state.id, id -> new Actor(this, id));
            actor.apply(state, world.difficulty); actor.phase = Math.max(0, phaseSerial - 1);
            if (!world.enemies.contains(actor)) world.enemies.add(actor);
        }
        world.enemies.removeIf(enemy -> enemy instanceof Actor && !live.contains(((Actor) enemy).id));
        actors.keySet().retainAll(live);
        Actor main = actors.values().stream().filter(actor -> actor.originalBoss).findFirst().orElse(null);
        if (main != null) world.boss = main;
        Set<Integer> liveShots = new HashSet<>();
        for (BulletState state : snapshot.bullets) {
            liveShots.add(state.id); Bullet bullet = bullets.get(state.id);
            if (bullet == null || bullet.dead) {
                bullet = new Bullet(state, world.difficulty, identity.label); bullets.put(state.id, bullet); world.projectiles.add(bullet);
            } else bullet.apply(state);
        }
        world.projectiles.removeIf(p -> p instanceof Bullet && !liveShots.contains(((Bullet) p).id));
        bullets.keySet().retainAll(liveShots); beams.clear(); beams.addAll(snapshot.beams);
        if (snapshot.status == 1) {
            finishCard(world);
            world.score += 1000; world.finishOriginalBoss();
        }
    }
    private void finishCard(GameWorld world) {
        if (activeCard && world.player.hitsTaken == hitsAtStart && world.player.bombsUsed == bombsAtStart) { world.captures++; world.score += 2500; }
        activeCard = false;
    }
    public void collideBeams(GameWorld world) {
        for (Beam beam : beams) if (beam.active && Collision.segmentsDistanceSquared(beam.x1, beam.y1, beam.x2, beam.y2,
            world.player.previousCenterX, world.player.previousCenterY, world.player.getCenterX(), world.player.getCenterY()) <= Math.pow(beam.width / 4 + Player.BULLET_RADIUS, 2))
            world.hurtPlayer(12 * world.difficulty.enemyDamage, identity.label + " • laser");
    }
    public void drawBeams(Graphics2D g) {
        Stroke previous = g.getStroke();
        for (Beam beam : beams) {
            g.setStroke(new BasicStroke((float) (beam.active ? Math.max(2, beam.width) : 1)));
            g.setColor(beam.active ? new Color(255, 115, 180, 210) : new Color(150, 185, 255, 150));
            g.drawLine((int) beam.x1, (int) beam.y1, (int) beam.x2, (int) beam.y2);
        }
        g.setStroke(previous);
    }
    @Override public void close() {
        if (closed) return; closed = true;
        simulation.close(); damage.clear(); actors.clear(); bullets.clear(); beams.clear();
    }

    record Snapshot(int status, int phase, List<ActorState> actors, List<BulletState> bullets, List<Beam> beams, List<Integer> spells, int endedCards) {}
    record ActorState(int id, float x, float y, float hp, float maxHp, float width, float height, int flags) {}
    record BulletState(int id, float x, float y, float angle, int sprite, float width, float height, boolean active) {}
    public record Beam(double x1, double y1, double x2, double y2, double width, boolean active, int color) {}
    public static final class Actor extends Boss {
        // A source Spell Card may contain only a few hundred HP. Give every boss
        // phase a usable Survivors damage budget without changing ECL thresholds.
        static final double MIN_PHASE_HP = 6000;
        final Th06Runtime runtime; final int id;
        boolean damageable, originalBoss, visible = true, collidable, initialized;
        double collisionRadius = 24;
        Actor(Th06Runtime runtime, int id) { super(0, 0, 0); this.runtime = runtime; this.id = id; }
        void apply(ActorState state, Difficulty difficulty) {
            previousCenterX = getCenterX(); previousCenterY = getCenterY();
            x = LEFT + state.x * SCALE - width / 2.0; y = TOP + state.y * SCALE - height / 2.0;
            if (!initialized) { previousCenterX = getCenterX(); previousCenterY = getCenterY(); initialized = true; }
            originalBoss = (state.flags & 16) != 0;
            double scale = difficulty.enemyHealth * (originalBoss ? Math.max(1, MIN_PHASE_HP / state.maxHp) : 1);
            hp = state.hp * scale; maxHp = state.maxHp * scale;
            rawMaxHp = state.maxHp;
            collisionRadius = Math.min(state.width, state.height) * SCALE / 3.0;
            damageable = (state.flags & 3) == 3; collidable = (state.flags & 5) == 5;
            visible = (state.flags & 8) == 0; dead = false;
            contactDamage = collidable ? 18 * difficulty.enemyDamage : 0;
        }
        @Override public void beginPhase(int phase) { /* The ECL script owns every phase. */ }
        @Override public void fight(GameWorld world) { /* One shared interpreter step per world tick. */ }
        @Override public void applyDifficulty(Difficulty difficulty) { /* Source HP is scaled on snapshots. */ }
        @Override public void takeDamage(double amount) {
            if (!damageable || amount <= 0 || hp <= 0) return;
            runtime.damage(id, amount / Math.max(.001, maxHp / Math.max(1, rawMaxHp)));
            hp = Math.max(0, hp - amount); hitFlashTimer = 6;
        }
        double rawMaxHp;
        @Override public String title() { return runtime.identity.label; }
        @Override public String spellName() { return ""; }
        @Override public boolean isWarning() { return false; }
        @Override public double bodyRadius() { return Math.max(2, collisionRadius); }
        @Override public void draw(Graphics2D g) {
            if (!visible) return;
            g.setColor(hitFlashTimer > 0 ? Color.WHITE : originalBoss ? new Color(210, 80, 145) : new Color(150, 120, 210));
            g.fillRoundRect((int) x, (int) y, width, height, 18, 18);
            g.setColor(Color.WHITE); g.drawOval((int) getCenterX() - 10, (int) getCenterY() - 10, 20, 20);
        }
    }
    public static final class Bullet extends Projectile {
        final int id; boolean active;
        double visualRadius; int sprite;
        Bullet(BulletState state, Difficulty difficulty, String boss) {
            super(LEFT + state.x * SCALE, TOP + state.y * SCALE, 0, 0, 12 * difficulty.enemyDamage, collisionRadius(state),
                palette(state.sprite), Integer.MAX_VALUE, true); id = state.id; sourceLabel = boss + " • đạn"; apply(state);
        }
        void apply(BulletState state) {
            previousX = x; previousY = y; x = LEFT + state.x * SCALE; y = TOP + state.y * SCALE;
            vx = x - previousX; vy = y - previousY; storedAngle = state.angle; active = state.active;
            knife = state.sprite >= 122 && state.sprite < 146; age++;
            visual = BulletVisual.fromTh06Sprite(state.sprite);
            visualRadius = Math.max(3, Math.min(34, Math.max(state.width, state.height) * SCALE / 2)); sprite = state.sprite;
        }
        @Override public void update(GameWorld world) { /* Position comes from the authoritative ECL snapshot. */ }
        @Override public void draw(Graphics2D g) {
            visual.draw(g, x, y, storedAngle, visualRadius, palette(sprite));
        }
        private static final Color[] COLORS = {new Color(110,110,110),new Color(230,65,65),new Color(255,110,150),new Color(180,80,230),
            new Color(130,80,255),new Color(80,80,255),new Color(80,155,255),new Color(80,230,255),new Color(80,255,205),new Color(80,255,105),
            new Color(155,255,80),new Color(205,255,80),new Color(255,255,80),new Color(255,205,80),new Color(255,155,80),Color.WHITE};
        private static Color palette(int sprite) { return COLORS[BulletVisual.th06Color(sprite)]; }
        private static int collisionRadius(BulletState state) {
            int type = BulletVisual.th06Type(state.sprite);
            double grazeSize = state.height <= 8 ? 4 : state.height <= 16 ? (type == 2 || type == 5 ? 4 : type == 4 ? 5 : 6)
                : state.height <= 32 ? (type == 7 ? 11 : type == 8 ? 9 : 16) : 32;
            return Math.max(2, (int) Math.round(grazeSize * SCALE / 2));
        }
    }
}
