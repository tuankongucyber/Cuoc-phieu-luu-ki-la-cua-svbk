import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Quái Xác sống (Zombie).
 * Đi chậm chạp, lượng máu cao, sức chịu đòn tốt.
 */
public class Zombie extends Enemy {
    public Zombie(double x, double y) {
        super(x, y, 26, 32, 45, 1.1, 10, 3, new Color(50, 140, 60));
    }

    @Override
    public void draw(Graphics2D g2) {
        drawHealthBar(g2);

        if (hitFlashTimer > 0) {
            g2.setColor(Color.WHITE);
            g2.fillRect((int) x, (int) y, width, height);
            return;
        }

        // Thân zombie xanh rêu
        g2.setColor(mainColor);
        g2.fillRoundRect((int) x + 3, (int) y + 8, width - 6, height - 8, 4, 4);

        // Đầu zombie
        g2.setColor(new Color(60, 160, 70));
        g2.fillOval((int) x + 4, (int) y, width - 8, 14);

        // Mắt zombie (một bên đục ngầu, một bên đỏ)
        g2.setColor(Color.WHITE);
        g2.fillRect((int) x + 7, (int) y + 4, 3, 3);
        g2.setColor(Color.RED);
        g2.fillRect((int) x + 15, (int) y + 4, 3, 3);

        // Vết máu/rách trên áo
        g2.setColor(new Color(120, 20, 20));
        g2.fillRect((int) x + 8, (int) y + 15, 6, 3);
    }
}
