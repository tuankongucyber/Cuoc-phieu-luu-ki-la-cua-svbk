import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Nhân vật người chơi chính (Survivor).
 * Kế thừa Entity, quản lý di chuyển 8 hướng mượt mà, cấp độ, kinh nghiệm,
 * bán kính thu thập vật phẩm và danh sách vũ khí sở hữu.
 */
public class Player extends Entity {
    // Trạng thái phím bấm
    private boolean up, down, left, right;

    // Hệ thống cấp độ và kinh nghiệm
    private int level = 1;
    private int currentExp = 0;
    private int expToNextLevel = 10;
    private int killCount = 0;

    // Chỉ số bổ trợ
    private double pickupRadius = 90.0;
    private double damageMultiplier = 1.0;

    // Kho vũ khí
    private List<Weapon> weapons = new ArrayList<>();

    // Góc quay hướng mặt
    private double faceAngle = 0;

    public Player(double x, double y) {
        super(x, y, 34, 34, 100, 3.5);
    }

    /**
     * Cập nhật vị trí di chuyển với chuẩn hóa vector (không bị nhanh hơn khi đi chéo)
     */
    public void move() {
        double dx = 0;
        double dy = 0;

        if (up) dy -= 1;
        if (down) dy += 1;
        if (left) dx -= 1;
        if (right) dx += 1;

        if (dx != 0 || dy != 0) {
            double length = Math.hypot(dx, dy);
            x += (dx / length) * speed;
            y += (dy / length) * speed;
            faceAngle = Math.atan2(dy, dx);
        }

        // Giới hạn trong sàn đấu nếu truyền biên vào
    }

    public void clampToBounds(double minX, double minY, double maxX, double maxY) {
        if (x < minX) x = minX;
        if (y < minY) y = minY;
        if (x > maxX - width) x = maxX - width;
        if (y > maxY - height) y = maxY - height;
    }

    @Override
    public void update() {
        if (hitFlashTimer > 0) {
            hitFlashTimer--;
        }
    }

    /**
     * Nhận kinh nghiệm từ ngọc và kiểm tra lên cấp
     */
    public boolean gainExp(int amount) {
        currentExp += amount;
        // Chỉ báo hiệu đủ điều kiện lên cấp, KHÔNG tự tăng level ở đây.
        // GamePanel sẽ gọi triggerLevelUp() → selectUpgrade() → consumeExpAndLevelUp()
        return currentExp >= expToNextLevel;
    }

    public void heal(double amount) {
        this.hp = Math.min(this.hp + amount, maxHp);
    }

    @Override
    public void draw(Graphics2D g2) {
        int cx = (int) getCenterX();
        int cy = (int) getCenterY();

        // Vẽ vòng bán kính nhặt đồ mờ nhạt bao quanh chân
        g2.setColor(new Color(100, 200, 255, 25));
        g2.fillOval((int) (cx - pickupRadius), (int) (cy - pickupRadius), (int) (pickupRadius * 2), (int) (pickupRadius * 2));

        // Vẽ bóng đổ nhân vật
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillOval((int) x + 2, (int) y + height - 8, width - 4, 10);

        // Hiệu ứng chớp nháy khi dính sát thương
        if (hitFlashTimer > 0) {
            g2.setColor(Color.RED);
            g2.fillOval((int) x, (int) y, width, height);
            return;
        }

        // Thân áo choàng hiệp sĩ (Xanh dương đậm hoàng gia)
        g2.setColor(new Color(25, 60, 130));
        g2.fillOval((int) x, (int) y, width, height);

        // Giáp ngực hiệp sĩ
        g2.setColor(new Color(60, 120, 220));
        g2.fillOval((int) x + 5, (int) y + 5, width - 10, height - 10);

        // Mũ trùm đầu / Khăn quàng màu vàng ánh kim
        g2.setColor(new Color(255, 200, 40));
        g2.fillOval((int) x + 10, (int) y + 10, width - 20, height - 20);

        // Mắt phát sáng hướng về hướng di chuyển
        int eyeDist = 6;
        int eyeX = cx + (int) (Math.cos(faceAngle) * eyeDist);
        int eyeY = cy + (int) (Math.sin(faceAngle) * eyeDist);
        g2.setColor(Color.CYAN);
        g2.fillOval(eyeX - 3, eyeY - 3, 6, 6);

        // Vẽ thanh máu (HP Bar) phía trên nhân vật
        int barW = 40;
        int barH = 5;
        int barX = cx - barW / 2;
        int barY = (int) y - 10;

        g2.setColor(new Color(30, 30, 30, 200));
        g2.fillRect(barX, barY, barW, barH);

        int currentW = (int) ((hp / maxHp) * barW);
        g2.setColor(new Color(50, 220, 90));
        g2.fillRect(barX, barY, currentW, barH);

        g2.setColor(Color.BLACK);
        g2.drawRect(barX, barY, barW, barH);
    }

    public void addWeapon(Weapon w) {
        weapons.add(w);
    }

    public Weapon getWeapon(Class<? extends Weapon> weaponClass) {
        for (Weapon w : weapons) {
            if (weaponClass.isInstance(w)) {
                return w;
            }
        }
        return null;
    }

    public boolean hasWeapon(Class<? extends Weapon> weaponClass) {
        return getWeapon(weaponClass) != null;
    }

    // Getters and Setters
    public boolean isUp() { return up; }
    public void setUp(boolean up) { this.up = up; }
    public boolean isDown() { return down; }
    public void setDown(boolean down) { this.down = down; }
    public boolean isLeft() { return left; }
    public void setLeft(boolean left) { this.left = left; }
    public boolean isRight() { return right; }
    public void setRight(boolean right) { this.right = right; }

    public int getLevel() { return level; }
    public int getCurrentExp() { return currentExp; }
    public int getExpToNextLevel() { return expToNextLevel; }
    public int getKillCount() { return killCount; }
    public void incrementKillCount() { this.killCount++; }

    public double getPickupRadius() { return pickupRadius; }
    public void setPickupRadius(double r) { this.pickupRadius = r; }

    public double getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(double d) { this.damageMultiplier = d; }

    public List<Weapon> getWeapons() { return weapons; }
    public void consumeExpAndLevelUp() {
        this.currentExp -= this.expToNextLevel; // Trừ EXP dùng để lên cấp
        this.level++;                            // Tăng level (mốc EXP giữ nguyên)
    }
}
