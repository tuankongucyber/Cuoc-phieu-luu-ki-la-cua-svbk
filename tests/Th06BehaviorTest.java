import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import javax.swing.*;

public final class Th06BehaviorTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        System.clearProperty("svbk.legacyBosses");
        GameWorld a = GameWorld.originalBossTest(42, Difficulty.NORMAL, Th06BossId.SAKUYA, 0);
        GameWorld b = GameWorld.originalBossTest(42, Difficulty.NORMAL, Th06BossId.SAKUYA, 0);
        try {
            a.player.invulnerability = b.player.invulnerability = 100000;
            for (int i = 0; i < 500; i++) { a.tick(); b.tick(); }
            check(a.projectiles.size() == b.projectiles.size(), "seed repeats original bullet count");
            for (int i = 0; i < a.projectiles.size(); i++) {
                Projectile x = a.projectiles.get(i), y = b.projectiles.get(i);
                check(x.x == y.x && x.y == y.y, "seed repeats trajectory");
            }
            java.util.Set<Integer> before = new HashSet<>();
            for (Projectile p : a.projectiles) if (p instanceof Th06Runtime.Bullet) before.add(((Th06Runtime.Bullet) p).id);
            check(!before.isEmpty(), "original hostile bullets present in Java simulation");
            a.input.bombPressed = true; a.tick();
            check(a.player.bombs == 2 && a.player.bombsUsed == 1, "Bomb uses existing resource rules");
            for (Projectile p : a.projectiles) if (p instanceof Th06Runtime.Bullet)
                check(!before.contains(((Th06Runtime.Bullet) p).id), "Bomb clears interpreter bullets too");
            check(a.lastDamageSource.isEmpty() || !a.lastDamageSource.contains("Clock"), "no move names in damage notices");
            a.player.invulnerability = 0;
            double hp = a.player.getHp(), x = a.player.getCenterX(), y = a.player.getCenterY();
            a.player.previousCenterX = x - 60; a.player.previousCenterY = y;
            a.th06.beams.clear(); a.th06.beams.add(new Th06Runtime.Beam(x - 30, y - 80, x - 30, y + 80, 8, false, 0));
            a.th06.collideBeams(a); check(a.player.getHp() == hp, "laser telegraph is harmless");
            a.th06.beams.set(0, new Th06Runtime.Beam(x - 30, y - 80, x - 30, y + 80, 8, true, 0));
            a.th06.collideBeams(a); check(a.player.getHp() == hp - 12, "dash path cannot cross an active laser unharmed");
            a.th06.collideBeams(a); check(a.player.getHp() == hp - 12, "laser respects shared damage immunity");
            a.th06.close(); check(!a.th06.isRunning(), "Java simulation closes with encounter");
        } finally { a.dispose(); b.dispose(); }
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel(); panel.gameTimer.stop();
            try {
                panel.showMenu(GamePanel.Screen.BOSS_TEST);
                for (Component component : panel.menuPanel.getComponents()) if (component instanceof JComboBox<?>) {
                    JComboBox<?> combo = (JComboBox<?>) component;
                    if (combo.getItemAt(0) instanceof Th06BossId) { check(combo.getItemCount() == 14, "all original encounter choices"); combo.setSelectedItem(Th06BossId.CIRNO); }
                }
                GameMenuTest.render(panel, args[0], "th06-menu");
                BossPracticeTest.click(panel, "Đánh Boss đã chọn");
                check(panel.world.originalPractice == Th06BossId.CIRNO, "menu starts selected source boss");
                panel.world.player.invulnerability = 10000;
                for (int i = 0; i < 360; i++) panel.world.tick();
                panel.world.player.invulnerability = 0;
                GameMenuTest.render(panel, args[0], "th06-live");
                Th06Runtime first = panel.world.th06;
                panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                long frame = first.frameCount; panel.world.tick(); check(first.frameCount == frame, "pause keeps native clock frozen");
                BossPracticeTest.click(panel, "Chơi lại Test Boss");
                check(!first.isRunning() && panel.world.th06.isRunning(), "replay disposes old Java simulation");
                panel.world.state = GameWorld.State.DEFEAT;
                panel.press(KeyEvent.VK_ENTER); panel.release(KeyEvent.VK_ENTER);
                check(panel.world.originalPractice == Th06BossId.CIRNO && panel.world.player.bombs == 3, "Enter retries same original boss");
                panel.world.state = GameWorld.State.VICTORY;
                panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                check(panel.screen == GamePanel.Screen.START, "Esc returns to Start");
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { if (panel.world != null) panel.world.dispose(); panel.gameTimer.stop(); }
        });
        GameWorld run = new GameWorld(42, Difficulty.NORMAL);
        try {
            run.director.fieldTicks = RunDirector.MID_BOSS_AT - 1; run.tick();
            for (int i = 0; i < 121; i++) run.tick();
            check(run.th06 != null && run.th06.identity == Th06BossId.CIRNO, "normal run uses original Cirno");
        } finally { run.dispose(); }
        String previousData = System.getProperty("svbk.th06.data");
        String previousOffline = System.getProperty("svbk.th06.offline");
        String previousBundle = System.getProperty("svbk.th06.bundle");
        try {
            System.setProperty("svbk.th06.offline", "true");
            System.setProperty("svbk.th06.bundle", "missing-bundle-for-test.zip");
            System.setProperty("svbk.th06.data", java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "svbk-missing-th06-data-for-test").toString());
            SwingUtilities.invokeAndWait(() -> {
                GamePanel panel = new GamePanel(); panel.gameTimer.stop();
                try {
                    panel.startRun(42);
                    check(panel.screen == GamePanel.Screen.START && panel.world == null, "missing data blocks start without a mixed fallback");
                    GameMenuTest.render(panel, args[0], "th06-missing-runtime");
                    panel.startOriginalBoss(42, Th06BossId.RUMIA, 0);
                    check(panel.screen == GamePanel.Screen.BOSS_TEST && panel.world == null && !panel.bossLoadError.isEmpty(), "missing data is surfaced in boss picker");
                } catch (Exception error) { throw new RuntimeException(error); }
                finally { if (panel.world != null) panel.world.dispose(); panel.gameTimer.stop(); }
            });
        } finally {
            if (previousData == null) System.clearProperty("svbk.th06.data"); else System.setProperty("svbk.th06.data", previousData);
            if (previousOffline == null) System.clearProperty("svbk.th06.offline"); else System.setProperty("svbk.th06.offline", previousOffline);
            if (previousBundle == null) System.clearProperty("svbk.th06.bundle"); else System.setProperty("svbk.th06.bundle", previousBundle);
        }
        System.out.println("PASS Th06BehaviorTest: source determinism, Bomb, lasers, UI, pause, replay, cleanup and normal run");
    }
}
