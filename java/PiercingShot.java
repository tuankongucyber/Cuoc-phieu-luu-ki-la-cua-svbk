import java.awt.Color;
public class PiercingShot extends Weapon {
    public PiercingShot() { super(WeaponId.PIERCING, 48); }
    @Override protected void fire(GameWorld world) {
        Player player = world.player;
        Enemy target = world.closestEnemy(player.getCenterX(), player.getCenterY(), 700, null);
        double a = target == null ? Math.atan2(player.lastDy, player.lastDx)
            : Math.atan2(target.getCenterY() - player.getCenterY(), target.getCenterX() - player.getCenterX());
        int count = 1 + player.extraProjectiles();
        for (int i = 0; i < count; i++) {
            double angle = a + fanOffset(i, 0.12);
            Projectile p = new Projectile(player.getCenterX(), player.getCenterY(),
                Math.cos(angle) * 10, Math.sin(angle) * 10, (24 + (level - 1) * 11) * player.getDamageMultiplier(),
                4, new Color(150, 220, 160), 90);
            p.source = id; p.pierce = 2 + level; world.addProjectile(p);
        }
    }
    @Override public String getDescription() { return (24 + (level - 1) * 11) + " sát thương, xuyên " + (2 + level) + " mục tiêu. Hồi " + cooldownText() + ". Bắn thẳng qua hàng quái."; }
}
