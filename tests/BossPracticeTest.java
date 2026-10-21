import java.awt.Component;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

public final class BossPracticeTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static void click(GamePanel panel, String label) {
        for (Component child : panel.menuPanel.getComponents()) {
            if (child instanceof JButton && ((JButton) child).getText().equals(label)) {
                ((JButton) child).doClick(); return;
            }
        }
        throw new AssertionError("Missing button: " + label);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel(); panel.gameTimer.stop();
            try {
                panel.difficulty = Difficulty.HARD;
                panel.seedField.setText("12345"); click(panel, "Test Boss");
                check(panel.screen == GamePanel.Screen.BOSS_TEST && panel.selectedSeed == 12345, "chooser preserves seed");
                GameMenuTest.render(panel, args[0], "boss-test-menu");
                panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                check(panel.screen == GamePanel.Screen.START, "chooser Escape returns to Start");
                for (int encounter = 0; encounter < 2; encounter++) {
                    click(panel, "Test Boss"); click(panel, encounter == 0 ? "Test Boss giữa" : "Test Boss cuối");
                    GameWorld world = panel.world;
                    check(world.isBossTest() && world.practiceEncounter == encounter && world.inBossRoom(), "chosen boss immediately active");
                    check(world.seed == 12345 && world.difficulty == Difficulty.HARD, "seed and difficulty applied");
                    check(world.enemies.size() == 1 && world.player.getHp() == 100 && world.player.bombs == 3, "clean arena and full resources");
                    check(world.player.getWeapons().size() == 4, "four weapon build");
                    for (Weapon weapon : world.player.getWeapons()) check(weapon.getLevel() == 4, "weapon level four");
                    world.player.gainExp(1000); world.checkLevelUp();
                    check(world.state == GameWorld.State.PLAYING, "EXP does not interrupt practice");
                    GameMenuTest.render(panel, args[0], "boss-test-" + encounter);
                    panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                    GameMenuTest.render(panel, args[0], "boss-test-pause");
                    click(panel, "Chơi lại Test Boss");
                    check(panel.world != world && panel.world.practiceEncounter == encounter && !panel.world.paused, "pause replay starts fresh encounter");
                    world = panel.world;
                    for (int phase = 0; phase < 3; phase++) {
                        world.boss.takeDamage(1_000_000); world.tick();
                        if (phase < 2) check(world.director.phase == phase + 1 && world.inBossRoom(), "next phase");
                    }
                    check(world.state == GameWorld.State.VICTORY && world.director.segment == RunDirector.Segment.COMPLETE, "practice ends after chosen boss");
                    check(world.captures == 3, "three captures available");
                    GameMenuTest.render(panel, args[0], "boss-test-result");
                    panel.press(KeyEvent.VK_ENTER); panel.release(KeyEvent.VK_ENTER);
                    check(panel.world != world && panel.world.practiceEncounter == encounter && panel.world.player.bombs == 3, "Enter retries same boss");
                    panel.world.state = GameWorld.State.DEFEAT;
                    panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                }
                click(panel, "Bắt đầu");
                check(!panel.world.isBossTest() && panel.world.director.segment == RunDirector.Segment.FIELD, "normal run remains available");
                System.out.println("PASS BossPracticeTest: chooser, both bosses, build, completion, replay and normal run");
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { panel.gameTimer.stop(); }
        });
    }
}
