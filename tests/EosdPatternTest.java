import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class EosdPatternTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        for (Difficulty difficulty : Difficulty.values()) {
            GameWorld world = PatternSurvivalTest.fixture(difficulty, 1, 1);
            for (int beat = 0; beat <= 151; beat++) {
                BossPatterns.update(world, world.boss, difficulty.bossWarning + beat);
                for (Projectile p : world.projectiles) p.update(world);
                if (beat == 140) {
                    check(world.projectiles.size() > 60, "four clock layers");
                    for (Projectile p : world.projectiles)
                        check(p.knife && p.vx == 0 && p.vy == 0, "all knives wait before common release");
                }
            }
            for (Projectile p : world.projectiles)
                check(Math.hypot(p.vx, p.vy) > 0, "all clock layers launch together");
            world.player.hit(75);
            BossPreviewTest.render(world, args[0], "clock-knives-low-hp-" + difficulty.name());
        }
        Player player = new Player(100, 100);
        BufferedImage image = new BufferedImage(250, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        player.draw(g);
        check(new Color(image.getRGB(130, 89)).getGreen() > 200, "full HP green bar");
        player.hit(80); player.invulnerability = 5;
        player.draw(g); g.dispose();
        check(new Color(image.getRGB(95, 89)).getRed() > 200, "low HP red bar stays visible while blinking");
        check(new Color(image.getRGB(130, 89)).getRed() < 100, "bar width follows current HP");
        System.out.println("PASS EosdPatternTest: synchronized knives on three difficulties and visible proportional HP bar");
    }
}
