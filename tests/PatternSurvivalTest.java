import java.util.*;
import java.nio.file.*;
/** Full-duration isolated pattern observation. No offensive DPS or defensive resource/stat overrides. */
public final class PatternSurvivalTest {
    static GameWorld fixture(Difficulty difficulty, int encounter, int phase) {
        GameWorld world = new GameWorld(442211, difficulty);
        world.director.segment = RunDirector.Segment.BOSS;
        world.director.completedBosses = encounter; world.director.phase = phase;
        world.enterBoss(encounter); world.boss.beginPhase(phase);
        world.player.getWeapons().forEach(weapon -> weapon.timer = -1000000);
        return world;
    }
    public static void main(String[] args) throws Exception {
        StringBuilder report = new StringBuilder();
        int failures = 0, idleNormalDeaths = 0;
        for (Difficulty difficulty : Difficulty.values()) for (int encounter = 0; encounter < 2; encounter++) for (int phase = 0; phase < 3; phase++) {
            GameWorld world = fixture(difficulty, encounter, phase);
            int peak = 0, observed = 0;
            for (int i = 0; i < 45 * 60 && !world.player.isDead(); i++) {
                // Isolated pattern fixture: consume graze-earned levels without adding combat upgrades.
                if (world.state == GameWorld.State.LEVEL_UP) {
                    world.player.consumeExpAndLevelUp(); world.state = GameWorld.State.PLAYING;
                    world.upgradeChoices = Collections.emptyList();
                }
                if (i % 3 == 0) PlaythroughTest.steer(world, false);
                world.tick(); observed++;
                peak = Math.max(peak, world.projectiles.size());
            }
            boolean survived = !world.player.isDead();
            if (!survived) failures++;
            GameWorld idle = fixture(difficulty, encounter, phase);
            for (int i = 0; i < 45 * 60 && idle.state == GameWorld.State.PLAYING; i++) idle.tick();
            if (difficulty == Difficulty.NORMAL && idle.player.isDead()) idleNormalDeaths++;
            String line = String.format(Locale.ROOT, "%s boss=%d phase=%d observed=%ds survived=%s hits=%d bombs=%d graze=%d peak=%d idleHP=%.0f",
                difficulty, encounter + 1, phase + 1, observed / 60, survived, world.player.hitsTaken, world.player.bombsUsed, world.grazes,
                peak, idle.player.getHp());
            System.out.println(line); System.out.flush(); report.append(line).append("\n");
            if (difficulty == Difficulty.NORMAL && peak < 70) throw new AssertionError("Pattern too sparse");
        }
        if (args.length > 0) Files.writeString(Path.of(args[0], "patterns.txt"), report);
        if (idleNormalDeaths < 3) throw new AssertionError("Boss should punish standing still");
        if (failures > 0) throw new AssertionError("Steering bot failed pattern scenarios: " + failures);
    }
}
