import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Lớp cha trừu tượng đại diện cho mọi thực thể sống trong game (Player, Quái vật).
 * Tuân thủ tính Đóng gói (Encapsulation) và Trừu tượng (Abstraction).
 */
public abstract class Entity {
    protected double x;
    protected double y;
    protected int width;
    protected int height;
    protected double speed;
    protected double hp;
    protected double maxHp;
    protected boolean dead = false;
    protected int hitFlashTimer = 0; // Hiệu ứng nhấp nháy trắng đỏ khi trúng đòn

    public Entity(double x, double y, int width, int height, double hp, double speed) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.maxHp = hp;
        this.hp = hp;
        this.speed = speed;
    }

    public abstract void update();
    public abstract void draw(Graphics2D g2);

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }

    public double getCenterX() {
        return x + width / 2.0;
    }

    public double getCenterY() {
        return y + height / 2.0;
    }

    public void takeDamage(double damage) {
        this.hp -= damage;
        this.hitFlashTimer = 6; // Chớp sáng trong 6 frame
        if (this.hp <= 0) {
            this.hp = 0;
            this.dead = true;
        }
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public double getHp() { return hp; }
    public void setHp(double hp) { 
        this.hp = Math.min(hp, maxHp); 
    }

    public double getMaxHp() { return maxHp; }
    public void setMaxHp(double maxHp) { this.maxHp = maxHp; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public boolean isDead() { return dead; }
    public void setDead(boolean dead) { this.dead = dead; }
}
