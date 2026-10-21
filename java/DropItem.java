import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Lớp cha trừu tượng cho các vật phẩm rơi ra sàn (Drop Items) như Ngọc EXP và Bình Máu.
 */
public abstract class DropItem {
    protected double x;
    protected double y;
    protected int size = 12;
    protected boolean collected = false;
    protected double magnetSpeed = 0.5;

    public DropItem(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void update(Player player) {
        if (collected) return;
        double dx = player.getCenterX() - x;
        double dy = player.getCenterY() - y;
        double dist = Math.hypot(dx, dy);
        if (dist < 18) {
            applyEffect(player);
            collected = true;
            return;
        }

        // Nếu người chơi ở trong phạm vi nhặt (Magnet Radius)
        if (dist < player.getPickupRadius()) {
            magnetSpeed += 0.4; // Tăng dần tốc độ hút
            double step = Math.min(dist, magnetSpeed);
            x += (dx / dist) * step;
            y += (dy / dist) * step;

            // Khi chạm vào người chơi
            if (dist - step <= 18) {
                applyEffect(player);
                collected = true;
            }
        }
    }

    public abstract void applyEffect(Player player);
    public abstract void draw(Graphics2D g2);

    public boolean isCollected() { return collected; }
    public double getX() { return x; }
    public double getY() { return y; }
}
