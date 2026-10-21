import java.nio.file.*;
import java.util.concurrent.*;

import javax.swing.*;

/** Fresh-cache setup from the known local pack, without internet or external executables. */
public final class Th06SetupIntegrationTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path source = Th06Data.directory().resolve("reference-data.zip");
        check(
                Files.isRegularFile(source),
                "integration fixture needs the known reference-data.zip");
        Path fresh = Files.createTempDirectory("svbk-setup-integration-");
        String previous = System.getProperty("svbk.th06.data");
        GamePanel[] panel = new GamePanel[1];
        CountDownLatch complete = new CountDownLatch(1);
        try {
            Files.copy(source, fresh.resolve("reference-data.zip"));
            System.setProperty("svbk.th06.data", fresh.toString());
            SwingUtilities.invokeAndWait(
                    () -> {
                        panel[0] = new GamePanel();
                        panel[0].gameTimer.stop();
                        panel[0].startRun(42);
                        check(
                                panel[0].preparingBossData && panel[0].world == null,
                                "start prepares missing extracted data asynchronously");
                        panel[0].dataTask.addPropertyChangeListener(
                                e -> {
                                    if ("state".equals(e.getPropertyName())
                                            && e.getNewValue() == SwingWorker.StateValue.DONE)
                                        complete.countDown();
                                });
                        for (var component : panel[0].menuPanel.getComponents())
                            if (component instanceof JButton button)
                                check(!button.isEnabled(), "setup blocks duplicate launches");
                        try {
                            GameMenuTest.render(panel[0], args[0], "th06-first-run-setup");
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
            SwingUtilities.invokeAndWait(
                    () -> check(panel[0] != null, "EDT stays responsive during archive setup"));
            check(complete.await(30, TimeUnit.SECONDS), "setup completes");
            SwingUtilities.invokeAndWait(
                    () -> {
                        check(
                                panel[0].screen == GamePanel.Screen.GAME
                                        && panel[0].world != null
                                        && panel[0].world.seed == 42,
                                "original start continues automatically after setup");
                        check(
                                panel[0].bossLoadError.isEmpty()
                                        && Th06Data.ready(1, 2, 3, 4, 5, 6, 7),
                                "all real scripts and metadata extracted");
                        panel[0].showMenu(GamePanel.Screen.BOSS_TEST);
                        panel[0].startOriginalBoss(42, Th06BossId.SAKUYA, 0);
                        check(
                                panel[0].screen == GamePanel.Screen.GAME
                                        && panel[0].world.th06 != null,
                                "test boss starts with prepared data");
                        panel[0].world.player.invulnerability = 100000;
                        for (int i = 0; i < 600; i++) panel[0].world.tick();
                        check(
                                panel[0].world.th06.failure.isEmpty()
                                        && !panel[0].world.projectiles.isEmpty(),
                                "real Sakuya script fires after setup");
                    });
        } finally {
            SwingUtilities.invokeAndWait(
                    () -> {
                        if (panel[0] != null) {
                            panel[0].gameTimer.stop();
                            if (panel[0].world != null) panel[0].world.dispose();
                        }
                    });
            if (previous == null) System.clearProperty("svbk.th06.data");
            else System.setProperty("svbk.th06.data", previous);
            try (var files = Files.list(fresh)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(fresh);
        }
        System.out.println(
                "PASS Th06SetupIntegrationTest: fresh cache, responsive loading UI, automatic start"
                    + " and real Boss fire");
    }
}
