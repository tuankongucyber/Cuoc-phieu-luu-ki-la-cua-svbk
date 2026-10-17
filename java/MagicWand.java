import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Comparator;
import java.util.List;

/**
 * Gậy phép Ma thuật (Magic Wand).
 * Tự động tìm mục tiêu quái gần nhất và bắn các quả cầu năng lượng truy đuổi.
 */
public class MagicWand extends Weapon {
    private int projectileCount = 1;
    private double projectileSpeed = 8.0;

    public MagicWand() {
        super("Magic Wand", 40, 15); // Bắn mỗi 40 frame (~0.67s)
    }

    @Override
    public void update(Player player, List<Enemy> enemies, List<Projectile> projectiles, List<DamageText> damageTexts, Sound sound) {
        timer++;
        if (timer >= cooldown) {
            timer = 0;
            if (enemies.isEmpty()) return;

            // Tìm quái gần người chơi nhất
            Enemy target = enemies.stream()
                .filter(e -> !e.isDead())
                .min(Comparator.comparingDouble(e -> Math.hypot(e.getCenterX() - player.getCenterX(), e.getCenterY() - player.getCenterY())))
                .orElse(null);

            if (target != null) {
                double targetDx = target.getCenterX() - player.getCenterX();
                double targetDy = target.getCenterY() - player.getCenterY();
                double dist = Math.hypot(targetDx, targetDy);

                if (dist > 0) {
                    double baseAngle = Math.atan2(targetDy, targetDx);
                    double spread = 0.2; // Độ tỏa góc bắn khi bắn nhiều đạn

                    for (int i = 0; i < projectileCount; i++) {
                        double angle = baseAngle + (i - (projectileCount - 1) / 2.0) * spread;
                        double vx = Math.cos(angle) * projectileSpeed;
                        double vy = Math.sin(angle) * projectileSpeed;

                        double dmg = currentDamage * player.getDamageMultiplier();
                        projectiles.add(new Projectile(
                            player.getCenterX(), player.getCenterY(),
                            vx, vy, dmg, 6, new Color(70, 180, 255), 90
                        ));
                    }
                    sound.playShoot();
                }
            }
        }
    }

    @Override
    public void draw(Graphics2D g2, Player player) {
        // Vũ khí này vẽ đạn thông qua Projectile list
    }

    @Override
    public void upgrade() {
        level++;
        projectileCount++; // +1 tia đạn mỗi cấp
        currentDamage += 5; // +5 dmg mỗi cấp
    }

    @Override
    public String getDescription() {
        return String.format("Bắn %d tia phép | Sát thương: %.0f | Hồi chiêu: %d frame",
            projectileCount, currentDamage, cooldown);
    }

    @Override
    public Weapon copy() {
        MagicWand clone = new MagicWand();
        clone.level = this.level;
        clone.projectileCount = this.projectileCount;
        clone.projectileSpeed = this.projectileSpeed;
        clone.currentDamage = this.currentDamage;
        clone.cooldown = this.cooldown;
        return clone;
    }
}
