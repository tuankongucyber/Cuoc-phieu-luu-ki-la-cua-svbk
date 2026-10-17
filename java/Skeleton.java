import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Quái Khung xương (Skeleton).
 * Tốc độ di chuyển trung bình, cơ thể xương trắng xám.
 */
public class Skeleton extends Enemy {
    public Skeleton(double x, double y) {
        super(x, y, 22, 28, 25, 1.7, 7, 3, new Color(220, 220, 225));
    }

    @Override
    public void draw(Graphics2D g2) {
        drawHealthBar(g2);

        if (hitFlashTimer > 0) {
            g2.setColor(Color.WHITE);
            g2.fillRect((int) x, (int) y, width, height);
            return;
        }

        // Hộp sọ màu trắng xám
        g2.setColor(mainColor);
        g2.fillRoundRect((int) x + 3, (int) y, width - 6, 12, 3, 3);

        // Hốc mắt đen sâu hoắm
        g2.setColor(Color.BLACK);
        g2.fillRect((int) x + 6, (int) y + 3, 3, 3);
        g2.fillRect((int) x + 13, (int) y + 3, 3, 3);

        // Khung xương sườn
        g2.setColor(mainColor);
        g2.fillRect((int) x + 5, (int) y + 14, width - 10, 2);
        g2.fillRect((int) x + 6, (int) y + 18, width - 12, 2);
        g2.fillRect((int) x + 7, (int) y + 22, width - 14, 2);
        g2.fillRect((int) x + 10, (int) y + 12, 2, 14); // Cột sống
    }
}
