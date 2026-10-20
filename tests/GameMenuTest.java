import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Chạy trên EDT, không cần mở cửa sổ: javac -cp <build> -d <build> tests/GameMenuTest.java */
public class GameMenuTest {
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void state(GamePanel panel, GamePanel.GameState expected) throws Exception {
        check(get(panel, "state") == expected, "Expected state " + expected);
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) layout((Container) child);
        }
    }
    private static void render(GamePanel panel, String path) throws Exception {
        layout(panel);
        JPanel menu = (JPanel) get(panel, "menuPanel");
        for (Component child : menu.getComponents()) {
            check(child.getY() >= 0 && child.getY() + child.getHeight() <= menu.getHeight(),
                "Menu control clipped: " + child);
        }
        BufferedImage image = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File(path));
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            try {
                ((Timer) get(panel, "gameTimer")).stop();
                panel.setSize(1000, 750);
                state(panel, GamePanel.GameState.START);
                call(panel, "gameLoop");
                check((int) get(panel, "frameCount") == 0, "Start must freeze simulation");
                render(panel, args[0] + "/start.png");
                call(panel, "openSettings");
                render(panel, args[0] + "/settings.png");
                JPanel menu = (JPanel) get(panel, "menuPanel");
                for (Component component : menu.getComponents()) {
                    if (component instanceof JComboBox) ((JComboBox<?>) component).setSelectedItem(Difficulty.HARD);
                    if (component instanceof JCheckBox) ((JCheckBox) component).doClick();
                    if (component instanceof JSlider) ((JSlider) component).setValue(35);
                }
                Sound sound = (Sound) get(panel, "sound");
                check(!sound.isSoundEnabled() && sound.getVolume() == 35, "Audio settings not applied");
                call(panel, "handleEscape");
                state(panel, GamePanel.GameState.START);
                // Giữ Esc không được tự chuyển qua lại do key-repeat.
                call(panel, "initGame");
                panel.getActionMap().get("escape").actionPerformed(null);
                panel.getActionMap().get("escape").actionPerformed(null);
                state(panel, GamePanel.GameState.PAUSED);
                panel.getActionMap().get("escapeRelease").actionPerformed(null);
                panel.getActionMap().get("escape").actionPerformed(null);
                state(panel, GamePanel.GameState.PLAYING);
                panel.getActionMap().get("escapeRelease").actionPerformed(null);
                call(panel, "initGame");
                check(get(panel, "runDifficulty") == Difficulty.HARD, "Difficulty not applied");
                Player player = (Player) get(panel, "player");
                player.setUp(true);
                call(panel, "handleEscape");
                state(panel, GamePanel.GameState.PAUSED);
                check(!player.isUp(), "Pause must clear held movement");
                int frame = (int) get(panel, "frameCount");
                double x = player.getX();
                call(panel, "gameLoop");
                check((int) get(panel, "frameCount") == frame && player.getX() == x, "Pause must freeze simulation");
                render(panel, args[0] + "/pause.png");
                call(panel, "openSettings");
                for (Component component : menu.getComponents()) {
                    if (component instanceof JComboBox) check(!component.isEnabled(), "Mid-run difficulty must be locked");
                }
                call(panel, "handleEscape");
                state(panel, GamePanel.GameState.PAUSED);
                call(panel, "handleEscape");
                state(panel, GamePanel.GameState.PLAYING);
                call(panel, "gameLoop");
                check((int) get(panel, "frameCount") == frame + 1, "Resume must restart simulation");
                player.gainExp(10);
                call(panel, "triggerLevelUp");
                Object choices = get(panel, "activeUpgradeChoices");
                call(panel, "handleEscape");
                call(panel, "openSettings");
                call(panel, "handleEscape");
                call(panel, "handleEscape");
                state(panel, GamePanel.GameState.LEVEL_UP);
                check(get(panel, "activeUpgradeChoices") == choices, "Resume must preserve upgrade choices");

                // Sinh quái thực tế và kiểm tra cả Boss phòng riêng sau khi sao chép người chơi.
                for (Difficulty difficulty : Difficulty.values()) {
                    set(panel, "difficulty", difficulty);
                    call(panel, "initGame");
                    set(panel, "frameCount", difficulty.spawnInterval);
                    call(panel, "spawnEnemies");
                    List<?> enemies = (List<?>) get(panel, "enemies");
                    Bat base = new Bat(0, 0);
                    Enemy spawned = (Enemy) enemies.get(0);
                    check(Math.abs(spawned.getHp() - base.getHp() * difficulty.enemyHealth) < 0.001, "Enemy HP");
                    check(Math.abs(spawned.getContactDamage() - base.getContactDamage() * difficulty.enemyDamage) < 0.001, "Enemy damage");
                    check(Math.abs(spawned.getSpeed() - base.getSpeed() * difficulty.enemySpeed) < 0.001, "Enemy speed");
                    check((int) get(panel, "spawnInterval") == difficulty.spawnInterval, "Enemy density");
                    NinjaZombie ninja = new NinjaZombie(0, 0);
                    ninja.applyDifficulty(difficulty);
                    check(ninja.getDodgeCount() == difficulty.ninjaDodges, "Ninja moveset");
                    call(panel, "enterBossRoom");
                    Boss boss = (Boss) ((List<?>) get(panel, "enemies")).get(0);
                    check(Math.abs(boss.getHp() - 5000 * difficulty.enemyHealth) < 0.001, "Mirror boss HP");
                    check(Math.abs(boss.getContactDamage() - 20 * difficulty.enemyDamage) < 0.001, "Boss contact damage");
                    Player target = new Player(700, 600);
                    for (int i = 0; i < difficulty.bossDashCooldown; i++) boss.moveTowards(target);
                    check((int) get(boss, "warningTimer") == difficulty.bossWarning, "Boss warning timing");
                    check((int) get(boss, "remainingDashes") == difficulty.bossDashCount, "Boss dash chain");
                    for (int i = 0; i < difficulty.bossWarning + 14 + 25; i++) boss.moveTowards(target);
                    check((int) get(boss, "dashTimer") == (difficulty == Difficulty.HARD ? 12 : difficulty.bossDashCooldown),
                        "Hard boss must prepare a second dash immediately");
                    if (difficulty == Difficulty.HARD) {
                        for (int i = 0; i < 12; i++) boss.moveTowards(target);
                        check((int) get(boss, "warningTimer") == difficulty.bossWarning, "Second dash must telegraph again");
                        for (int i = 0; i < difficulty.bossWarning + 14 + 25; i++) boss.moveTowards(target);
                        check((int) get(boss, "dashTimer") == difficulty.bossDashCooldown, "Dash chain must end with full cooldown");
                    }
                    List<Projectile> projectiles = new ArrayList<>();
                    for (int i = 0; i < 40; i++) boss.updateWeapons(target, projectiles, new ArrayList<>(), sound);
                    check(!projectiles.isEmpty(), "Boss must fire");
                    check(Math.abs(projectiles.get(0).getDamage() - 15 * difficulty.enemyDamage) < 0.001, "Boss projectile damage");
                }
                System.out.println("PASS: start, settings, pause/resume, level-up, audio, all difficulty profiles, mirror boss and movesets");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                try { ((Timer) get(panel, "gameTimer")).stop(); } catch (Exception ignored) { }
            }
        });
    }
}
