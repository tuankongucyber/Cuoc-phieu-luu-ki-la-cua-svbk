import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Lớp đại diện cho các viên đạn, quả cầu phép thuật bay trên màn hình.
 */
public class Projectile {
    private double x;
    private double y;
    private double vx;
    private double vy;
    private double damage;
    private int radius;
    private Color color;
    private int lifeSpan;
    private int age = 0;
    private boolean dead = false;
    private boolean hostile = false; // true nếu là đạn của Boss/quái nhắm vào người chơi

    public Projectile(double x, double y, double vx, double vy, double damage, int radius, Color color, int lifeSpan) {
        this(x, y, vx, vy, damage, radius, color, lifeSpan, false);
    }

    public Projectile(double x, double y, double vx, double vy, double damage, int radius, Color color, int lifeSpan, boolean hostile) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.radius = radius;
        this.color = color;
        this.lifeSpan = lifeSpan;
        this.hostile = hostile;
    }

    public void update() {
        x += vx;
        y += vy;
        age++;
        if (age >= lifeSpan) {
            dead = true;
        }
    }

    public void draw(Graphics2D g2) {
        // Vẽ vệt sáng phát quang (glow effect)
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
        g2.fillOval((int) (x - radius * 1.5), (int) (y - radius * 1.5), radius * 3, radius * 3);

        // Vẽ lõi viên đạn
        g2.setColor(color);
        g2.fillOval((int) (x - radius), (int) (y - radius), radius * 2, radius * 2);

        // Vẽ tâm đạn màu trắng sáng rực
        g2.setColor(Color.WHITE);
        int inner = Math.max(2, radius / 2);
        g2.fillOval((int) (x - inner), (int) (y - inner), inner * 2, inner * 2);
    }

    public Rectangle getBounds() {
        return new Rectangle((int) (x - radius), (int) (y - radius), radius * 2, radius * 2);
    }

    public double getDamage() { return damage; }
    public boolean isDead() { return dead; }
    public void setDead(boolean dead) { this.dead = dead; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public int getRadius() { return radius; }
    public boolean isHostile() { return hostile; }
    public void setHostile(boolean hostile) { this.hostile = hostile; }
}
