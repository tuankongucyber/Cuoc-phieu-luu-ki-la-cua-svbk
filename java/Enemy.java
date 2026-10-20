import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Lớp cha trừu tượng cho tất cả quái vật trong game.
 * Kế thừa Entity, cung cấp cơ chế di chuyển bám đuổi nhân vật và rơi phần thưởng khi chết.
 */
public abstract class Enemy extends Entity {
    protected double contactDamage;
    protected int expReward;
    protected Color mainColor;

    public Enemy(double x, double y, int width, int height, double hp, double speed, double contactDamage, int expReward, Color mainColor) {
        super(x, y, width, height, hp, speed);
        this.contactDamage = contactDamage;
        this.expReward = expReward;
        this.mainColor = mainColor;
    }

    /**
     * Thuật toán bám đuổi người chơi: chuẩn hóa vector hướng (Unit Vector)
     */
    public void moveTowards(Player player) {
        double dx = player.getCenterX() - getCenterX();
        double dy = player.getCenterY() - getCenterY();
        double dist = Math.hypot(dx, dy);

        if (dist > 1.0) {
            x += (dx / dist) * speed;
            y += (dy / dist) * speed;
        }
    }

    @Override
    public void update() {
        if (hitFlashTimer > 0) {
            hitFlashTimer--;
        }
    }

    /**
     * Vẽ thanh máu nhỏ phía trên quái vật
     */
    protected void drawHealthBar(Graphics2D g2) {
        if (hp < maxHp) {
            int barWidth = width;
            int barHeight = 4;
            int barX = (int) x;
            int barY = (int) y - 7;

            // Nền thanh máu
            g2.setColor(new Color(50, 50, 50, 180));
            g2.fillRect(barX, barY, barWidth, barHeight);

            // Phần máu hiện tại
            int currentWidth = (int) ((hp / maxHp) * barWidth);
            g2.setColor(new Color(220, 40, 40));
            g2.fillRect(barX, barY, currentWidth, barHeight);
        }
    }

    public double getContactDamage() { return contactDamage; }
    public void applyDifficulty(Difficulty difficulty) {
        maxHp *= difficulty.enemyHealth;
        hp = maxHp;
        speed *= difficulty.enemySpeed;
        contactDamage *= difficulty.enemyDamage;
    }
    public int getExpReward() { return expReward; }
}
