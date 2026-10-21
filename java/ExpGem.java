import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Hạt ngọc kinh nghiệm (Exp Gem).
 * Rơi ra khi quái vật bị tiêu diệt.
 */
public class ExpGem extends DropItem {
    private int expValue;
    private Color gemColor;

    public ExpGem(double x, double y, int expValue) {
        super(x, y);
        this.expValue = expValue;
        if (expValue >= 15) {
            gemColor = new Color(255, 60, 60); // Đỏ (EXP cao từ Boss)
            this.size = 14;
        } else if (expValue >= 5) {
            gemColor = new Color(50, 255, 120); // Xanh lá
            this.size = 12;
        } else {
            gemColor = new Color(80, 180, 255); // Xanh dương
            this.size = 10;
        }
    }

    @Override
    public void applyEffect(Player player) {
        player.gainExp(expValue);
    }

    @Override
    public void draw(Graphics2D g2) {
        int half = size / 2;
        // Vẽ hình thoi (Diamond gem)
        int[] xPoints = {(int) x, (int) x + half, (int) x, (int) x - half};
        int[] yPoints = {(int) y - half, (int) y, (int) y + half, (int) y};

        // Hào quang
        g2.setColor(new Color(gemColor.getRed(), gemColor.getGreen(), gemColor.getBlue(), 90));
        g2.fillOval((int) x - size, (int) y - size, size * 2, size * 2);

        // Viên ngọc
        g2.setColor(gemColor);
        g2.fillPolygon(xPoints, yPoints, 4);

        // Điểm sáng
        g2.setColor(Color.WHITE);
        g2.fillRect((int) x - 1, (int) y - 1, 2, 2);
    }

    public int getExpValue() { return expValue; }
    public void merge(int amount) { expValue += amount; }
}
