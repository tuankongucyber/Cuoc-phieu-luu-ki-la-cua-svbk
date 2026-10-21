import java.awt.Color;
/** Reusable emitters. All aim is sampled once per emission, never homing afterwards. */
public enum BulletPattern {
    AIMED, FAN, GAP_RING, SPIRAL;
    public void emit(GameWorld world, double x, double y, double aim, double rotation, String source) {
        Difficulty d = world.difficulty;
        switch (this) {
            case AIMED: fan(world, x, y, aim, d.bulletCount(3), 0.14, 3.1, source, BulletVisual.KUNAI); break;
            case FAN: fan(world, x, y, aim, d.bulletCount(7), 0.16, 2.7, source, BulletVisual.RICE); break;
            case GAP_RING:
                int count = d.bulletCount(26);
                double gap = d == Difficulty.EASY ? 0.52 : d == Difficulty.HARD ? 0.36 : 0.44;
                for (int i = 0; i < count; i++) {
                    double a = rotation + i * Math.PI * 2 / count;
                    if (Math.abs(Math.atan2(Math.sin(a - aim), Math.cos(a - aim))) > gap)
                        shot(world, x, y, a, 2.15, source, new Color(255, 125, 170), BulletVisual.RING);
                }
                break;
            case SPIRAL:
                int arms = d == Difficulty.EASY ? 2 : d == Difficulty.HARD ? 4 : 3;
                for (int i = 0; i < arms; i++)
                    shot(world, x, y, rotation + i * Math.PI * 2 / arms, 2.25, source, new Color(255, 185, 90), BulletVisual.SHARD);
                break;
        }
    }
    private static void fan(GameWorld w, double x, double y, double aim, int count, double spread, double speed, String source, BulletVisual visual) {
        for (int i = 0; i < count; i++)
            shot(w, x, y, aim + (i - (count - 1) / 2.0) * spread, speed, source, new Color(255, 100, 130), visual);
    }
    private static void shot(GameWorld w, double x, double y, double angle, double speed, String source, Color color, BulletVisual visual) {
        // Never create a bullet overlapping or immediately next to the player's hitbox.
        if (Math.hypot(x - w.player.getCenterX(), y - w.player.getCenterY()) < 42) return;
        double velocity = speed * w.difficulty.bulletSpeed();
        Projectile p = new Projectile(x, y, Math.cos(angle) * velocity, Math.sin(angle) * velocity,
            12 * w.difficulty.enemyDamage, 4, color, 420, true);
        p.sourceLabel = source;
        p.visual = visual;
        w.addProjectile(p);
    }
}
