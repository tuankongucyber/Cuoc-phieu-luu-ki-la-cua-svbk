import java.awt.Color;
import java.util.*;
public final class BossReworkTest {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        GameWorld w = GameplayTest.quiet();
        for (int i = 0; i < 4; i++) w.player.getWeapon(WeaponId.WAND).upgrade();
        for (int i = 0; i < 3; i++) w.player.upgrade(PassiveId.DAMAGE);
        for (int sample = 0; sample < 100; sample++) {
            for (UpgradeChoice c : BuildSystem.choices(w)) {
                check(!c.id.equals("weapon:WAND") && !c.id.equals("passive:DAMAGE"), "maxed choices must not be offered");
                if (c.maxLevel > 0) check(c.currentLevel < c.maxLevel && c.progressLabel().contains("/" + c.maxLevel), "valid x/MAX metadata");
            }
        }
        w.player.addWeapon(new OrbitBlade()); w.player.getWeapon(WeaponId.ORBIT).upgrade();
        boolean sawProgress = false;
        for (int sample = 0; sample < 100; sample++) for (UpgradeChoice c : BuildSystem.choices(w)) if (c.id.equals("weapon:ORBIT")) {
            sawProgress = true; check(c.progressLabel().equals("2/5 → 3/5"), "current and next rank explicit");
        }
        check(sawProgress, "nonmax orbit remains eligible");
        check(BossPatterns.wallWarningUntil(210, 30) == 30 && BossPatterns.wallWarningUntil(80, 30) == 22,
            "wall telegraph predicts actual emission, including cycle changes");
        GameWorld left = PatternSurvivalTest.fixture(Difficulty.NORMAL, 0, 1);
        GameWorld right = PatternSurvivalTest.fixture(Difficulty.NORMAL, 0, 1);
        left.player.x = 200; right.player.x = 800;
        BossPatterns.update(left, left.boss, 30); BossPatterns.update(right, right.boss, 30);
        check(left.projectiles.size() == right.projectiles.size(), "ring gap independent of player position");
        for (int i = 0; i < left.projectiles.size(); i++)
            check(left.projectiles.get(i).vx == right.projectiles.get(i).vx && left.projectiles.get(i).vy == right.projectiles.get(i).vy,
                "no free player-tracking gap");
        Projectile curve = new Projectile(100, 100, 2, 0, 1, 4, Color.RED, 500, true);
        curve.turnRate = .01;
        for (int i = 0; i < 50; i++) curve.update();
        check(curve.vy > .9 && curve.y > 110, "curved bullets actually turn");
        w = GameplayTest.quiet();
        Projectile freeze = new Projectile(100, 100, 2, 0, 1, 4, Color.CYAN, 500, true);
        freeze.freezeAt = 10; freeze.aimAt = 20; freeze.releaseAt = 40; freeze.releaseSpeed = 3;
        for (int i = 0; i < 10; i++) freeze.update(w);
        double frozenX = freeze.x, frozenY = freeze.y;
        for (int i = 10; i < 20; i++) freeze.update(w);
        double snapshot = freeze.storedAngle;
        w.player.x = 800;
        for (int i = 20; i < 39; i++) freeze.update(w);
        check(freeze.x == frozenX && freeze.y == frozenY, "freeze holds bullets still");
        for (int i = 0; i < 10; i++) freeze.update(w);
        check(freeze.x != frozenX && Math.abs(Math.atan2(freeze.vy, freeze.vx) - snapshot) < 1e-8, "release uses one locked aim, not homing");
        for (int phase = 0; phase < 3; phase++) {
            w = PatternSurvivalTest.fixture(Difficulty.NORMAL, 1, phase);
            w.director.phaseTicks = 1000000;
            w.tick();
            check(w.director.phase == phase && w.state == GameWorld.State.PLAYING, "no timeout at any phase");
        }
        w = PatternSurvivalTest.fixture(Difficulty.NORMAL, 1, 2);
        Boss boss = w.boss; boss.phaseAge = 60; boss.dashWait = w.difficulty.bossWarning + 1;
        boss.fight(w); double locked = boss.dashAim;
        w.player.x += 250;
        for (int i = 0; i < w.difficulty.bossWarning; i++) boss.fight(w);
        check(boss.dashTicks == 14 && Math.abs(Math.atan2(boss.dashDy, boss.dashDx) - locked) < 1e-8, "dash follows its warning, not last-frame tracking");
        System.out.println("PASS BossReworkTest: max filtering, x/MAX, curved/frozen bullets, HP-only phases and locked dash");
    }
}
