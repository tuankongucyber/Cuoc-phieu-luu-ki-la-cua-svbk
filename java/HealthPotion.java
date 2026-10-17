import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Bình máu hồi phục sinh lực (Health Potion).
 * Xuất hiện với tỉ lệ hiếm khi tiêu diệt quái vật.
 */
public class HealthPotion extends DropItem {
    private double healAmount = 30;

    public HealthPotion(double x, double y) {
        super(x, y);
        this.size = 14;
    }

    @Override
    public void applyEffect(Player player) {
        player.heal(healAmount);
    }

    @Override
    public void draw(Graphics2D g2) {
        // Vẽ bình thuốc hình chữ thập đỏ viền trắng
        g2.setColor(new Color(255, 50, 50, 80));
        g2.fillOval((int) x - size, (int) y - size, size * 2, size * 2);

        // Bình hình tròn
        g2.setColor(new Color(220, 20, 60));
        g2.fillOval((int) x - 7, (int) y - 7, 14, 14);

        // Chữ thập trắng
        g2.setColor(Color.WHITE);
        g2.fillRect((int) x - 2, (int) y - 5, 4, 10);
        g2.fillRect((int) x - 5, (int) y - 2, 10, 4);
    }
}
