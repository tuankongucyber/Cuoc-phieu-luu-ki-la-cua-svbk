import java.awt.Color;
import java.util.*;

public final class GameplayTest {
    static int assertions;
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static void close(double actual, double expected, String message) { check(Math.abs(actual - expected) < 0.00001, message + ": " + actual + " != " + expected); }
    static GameWorld quiet() {
        GameWorld w = new GameWorld(42, Difficulty.NORMAL); w.director.nextSpawn = Integer.MAX_VALUE;
        w.player.getWeapons().forEach(weapon -> weapon.timer = -1_000_000); return w;
    }
    static Projectile hostile(double x, double y, double vx, double vy) {
        return new Projectile(x, y, vx, vy, 12, 4, Color.RED, 1000, true);
    }
    static void movementAndDamage() {
        Player p = new Player(0, 0); InputState input = new InputState();
        input.right = true; p.step(input); close(p.x, 3.5, "normal movement");
        input.focus = true; p.step(input); close(p.x, 3.5 + 3.5 * 0.45, "focus movement");
        double start = p.x; input.dashPressed = true;
        for (int i = 0; i < 9; i++) p.step(input);
        close(p.x - start, 120, "dash distance independent of focus");
        check(p.invulnerability == 0 && p.dashRemaining == 0, "dash gives no invulnerability");
        check(p.hit(12), "initial damage");
        check(!p.hit(12) && p.hitsTaken == 1, "same-frame damage gated");
        input.clear();
        for (int i = 0; i < 44; i++) p.step(input);
        check(!p.hit(12), "44 ticks still protected");
        p.step(input); check(p.hit(12), "45 ticks permits damage");
        check(p.useBomb() && p.invulnerability == 60 && p.bombs == 1, "bomb immunity and resource");
        p.useBomb(); check(!p.useBomb() && p.bombsUsed == 2, "bomb cannot be used without stock");

        GameWorld w = quiet();
        w.hurtPlayer(12, "test hit");
        check(w.noticeTicks == 0 && w.notice.isEmpty(), "taking damage does not show a yellow notice");
        check(w.lastDamageSource.equals("test hit"), "damage source retained for defeat statistics");
        w.player.invulnerability = 0; w.notice("existing notice", 90);
        w.hurtPlayer(12, "second hit");
        check(w.noticeTicks == 90 && w.notice.equals("existing notice"), "damage does not replace an existing notice");
        w.hurtPlayer(12, "blocked hit");
        check(w.player.getHp() == 76 && w.lastDamageSource.equals("second hit"), "silent damage still respects immunity");
        w = quiet();
        w.addProjectile(hostile(350, 375, 300, 0)); w.tick();
        close(w.player.getHp(), 88, "fast projectile swept collision");
        w = quiet(); Enemy enemy = new Zombie(547, 359); enemy.setSpeed(0); w.addEnemy(enemy);
        w.input.right = true; w.input.dashPressed = true;
        for (int i = 0; i < 9; i++) w.tick();
        check(w.player.getHp() < 100, "dash cannot tunnel through body");
        w = quiet(); w.addProjectile(hostile(560, 375, 0, 0));
        w.input.right = true; w.input.dashPressed = true;
        for (int i = 0; i < 9; i++) w.tick();
        close(w.player.getHp(), 88, "dash cannot cross a bullet");
        w = quiet(); Enemy fast = new Bat(368, 366); fast.setSpeed(240); w.addEnemy(fast); w.tick();
        check(w.player.getHp() < 100, "fast enemy relative swept collision");
        w = quiet(); w.addProjectile(hostile(514, 375, 0, 0)); w.input.bombPressed = true; w.tick();
        check(w.projectiles.stream().noneMatch(p2 -> p2.hostile) && w.grazes == 0 && w.player.bombs == 1, "bomb clear earns no graze");
        w = quiet(); ExpGem gem = new ExpGem(w.player.getCenterX(), w.player.getCenterY(), 5);
        gem.update(w.player);
        check(gem.isCollected() && Double.isFinite(gem.getX()) && w.player.getCurrentExp() == 5, "zero-distance pickup");
        gem.update(w.player); check(w.player.getCurrentExp() == 5, "pickup applies once");
        gem = new ExpGem(w.player.getCenterX() + 25, w.player.getCenterY(), 1); gem.magnetSpeed = 1000;
        gem.update(w.player); check(gem.isCollected(), "high magnet speed cannot overshoot pickup");
    }
    static void grazeAndCollision() {
        GameWorld w = quiet(); Projectile p = hostile(514, 375, 0, 0); w.addProjectile(p);
        for (int i = 0; i < 20; i++) w.tick();
        check(w.grazes == 1 && w.score == 10, "one graze per bullet");
        w = quiet();
        for (int i = 0; i < 60; i++) w.addProjectile(hostile(514, 375 + (i % 2), 0, 0));
        w.tick(); check(w.grazes == 60 && w.player.getCurrentExp() == 2, "graze EXP limited to 2 per second");
        for (int i = 0; i < 20; i++) w.addProjectile(hostile(514, 375, 0, 0));
        w.tick(); check(w.player.getCurrentExp() == 2, "same second cannot mint more graze EXP");
        w.player.invulnerability = 10; w.addProjectile(hostile(514, 375, 0, 0));
        int before = w.grazes; w.tick(); check(w.grazes == before, "no graze while invulnerable");
        w = quiet(); w.input.right = true; w.input.dashPressed = true; w.addProjectile(hostile(514, 391, 0, 0)); w.tick();
        check(w.grazes == 0, "no graze during dash");
        w = quiet(); w.addProjectile(hostile(508, 375, 0, 0)); w.tick();
        check(w.player.getHp() == 88 && w.grazes == 0, "touching hitbox boundary hits, never grazes");
        w = quiet(); w.addProjectile(hostile(522, 375, 0, 0)); w.tick();
        check(w.player.getHp() == 100 && w.grazes == 1, "graze boundary included");

        SpatialGrid<Object> grid = new SpatialGrid<>(); Object value = new Object();
        grid.insert(value, -100, -100, 100, 100); List<Object> found = new ArrayList<>();
        grid.query(-200, -200, 200, 200, found);
        check(found.size() == 1, "grid deduplicates objects across negative cells");
        check(Collision.swept(-40, 0, 40, 0, 8), "swept geometry");
        check(!Collision.swept(-40, 9, 40, 9, 8), "swept geometry miss");
    }
    static void builds() {
        GameWorld w = quiet();
        w.player.gainExp(35); w.checkLevelUp();
        long tick = w.ticks; Object choices = w.upgradeChoices;
        w.tick(); check(w.ticks == tick && w.upgradeChoices == choices, "level up freezes choices");
        Set<String> ids = new HashSet<>();
        for (UpgradeChoice c : w.upgradeChoices) check(ids.add(c.id), "choices unique");
        int exp = w.player.getCurrentExp(); w.reroll(); w.reroll(); w.reroll();
        check(w.rerolls == 0 && w.player.getCurrentExp() == exp, "only two free rerolls");
        w.chooseUpgrade(0); check(w.player.getLevel() == 2 && w.player.getCurrentExp() == 25, "EXP cost at first level");
        w.chooseUpgrade(0); check(w.player.getLevel() == 3 && w.player.getCurrentExp() == 10 && w.state == GameWorld.State.PLAYING, "EXP surplus preserved");
        w = quiet();
        for (int i = 0; i < 4; i++) w.player.getWeapon(WeaponId.WAND).upgrade();
        for (int i = 0; i < 3; i++) w.player.upgrade(PassiveId.COOLDOWN);
        w.player.gainExp(10); w.checkLevelUp();
        check(w.upgradeChoices.stream().anyMatch(c -> c.id.equals("evolve:PRISM")), "evolution guaranteed");
        for (int i = 0; i < w.upgradeChoices.size(); i++) if (w.upgradeChoices.get(i).id.equals("evolve:PRISM")) { w.chooseUpgrade(i); break; }
        check(w.player.getWeapon(WeaponId.PRISM) != null && w.player.getWeapon(WeaponId.WAND) == null && w.player.getWeapons().size() == 1, "evolution replaces slot");
        check(BuildSystem.choices(w).stream().noneMatch(c -> c.id.equals("weapon:WAND")), "evolved base cannot be reacquired");
        Player p = new Player(0, 0);
        p.addWeapon(new MagicWand()); p.addWeapon(new OrbitBlade()); p.addWeapon(new HolyAura()); p.addWeapon(new ChainLightning());
        boolean rejected = false; try { p.addWeapon(new PiercingShot()); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected && p.getWeapons().size() == 4, "weapon slots enforced");
        for (PassiveId id : new PassiveId[]{PassiveId.DAMAGE, PassiveId.COOLDOWN, PassiveId.AREA, PassiveId.AMOUNT})
            for (int i = 0; i < 3; i++) p.upgrade(id);
        check(!p.canUpgrade(PassiveId.SPEED) && !p.canUpgrade(PassiveId.DAMAGE), "passive slots and rank enforced");
        close(p.getDamageMultiplier(), 1.3, "additive damage");
        close(p.cooldownMultiplier(), 0.76, "bounded cooldown");
        close(p.areaMultiplier(), 1.3, "additive area");
        for (WeaponId id : WeaponId.values()) {
            Weapon weapon = Weapon.create(id);
            while (!weapon.isMaxLevel()) weapon.upgrade();
            rejected = false; try { weapon.upgrade(); } catch (IllegalStateException expected) { rejected = true; }
            check(rejected, "finite cap " + id);
        }
        w = quiet(); w.player.evolve(WeaponId.WAND, new MagicWand(true)); w.player.addWeapon(new OrbitBlade(true));
        w.player.addWeapon(new HolyAura()); w.player.addWeapon(new ChainLightning());
        for (Weapon weapon : w.player.getWeapons()) while (!weapon.isMaxLevel()) weapon.upgrade();
        for (PassiveId id : new PassiveId[]{PassiveId.DAMAGE, PassiveId.COOLDOWN, PassiveId.AREA, PassiveId.AMOUNT})
            for (int i = 0; i < 3; i++) w.player.upgrade(id);
        List<UpgradeChoice> fallback = BuildSystem.choices(w);
        check(fallback.size() == 3 && fallback.stream().allMatch(c -> c.id.startsWith("score:")), "fallback when all upgrades exhausted");

        // Both evolutions work independently and preserve the two other equipped weapons.
        w = quiet(); w.player.addWeapon(new OrbitBlade());
        for (int i = 0; i < 4; i++) w.player.getWeapon(WeaponId.ORBIT).upgrade();
        for (int i = 0; i < 3; i++) w.player.upgrade(PassiveId.AREA);
        for (UpgradeChoice c : BuildSystem.choices(w)) if (c.id.equals("evolve:DUAL_ORBIT")) c.apply();
        check(w.player.getWeapon(WeaponId.DUAL_ORBIT) != null && w.player.getWeapons().size() == 2, "dual-orbit evolution");
        w = quiet(); Zombie farTarget = new Zombie(487, 134); farTarget.setSpeed(0); w.addEnemy(farTarget); w.rebuildEnemyGrid();
        new MagicWand(true).fire(w);
        check(w.projectiles.stream().anyMatch(p2 -> Math.abs(p2.vx) < 1e-8), "evolved fan retains central ray against distant boss");
        w.projectiles.clear(); for (int i = 0; i < 3; i++) w.player.upgrade(PassiveId.AMOUNT);
        new PiercingShot().fire(w);
        check(w.projectiles.stream().anyMatch(p2 -> Math.abs(p2.vx) < 1e-8), "even multishot retains aimed piercing ray");
    }
    static void patternsAndDirector() {
        for (Difficulty d : Difficulty.values()) for (BulletPattern pattern : BulletPattern.values()) {
            GameWorld a = new GameWorld(99, d), b = new GameWorld(99, d);
            pattern.emit(a, 500, 150, Math.PI / 2, 0.31, "test");
            pattern.emit(b, 500, 150, Math.PI / 2, 0.31, "test");
            check(!a.projectiles.isEmpty() && a.projectiles.size() == b.projectiles.size(), "pattern emits " + d + pattern);
            for (int i = 0; i < a.projectiles.size(); i++) {
                close(a.projectiles.get(i).vx, b.projectiles.get(i).vx, "deterministic pattern");
                close(a.projectiles.get(i).damage, 12 * d.enemyDamage, "difficulty bullet damage");
            }
            int before = a.projectiles.size();
            pattern.emit(a, a.player.getCenterX(), a.player.getCenterY(), 0, 0, "test");
            check(a.projectiles.size() == before, "safe emission distance");
        }
        GameWorld w = quiet();
        w.director.fieldTicks = RunDirector.MID_BOSS_AT - 1;
        w.tick(); check(w.director.segment == RunDirector.Segment.WARNING && w.director.warningTicks == 120, "two second boss warning");
        for (int i = 0; i < 120; i++) w.tick();
        check(w.inBossRoom() && w.boss.encounter == 0 && w.boss.phase == 0, "mid boss scheduled");
        int field = w.director.fieldTicks, phase = w.director.phaseTicks;
        w.setPaused(true); for (int i = 0; i < 100; i++) w.tick();
        check(w.director.fieldTicks == field && w.director.phaseTicks == phase, "pause freezes director");
        w.setPaused(false);
        for (int i = 0; i < 3; i++) {
            w.boss.takeDamage(1e9); w.tick();
            if (w.state == GameWorld.State.LEVEL_UP) while (w.state == GameWorld.State.LEVEL_UP) w.chooseUpgrade(0);
        }
        check(w.director.completedBosses == 1 && !w.inBossRoom() && w.player.bombs == 3 && w.captures == 3, "mid boss rewards and captures");
        w.director.fieldTicks = RunDirector.FINAL_BOSS_AT - 1; w.tick();
        for (int i = 0; i < 120; i++) w.tick();
        check(w.boss.encounter == 1, "final boss scheduled");
        w.player.invulnerability = 999999;
        w.director.phaseTicks = 20 * 60 * 60;
        double remainingHp = w.boss.getHp();
        w.tick();
        check(w.state == GameWorld.State.PLAYING && w.boss.phase == 0 && w.boss.getHp() == remainingHp,
            "elapsed time never clears a living boss phase");
        for (int i = 0; i < 3; i++) { w.boss.takeDamage(1e9); w.tick(); }
        check(w.state == GameWorld.State.VICTORY && w.captures == 6, "boss HP depletion completes run without a deadline");
        long end = w.ticks; w.tick(); check(w.ticks == end, "victory freezes world");
        w = quiet(); w.player.takeDamage(100); w.tick();
        check(w.state == GameWorld.State.DEFEAT, "death ends run");

        // Normal seeded runs must match, including spawn RNG and ninja dodges.
        GameWorld a = new GameWorld(123, Difficulty.NORMAL), b = new GameWorld(123, Difficulty.NORMAL);
        for (int i = 0; i < 1500; i++) {
            for (GameWorld sim : new GameWorld[]{a, b}) {
                sim.input.right = i % 400 < 200; sim.input.left = !sim.input.right;
                if (sim.state == GameWorld.State.LEVEL_UP) sim.chooseUpgrade(0);
                sim.tick();
            }
        }
        check(a.state == b.state && a.enemies.size() == b.enemies.size() && a.score == b.score, "deterministic simulation");
        close(a.player.getHp(), b.player.getHp(), "deterministic damage");
    }
    static void fixedClock() {
        SimulationClock clock = new SimulationClock(); int[] steps = {0}; long now = 1;
        clock.advance(now, true, () -> steps[0]++);
        for (int i = 0; i < 60; i++) clock.advance(now += SimulationClock.STEP_NANOS, true, () -> steps[0]++);
        check(steps[0] == 60, "fixed sixty ticks");
        clock.advance(now += 5_000_000_000L, false, () -> steps[0]++);
        clock.advance(now += SimulationClock.STEP_NANOS, true, () -> steps[0]++);
        check(steps[0] == 61, "pause accumulates no time debt");
        int catchup = clock.advance(now += 5_000_000_000L, true, () -> steps[0]++);
        check(catchup == 5, "bounded catch-up after stall");
    }
    static void phaseDifficultyAndFreeze() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameWorld world = new GameWorld(44, difficulty);
            world.director.segment = RunDirector.Segment.BOSS; world.director.completedBosses = 1; world.director.phase = 2;
            world.enterBoss(1); world.boss.beginPhase(2);
            close(world.boss.getHp(), 12400 * difficulty.enemyHealth, "boss phase HP scaling");
            close(world.boss.getContactDamage(), 18 * difficulty.enemyDamage, "boss body damage scaling");
            world.boss.phaseAge = 60; world.boss.dashWait = 1; world.boss.fight(world);
            check(world.boss.dashTicks == 14 && world.boss.dashRemaining == difficulty.bossDashCount - 1, "difficulty dash chain begins");
            for (int i = 0; i < 14; i++) world.boss.fight(world);
            check(world.boss.dashWait == (difficulty == Difficulty.HARD ? difficulty.bossWarning + 12 : difficulty.bossDashCooldown), "dash chain recovery");
            if (difficulty == Difficulty.HARD) {
                for (int i = 0; i < difficulty.bossWarning + 12; i++) world.boss.fight(world);
                check(world.boss.dashTicks == 14 && world.boss.dashRemaining == 0, "hard second dash telegraphs and fires");
            }
        }
        GameWorld w = quiet(); w.player.dashCooldown = 90; w.addProjectile(hostile(800, 300, -2, 1));
        w.setPaused(true); long ticks = w.ticks; int age = w.projectiles.get(0).age;
        for (int i = 0; i < 100; i++) w.tick();
        check(w.ticks == ticks && w.projectiles.get(0).age == age && w.player.dashCooldown == 90, "pause freezes bullets and cooldown");
        w.setPaused(false); w.player.gainExp(10); w.checkLevelUp();
        for (int i = 0; i < 100; i++) w.tick();
        check(w.ticks == ticks && w.projectiles.get(0).age == age && w.player.dashCooldown == 90, "upgrade freezes bullets and cooldown");
        w = quiet(); w.addEnemy(new Zombie(500, 500)); w.rebuildEnemyGrid();
        Enemy target = w.enemies.get(0); double hp = target.getHp();
        w.hitEnemy(target, hp * 10, WeaponId.CHAIN);
        close(w.damageByWeapon.get(WeaponId.CHAIN), hp, "damage statistics exclude overkill");
        w = quiet(); w.drops.add(new ExpGem(2000, 2000, 7)); w.enterBoss(0);
        check(w.player.getCurrentExp() == 7 && w.drops.isEmpty(), "boss transition banks uncollected EXP");
    }
    public static void main(String[] args) {
        movementAndDamage(); grazeAndCollision(); builds(); patternsAndDirector(); fixedClock(); phaseDifficultyAndFreeze();
        System.out.println("PASS GameplayTest: " + assertions + " assertions");
    }
}
