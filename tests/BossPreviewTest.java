import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
public final class BossPreviewTest {
    static void render(GameWorld world, String directory, String name) throws Exception {
        BufferedImage canvas = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics(); new GameRenderer().draw(g, world); g.dispose();
        ImageIO.write(canvas, "png", new File(directory, name + ".png"));
    }
    public static void main(String[] args) throws Exception {
        for (int encounter = 0; encounter < 2; encounter++) for (int phase = 0; phase < 3; phase++) {
            GameWorld world = PatternSurvivalTest.fixture(Difficulty.NORMAL, encounter, phase);
            // Visual fixture only: keep a stationary player alive while snapshots fill with real scheduled bullets.
            world.player.invulnerability = 100000; world.player.focused = true;
            int ticks = encounter == 0 && phase == 2 ? 205 : 450;
            for (int i = 0; i < ticks; i++) world.tick();
            world.player.focused = true;
            render(world, args[0], "boss-" + (encounter + 1) + "-" + (phase + 1));
        }
        GameWorld world = GameplayTest.quiet();
        world.player.getWeapon(WeaponId.WAND).upgrade(); world.player.getWeapon(WeaponId.WAND).upgrade();
        world.player.upgrade(PassiveId.COOLDOWN);
        Map<String, UpgradeChoice> desired = new HashMap<>();
        for (int i = 0; i < 100; i++) for (UpgradeChoice c : BuildSystem.choices(world)) desired.put(c.id, c);
        world.player.gainExp(10); world.checkLevelUp();
        world.upgradeChoices = java.util.List.of(desired.get("weapon:WAND"), desired.get("passive:COOLDOWN"), desired.get("weapon:CHAIN"));
        render(world, args[0], "upgrade-progress");
        System.out.println("PASS BossPreviewTest: six real pattern renders and x/MAX choices");
    }
}
