import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lưỡi phi đao xoay (Orbiting Blades).
 * Xoay tròn xung quanh bảo vệ nhân vật, gây sát thương chém quét khi quái chạm vào.
 */
public class OrbitBlade extends Weapon {
    private int bladeCount = 1;
    private double currentAngle = 0;
    private double orbitRadius = 70;
    private double rotateSpeed = 0.07;
    // Lưu thời gian nhận đòn của quái để tránh trừ máu liên tục mỗi frame
    private Map<Enemy, Integer> hitCooldowns = new HashMap<>();

    public OrbitBlade() {
        super("Orbit Blades", 0, 18);
    }

    @Override
    public void update(Player player, List<Enemy> enemies, List<Projectile> projectiles, List<DamageText> damageTexts, Sound sound) {
        currentAngle += rotateSpeed;
        if (currentAngle >= Math.PI * 2) {
            currentAngle -= Math.PI * 2;
        }

        // Cập nhật cooldown đòn chém cho từng quái
        hitCooldowns.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });

        // Kiểm tra va chạm giữa từng lưỡi đao với quái
        for (int i = 0; i < bladeCount; i++) {
            double angle = currentAngle + i * (Math.PI * 2 / bladeCount);
            double bx = player.getCenterX() + Math.cos(angle) * orbitRadius;
            double by = player.getCenterY() + Math.sin(angle) * orbitRadius;
            double bladeHitRadius = 20;

            for (Enemy enemy : enemies) {
                if (enemy.isDead() || hitCooldowns.containsKey(enemy)) continue;

                double dist = Math.hypot(enemy.getCenterX() - bx, enemy.getCenterY() - by);
                if (dist < bladeHitRadius + enemy.getWidth() / 2.0) {
                    double finalDmg = currentDamage * player.getDamageMultiplier();
                    enemy.takeDamage(finalDmg);
                    damageTexts.add(new DamageText(enemy.getX(), enemy.getY() - 10, String.valueOf((int) finalDmg), Color.ORANGE, false));
                    hitCooldowns.put(enemy, 15); // Quái nhận sát thương tối đa 4 lần/giây từ đao
                    sound.playHit();
                }
            }
        }
    }

    @Override
    public void draw(Graphics2D g2, Player player) {
        for (int i = 0; i < bladeCount; i++) {
            double angle = currentAngle + i * (Math.PI * 2 / bladeCount);
            double bx = player.getCenterX() + Math.cos(angle) * orbitRadius;
            double by = player.getCenterY() + Math.sin(angle) * orbitRadius;

            AffineTransform old = g2.getTransform();
            g2.translate(bx, by);
            g2.rotate(angle + Math.PI / 2);

            // Vệt sáng
            g2.setColor(new Color(255, 215, 0, 100));
            g2.fillOval(-10, -16, 20, 32);

            // Thân kiếm/đao
            g2.setColor(new Color(255, 230, 100));
            int[] xPoints = {0, 6, 0, -6};
            int[] yPoints = {-16, 0, 14, 0};
            g2.fillPolygon(xPoints, yPoints, 4);

            // Cán đao
            g2.setColor(new Color(180, 50, 20));
            g2.fillRect(-2, 10, 4, 8);

            g2.setTransform(old);
        }
    }

    @Override
    public void upgrade() {
        level++;
        bladeCount++; // +1 lưỡi đao mỗi cấp
        currentDamage += 5; // +5 dmg mỗi cấp
    }

    @Override
    public String getDescription() {
        return String.format("%d lưỡi đao xoay | Sát thương: %.0f | Bán kính: %.0fpx",
            bladeCount, currentDamage, orbitRadius);
    }

    @Override
    public Weapon copy() {
        OrbitBlade clone = new OrbitBlade();
        clone.level = this.level;
        clone.bladeCount = this.bladeCount;
        clone.orbitRadius = this.orbitRadius;
        clone.rotateSpeed = this.rotateSpeed;
        clone.currentDamage = this.currentDamage;
        clone.cooldown = this.cooldown;
        return clone;
    }
}
