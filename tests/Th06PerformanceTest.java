import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.nio.file.*;

/** Includes actual companion I/O, Java collision and clipped Swing-world drawing. */
public final class Th06PerformanceTest {
    static double p95(double[] values) { Arrays.sort(values); return values[(int) Math.ceil(values.length * .95) - 1]; }
    public static void main(String[] args) throws Exception {
        GameWorld world = GameWorld.originalBossTest(20261021, Difficulty.NORMAL, Th06BossId.SAKUYA_MID, 0);
        try {
            world.player.invulnerability = 1000000;
            world.player.getWeapons().forEach(weapon -> weapon.timer = -1000000);
            // Observe entry/nonspell, then enter the denser original Misdirection phase.
            for (int i = 0; i < 480; i++) world.tick();
            for (Enemy enemy : world.enemies) enemy.takeDamage(1e8);
            world.tick();
            BufferedImage canvas = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_RGB);
            GameRenderer renderer = new GameRenderer();
            double[] update = new double[600], render = new double[600], combined = new double[600]; int peak = 0;
            for (int frame = -240; frame < 600; frame++) {
                long start = System.nanoTime(); world.tick(); long afterUpdate = System.nanoTime();
                Graphics2D g = canvas.createGraphics(); renderer.draw(g, world); g.dispose(); long end = System.nanoTime();
                peak = Math.max(peak, world.projectiles.size());
                if (frame >= 0) { update[frame] = (afterUpdate-start)/1e6; render[frame] = (end-afterUpdate)/1e6; combined[frame] = (end-start)/1e6; }
            }
            String report = String.format(Locale.ROOT, "TH06 native bridge + Java update/render: peak=%d updateP95=%.3f renderP95=%.3f combinedP95=%.3f ms", peak,p95(update),p95(render),p95(combined));
            System.out.println(report);
            if (args.length > 0) Files.writeString(Path.of(args[0], "th06-performance.txt"), report);
            if (!world.th06.failure.isEmpty() || combined[(int) Math.ceil(combined.length*.95)-1] >= 16.7) throw new AssertionError("Native encounter frame budget failed");
        } finally { world.dispose(); }
    }
}
