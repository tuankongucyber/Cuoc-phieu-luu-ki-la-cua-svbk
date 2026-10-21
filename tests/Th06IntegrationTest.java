import java.util.*;

/** Tests real scripts in the in-process Java VM, without replacing their patterns with fixtures. */
public final class Th06IntegrationTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        int observed = 0;
        for (Th06BossId id : Th06BossId.values()) {
            GameWorld world = GameWorld.originalBossTest(20261021, Difficulty.NORMAL, id, 0);
            try {
                world.player.invulnerability = 100000;
                world.player.getWeapons().forEach(weapon -> weapon.timer = -1000000);
                int peak = 0; double worst = 0;
                for (int i = 0; i < 1200 && world.state == GameWorld.State.PLAYING; i++) {
                    world.tick(); peak = Math.max(peak, world.projectiles.size()); worst = Math.max(worst, world.th06.lastStepMillis);
                }
                check(world.th06.failure.isEmpty(), "Host failed: " + id + " " + world.th06.failure);
                check(peak > 0 || !world.th06.beams.isEmpty(), "No original attacks from " + id);
                check(world.boss.title().equals(id.label) && world.boss.spellName().isEmpty(), "No move name exposed");
                long frame = world.th06.frameCount;
                world.setPaused(true); for (int i = 0; i < 20; i++) world.tick();
                check(world.th06.frameCount == frame, "Pause freezes the interpreter"); world.setPaused(false);
                System.out.println("PASS " + id.name() + " peak=" + peak + " serial=" + world.boss.phase + " worstVmStepMs=" + String.format(Locale.ROOT,"%.2f",worst));
                if (args.length > 0) { world.player.invulnerability = 0; BossPreviewTest.render(world, args[0], "th06-" + id.name()); }
                observed++;
            } finally { world.dispose(); }
        }
        check(observed == Th06BossId.values().length, "All encounters exercised");
    }
}
