import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Hiệu ứng số sát thương nổi (Floating Combat Text).
 * Chữ số nảy lên khi tấn công và mờ dần trước khi biến mất.
 */
public class DamageText {
    private double x;
    private double y;
    private double vy = -1.5; // Tốc độ trôi lên trên
    private String text;
    private Color color;
    private int lifeTime = 35; // Số frame tồn tại
    private int currentLife = 0;
    private boolean isCritical = false;

    public DamageText(double x, double y, String text, Color color, boolean isCritical) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.isCritical = isCritical;
    }

    public void update() {
        y += vy;
        currentLife++;
    }

    public boolean isDead() {
        return currentLife >= lifeTime;
    }

    public void draw(Graphics2D g2) {
        float alpha = 1.0f - ((float) currentLife / lifeTime);
        if (alpha < 0f) alpha = 0f;

        // Vẽ bóng đổ chữ màu đen giúp dễ đọc trên nền bất kỳ
        g2.setFont(new Font("Arial", Font.BOLD, isCritical ? 18 : 14));
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 180)));
        g2.drawString(text, (int) x + 1, (int) y + 1);

        // Vẽ chữ chính
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)));
        g2.drawString(text, (int) x, (int) y);
    }
}
