import java.awt.Color;
public class MagicWand extends Weapon {
    public MagicWand() { this(false); }
    public MagicWand(boolean evolved) { super(evolved ? WeaponId.PRISM : WeaponId.WAND, evolved ? 30 : 38); }
    @Override protected void fire(GameWorld world) {
        Enemy target = world.closestEnemy(world.player.getCenterX(), world.player.getCenterY(), 650, null);
        if (target == null) return;
        double angle = Math.atan2(target.getCenterY() - world.player.getCenterY(), target.getCenterX() - world.player.getCenterX());
        int count = (isEvolved() ? 4 : 1 + (level - 1) / 2) + world.player.extraProjectiles();
        for (int i = 0; i < count; i++) {
            double a = angle + fanOffset(i, isEvolved() ? 0.15 : 0.11);
            Projectile p = new Projectile(world.player.getCenterX(), world.player.getCenterY(),
                Math.cos(a) * 8, Math.sin(a) * 8, (isEvolved() ? 58 : 15 + (level - 1) * 8) * world.player.getDamageMultiplier(),
                4, Color.CYAN, 95);
            p.pierce = isEvolved() ? 4 : 1; p.source = id; world.addProjectile(p);
        }
    }
    @Override public String getDescription() {
        return (isEvolved() ? 4 : 1 + (level - 1) / 2) + " tia × " + (isEvolved() ? 58 : 15 + (level - 1) * 8)
            + " sát thương. Hồi " + cooldownText() + ". Tự nhắm; " + (isEvolved() ? "xuyên 4 mục tiêu." : "ưu tiên tia chính giữa.");
    }
}
