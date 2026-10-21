import java.awt.*;
public class HolyAura extends Weapon {
    private int pending, delay, animation;
    public HolyAura() { super(WeaponId.AURA, 90); }
    @Override public void update(GameWorld world) {
        if (animation > 0) animation--;
        if (pending > 0 && --delay <= 0) { pulse(world); pending--; delay = 18; }
        super.update(world);
    }
    @Override protected void fire(GameWorld world) {
        pulse(world); pending = (level - 1) / 2; delay = 18;
    }
    private void pulse(GameWorld world) {
        animation = 15;
        double radius = (100 + level * 5) * world.player.areaMultiplier();
        for (Enemy enemy : world.nearby(world.player.getCenterX(), world.player.getCenterY(), radius + 30)) {
            double dx = enemy.getCenterX() - world.player.getCenterX(), dy = enemy.getCenterY() - world.player.getCenterY();
            double distance = Math.hypot(dx, dy);
            if (!enemy.isDead() && distance <= radius + enemy.bodyRadius()) {
                world.hitEnemy(enemy, (24 + level * 8) * world.player.getDamageMultiplier(), id);
                if (!(enemy instanceof Boss) && distance > 0) { enemy.x += dx / distance * 12; enemy.y += dy / distance * 12; }
            }
        }
    }
    @Override public void draw(Graphics2D g, Player player) {
        int r = (int) ((100 + level * 5) * player.areaMultiplier());
        g.setColor(new Color(255, 230, 100, animation > 0 ? 45 : 15));
        g.drawOval((int) player.getCenterX() - r, (int) player.getCenterY() - r, r * 2, r * 2);
    }
    @Override public String getDescription() { return (1 + (level - 1) / 2) + " nhịp × " + (24 + level * 8) + " sát thương. Bán kính " + (100 + level * 5) + " px; hồi " + cooldownText() + ". Đẩy lùi quái."; }
}
