import java.util.*;
import java.nio.file.*;

/** Forced-damage progression test: exercises every original phase, never fabricates a replacement pattern. */
public final class Th06FullMovesetTest {
    public static void main(String[] args) throws Exception {
        StringBuilder report = new StringBuilder();
        for (Difficulty difficulty : Difficulty.values()) for (Th06BossId id : Th06BossId.values()) {
            run(id, difficulty, 0, report);
        }
        for (int route = 1; route < 4; route++) run(Th06BossId.PATCHOULI, Difficulty.NORMAL, route, report);
        if (args.length > 0) Files.writeString(Path.of(args[0], "th06-progression.txt"), report);
    }
    static void run(Th06BossId id, Difficulty difficulty, int route, StringBuilder report) throws Exception {
        GameWorld world = GameWorld.originalBossTest(20261021, difficulty, id, route);
        try {
            world.player.invulnerability = 1000000;
            world.player.getWeapons().forEach(weapon -> weapon.timer = -10000000);
            int peak = 0, timer = 0; long frames = 0;
            for (; frames < 100000 && world.state == GameWorld.State.PLAYING; frames++) {
                world.tick(); peak = Math.max(peak, world.projectiles.size());
                if (++timer % 480 == 0) {
                    for (Enemy enemy : world.enemies) if (enemy instanceof Th06Runtime.Actor) {
                        Th06Runtime.Actor actor = (Th06Runtime.Actor) enemy;
                        if (actor.damageable) actor.takeDamage(1e8);
                    }
                }
            }
            String line = id.name() + " " + difficulty.name() + " route=" + route + " " + world.state + " frames=" + frames + " peak=" + peak + " spells=" + world.th06.observedSpellIds;
            System.out.println(line); System.out.flush(); report.append(line).append('\n');
            if (world.state != GameWorld.State.VICTORY || !world.th06.failure.isEmpty()) throw new AssertionError("Original encounter failed to finish: " + line + " " + world.th06.failure);
            if (world.captures != world.th06.observedSpellIds.size()) throw new AssertionError("Capture count does not match completed original spells: " + world.captures + " " + line);
            if (id == Th06BossId.FLANDRE && !world.th06.observedSpellIds.containsAll(Arrays.asList(54,55,56,57,58,59,60,61,62,63)))
                throw new AssertionError("Flandre skipped a spell: " + line);
            if (id == Th06BossId.REMILIA && difficulty == Difficulty.NORMAL && !world.th06.observedSpellIds.containsAll(Arrays.asList(41,42,43,44,45)))
                throw new AssertionError("Remilia skipped a spell: " + line);
            if (id == Th06BossId.CIRNO && difficulty == Difficulty.NORMAL && !world.th06.observedSpellIds.containsAll(Arrays.asList(3,5,6)))
                throw new AssertionError("Cirno skipped a spell: " + line);
        } finally { world.dispose(); }
    }
}
