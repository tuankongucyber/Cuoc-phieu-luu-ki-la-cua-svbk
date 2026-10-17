import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Vòng hào quang Thần thánh (Holy Aura / Garlic AoE).
 * Tỏa sóng xung kích định kỳ quanh người chơi, gây sát thương diện rộng và đẩy lùi quái.
 */
public class HolyAura extends Weapon {
    private double auraRadius = 110;
    private int pulseAnimTimer = 0;
    private final int PULSE_DURATION = 15;
    private int pulseCount = 1;      // Số lần nổ mỗi chu kỳ (tăng khi nâng cấp)
    private int pulseRemaining = 0;  // Số lần nổ còn lại trong chu kỳ hiện tại
    private int pulseDelay = 0;      // Timer delay giữa các lần nổ liên tiếp
    private final int PULSE_INTERVAL = 18; // Frame giữa 2 lần nổ liên tiếp

    public HolyAura() {
        super("Holy Aura", 75, 25);
    }

    @Override
    public void update(Player player, List<Enemy> enemies, List<Projectile> projectiles, List<DamageText> damageTexts, Sound sound) {
        if (pulseAnimTimer > 0) pulseAnimTimer--;

        // Xử lý các lần nổ liên tiếp còn lại
        if (pulseRemaining > 0) {
            pulseDelay--;
            if (pulseDelay <= 0) {
                doPulse(player, enemies, damageTexts, sound);
                pulseRemaining--;
                pulseDelay = PULSE_INTERVAL;
            }
        }

        timer++;
        if (timer >= cooldown) {
            timer = 0;
            // Bắt đầu chuỗi nổ
            doPulse(player, enemies, damageTexts, sound);
            pulseRemaining = pulseCount - 1; // Số lần nổ thêm còn lại
            pulseDelay = PULSE_INTERVAL;
        }
    }

    /** Thực hiện 1 lần nổ hào quang */
    private void doPulse(Player player, List<Enemy> enemies, List<DamageText> damageTexts, Sound sound) {
        pulseAnimTimer = PULSE_DURATION;
        sound.playHit();
        for (Enemy enemy : enemies) {
            if (enemy.isDead()) continue;
            double dx = enemy.getCenterX() - player.getCenterX();
            double dy = enemy.getCenterY() - player.getCenterY();
            double dist = Math.hypot(dx, dy);
            if (dist <= auraRadius) {
                double dmg = currentDamage * player.getDamageMultiplier();
                enemy.takeDamage(dmg);
                damageTexts.add(new DamageText(enemy.getX(), enemy.getY() - 10, String.valueOf((int) dmg), new Color(255, 255, 100), true));
                if (dist > 0) {
                    enemy.setX(enemy.getX() + (dx / dist) * 20);
                    enemy.setY(enemy.getY() + (dy / dist) * 20);
                }
            }
        }
    }

    @Override
    public void draw(Graphics2D g2, Player player) {
        // Vòng mờ tĩnh hiển thị tầm hoạt động
        g2.setColor(new Color(255, 215, 0, 30));
        g2.fillOval((int) (player.getCenterX() - auraRadius), (int) (player.getCenterY() - auraRadius), (int) auraRadius * 2, (int) auraRadius * 2);

        // Hiệu ứng sóng nổ xung kích khi kích hoạt
        if (pulseAnimTimer > 0) {
            float progress = 1.0f - ((float) pulseAnimTimer / PULSE_DURATION);
            int currentR = (int) (auraRadius * progress);
            int alpha = (int) (200 * (1.0f - progress));

            g2.setColor(new Color(255, 230, 80, alpha));
            g2.setStroke(new BasicStroke(3.0f));
            g2.drawOval((int) (player.getCenterX() - currentR), (int) (player.getCenterY() - currentR), currentR * 2, currentR * 2);
            g2.setStroke(new BasicStroke(1.0f));
        }
    }

    @Override
    public void upgrade() {
        level++;
        pulseCount++; // +1 lần nổ liên tiếp mỗi cấp
        currentDamage += 5; // +5 dmg mỗi cấp
    }

    @Override
    public String getDescription() {
        return String.format("%d lần nổ/chu kỳ | Sát thương: %.0f | Bán kính: %.0fpx",
            pulseCount, currentDamage, auraRadius);
    }

    public double getAuraRadius() { return auraRadius; }

    @Override
    public Weapon copy() {
        HolyAura clone = new HolyAura();
        clone.level = this.level;
        clone.pulseCount = this.pulseCount;
        clone.auraRadius = this.auraRadius;
        clone.currentDamage = this.currentDamage;
        clone.cooldown = this.cooldown;
        return clone;
    }
}
