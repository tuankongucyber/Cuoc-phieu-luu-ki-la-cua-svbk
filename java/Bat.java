import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Quái Dơi quỷ (Bat).
 * Tốc độ bay nhanh nhẹn, máu mỏng, vỗ cánh liên tục.
 */
public class Bat extends Enemy {
    private double wingAnim = 0;

    public Bat(double x, double y) {
        super(x, y, 24, 18, 15, 2.7, 5, 2, new Color(130, 50, 180));
    }

    @Override
    public void update() {
        super.update();
        wingAnim += 0.25;
    }

    @Override
    public void draw(Graphics2D g2) {
        drawHealthBar(g2);

        // Hiệu ứng chớp trắng khi bị trúng đạn
        if (hitFlashTimer > 0) {
            g2.setColor(Color.WHITE);
        } else {
            g2.setColor(mainColor);
        }

        int cx = (int) getCenterX();
        int cy = (int) getCenterY();
        int wingSpan = (int) (Math.sin(wingAnim) * 10);

        // Vẽ cánh dơi hai bên
        int[] leftWingX = {cx - 4, cx - 14, cx - 10};
        int[] leftWingY = {cy, cy - wingSpan, cy + 6};
        g2.fillPolygon(leftWingX, leftWingY, 3);

        int[] rightWingX = {cx + 4, cx + 14, cx + 10};
        int[] rightWingY = {cy, cy - wingSpan, cy + 6};
        g2.fillPolygon(rightWingX, rightWingY, 3);

        // Thân dơi hình oval
        g2.fillOval(cx - 6, cy - 6, 12, 12);

        // Mắt đỏ
        g2.setColor(Color.RED);
        g2.fillRect(cx - 3, cy - 2, 2, 2);
        g2.fillRect(cx + 1, cy - 2, 2, 2);
    }
}
