import java.awt.*;
import java.util.*;
public class OrbitBlade extends Weapon {
    private double angle;
    private final Map<Enemy, Long> hitUntil = new IdentityHashMap<>();
    public OrbitBlade() { this(false); }
    public OrbitBlade(boolean evolved) { super(evolved ? WeaponId.DUAL_ORBIT : WeaponId.ORBIT, 0); }
    @Override public void update(GameWorld world) {
        angle = (angle + 0.075) % (Math.PI * 2);
        hitUntil.entrySet().removeIf(e -> e.getKey().isDead() || e.getValue() <= world.ticks);
        Player player = world.player;
        int count = (isEvolved() ? 6 : 1 + (level - 1) / 2) + player.extraProjectiles();
        for (int i = 0; i < count; i++) {
            double a = angle + i * Math.PI * 2 / count;
            double radius = (isEvolved() && i % 2 == 1 ? 120 : 70) * player.areaMultiplier();
            double x = player.getCenterX() + Math.cos(a) * radius, y = player.getCenterY() + Math.sin(a) * radius;
            for (Enemy enemy : world.nearby(x, y, 55)) {
                if (!enemy.isDead() && !hitUntil.containsKey(enemy)
                    && Math.hypot(enemy.getCenterX() - x, enemy.getCenterY() - y) <= enemy.bodyRadius() + 18 * player.areaMultiplier()) {
                    world.hitEnemy(enemy, (isEvolved() ? 60 : 18 + (level - 1) * 8) * player.getDamageMultiplier(), id);
                    hitUntil.put(enemy, world.ticks + Math.round(20 * player.cooldownMultiplier()));
                }
            }
        }
    }
    @Override protected void fire(GameWorld world) {}
    @Override public void draw(Graphics2D g, Player player) {
        int count = (isEvolved() ? 6 : 1 + (level - 1) / 2) + player.extraProjectiles();
        g.setColor(new Color(250, 220, 95, 120));
        for (int i = 0; i < count; i++) {
            double a = angle + i * Math.PI * 2 / count;
            double radius = (isEvolved() && i % 2 == 1 ? 120 : 70) * player.areaMultiplier();
            int x = (int) (player.getCenterX() + Math.cos(a) * radius), y = (int) (player.getCenterY() + Math.sin(a) * radius);
            g.fillPolygon(new int[] {x, x + 7, x, x - 7}, new int[] {y - 13, y, y + 13, y}, 4);
        }
    }
    @Override public String getDescription() { return (isEvolved() ? 6 : 1 + (level - 1) / 2) + " đao × " + (isEvolved() ? 60 : 18 + (level - 1) * 8) + " sát thương. " + (isEvolved() ? "Hai quỹ đạo 70 / 120 px." : "Bảo vệ vùng 70 px quanh người."); }
}
