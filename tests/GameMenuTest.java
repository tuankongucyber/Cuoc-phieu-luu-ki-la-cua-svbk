import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class GameMenuTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
    static void render(GamePanel panel, String dir, String name) throws Exception {
        panel.setSize(1000, 750); layout(panel);
        if (panel.menuPanel.isVisible()) for (Component child : panel.menuPanel.getComponents())
            check(child.getY() >= 0 && child.getY() + child.getHeight() <= panel.menuPanel.getHeight(), "clipped menu " + child);
        BufferedImage image = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); panel.paint(g); g.dispose();
        ImageIO.write(image, "png", new File(dir, name + ".png"));
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel(); panel.gameTimer.stop();
            try {
                check(panel.screen == GamePanel.Screen.START, "starts in menu"); render(panel, args[0], "start");
                panel.settingsReturn = GamePanel.Screen.START; panel.showMenu(GamePanel.Screen.SETTINGS);
                for (Component component : panel.menuPanel.getComponents()) {
                    if (component instanceof JComboBox) ((JComboBox<?>) component).setSelectedItem(Difficulty.HARD);
                    if (component instanceof JCheckBox) ((JCheckBox) component).doClick();
                    if (component instanceof JSlider) ((JSlider) component).setValue(35);
                }
                check(panel.difficulty == Difficulty.HARD && !panel.sound.isSoundEnabled() && panel.sound.getVolume() == 35, "settings wired");
                render(panel, args[0], "settings");
                panel.startRun(42); panel.press(KeyEvent.VK_W); panel.press(KeyEvent.VK_SHIFT);
                check(panel.world.input.up && panel.world.input.focus, "movement and focus routing");
                panel.press(KeyEvent.VK_ESCAPE); panel.press(KeyEvent.VK_ESCAPE);
                check(panel.screen == GamePanel.Screen.PAUSE && panel.world.paused, "escape repeat does not resume");
                panel.release(KeyEvent.VK_ESCAPE); panel.press(KeyEvent.VK_ESCAPE); panel.press(KeyEvent.VK_ESCAPE);
                check(panel.screen == GamePanel.Screen.GAME && !panel.world.paused, "resume repeat does not re-pause");
                panel.release(KeyEvent.VK_ESCAPE);
                panel.world.player.gainExp(20); panel.world.checkLevelUp();
                Object choices = panel.world.upgradeChoices;
                panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                render(panel, args[0], "pause");
                panel.settingsReturn = GamePanel.Screen.PAUSE; panel.showMenu(GamePanel.Screen.SETTINGS);
                for (Component component : panel.menuPanel.getComponents()) if (component instanceof JComboBox)
                    check(!component.isEnabled(), "difficulty locked during run");
                panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                panel.press(KeyEvent.VK_ESCAPE); panel.release(KeyEvent.VK_ESCAPE);
                check(panel.world.state == GameWorld.State.LEVEL_UP && panel.world.upgradeChoices == choices, "upgrade screen survives settings");
                render(panel, args[0], "upgrades");
                panel.world.chooseUpgrade(0); panel.world.chooseUpgrade(0);
                panel.pauseForFocusLoss();
                check(panel.screen == GamePanel.Screen.PAUSE && !panel.world.input.up, "focus loss pauses");
                panel.resume();
                panel.world.director.segment = RunDirector.Segment.BOSS;
                panel.world.enterBoss(1); panel.world.boss.beginPhase(1);
                panel.world.director.phase = 1;
                panel.world.boss.phaseAge = 125;
                for (int n = 0; n < 12; n++) {
                    BulletPattern.SPIRAL.emit(panel.world, 500, 170, Math.PI / 2, n * 0.29, "preview");
                    for (Projectile p : panel.world.projectiles) for (int i = 0; i < 10; i++) p.update();
                }
                panel.world.player.focused = true;
                render(panel, args[0], "boss");
                panel.world.state = GameWorld.State.VICTORY; render(panel, args[0], "result");
                System.out.println("PASS GameMenuTest: menus, bindings, repeat, settings, focus loss and six renders");
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { panel.gameTimer.stop(); }
        });
    }
}
