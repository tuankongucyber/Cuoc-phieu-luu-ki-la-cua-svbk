import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
/** Reproducible offscreen update + software rendering benchmark (not an FPS guarantee for every display driver). */
public final class PerformanceTest {
    public static void main(String[] args) throws Exception {
        GameWorld world = new GameWorld(20261020, Difficulty.NORMAL);
        world.director.nextSpawn = Integer.MAX_VALUE;
        world.player.invulnerability = Integer.MAX_VALUE;
        world.player.getWeapons().forEach(w -> w.timer = -1_000_000);
        Random random = new Random(3002000);
        for (int i = 0; i < 300; i++) {
            Enemy enemy = i % 4 == 0 ? new NinjaZombie(random.nextInt(1000), random.nextInt(750))
                : new Bat(random.nextInt(1000), random.nextInt(750));
            enemy.setSpeed(0); world.addEnemy(enemy); enemy.setMaxHp(1e12); enemy.setHp(1e12);
        }
        BufferedImage canvas = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_RGB);
        GameRenderer renderer = new GameRenderer();
        int warmup = 180, samples = 600;
        long[] update = new long[samples], render = new long[samples], total = new long[samples];
        for (int i = 0; i < warmup + samples; i++) {
            while (world.projectiles.size() < 2000) {
                boolean hostile = world.projectiles.size() % 4 != 0;
                double angle = random.nextDouble() * Math.PI * 2;
                Projectile p = new Projectile(random.nextInt(1000), random.nextInt(750), Math.cos(angle), Math.sin(angle),
                    0.1, 4, hostile ? Color.PINK : Color.CYAN, 3000, hostile);
                p.source = WeaponId.WAND; p.pierce = 10;
                world.addProjectile(p);
            }
            long start = System.nanoTime(); world.tick(); long updated = System.nanoTime();
            Graphics2D g = canvas.createGraphics(); renderer.draw(g, world); g.dispose();
            long end = System.nanoTime();
            if (i >= warmup) { int n = i - warmup; update[n] = updated - start; render[n] = end - updated; total[n] = end - start; }
        }
        String report = String.format(Locale.ROOT,
            "{\"scenario\":\"300 enemies + 2000 projectiles, 1000x750 offscreen\",\"samples\":%d,\"updateP95Ms\":%.3f,\"renderP95Ms\":%.3f,\"combinedP95Ms\":%.3f,\"combinedMedianMs\":%.3f,\"targetMs\":16.7}",
            samples, percentile(update, .95), percentile(render, .95), percentile(total, .95), percentile(total, .5));
        System.out.println(report);
        if (args.length > 0) Files.writeString(Path.of(args[0], "performance.json"), report);
        if (percentile(total, .95) >= 16.7) throw new AssertionError("P95 exceeds 16.7ms target");
    }
    private static double percentile(long[] values, double p) {
        long[] copy = values.clone(); Arrays.sort(copy);
        return copy[(int) Math.ceil(copy.length * p) - 1] / 1e6;
    }
}
