import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Quái Ninja Zombie (NinjaZombie).
 * Ngoại hình kế thừa y hệt Zombie đã có, nhưng có phản xạ ninja:
 * Có khả năng lướt né đạn vuông góc 2 lần khi phát hiện đạn bay tới gần.
 */
public class NinjaZombie extends Zombie {
    private int dodgeCount = 2; // Tối đa 2 lần né đạn
    private int dodgeCooldown = 0; // Thời gian chờ giữa 2 lần né để không bị giật liên tục
    private int smokeEffectTimer = 0; // Hiệu ứng tàn ảnh / khói ninja khi vừa né xong
    private double lastDodgeX, lastDodgeY;

    public NinjaZombie(double x, double y) {
        super(x, y);
        // Tốc độ nhỉnh hơn zombie thường một chút (ninja nhanh nhẹn hơn)
        this.speed = 1.35;
        this.hp = 50;
        this.maxHp = 50;
        this.expReward = 5;
    }

    @Override
    public void update() {
        super.update();
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
        }
        if (smokeEffectTimer > 0) {
            smokeEffectTimer--;
        }
    }

    /**
     * Kiểm tra đạn xung quanh và kích hoạt né đạn nếu đạn đang bay về phía mình
     */
    public boolean checkAndDodge(List<Projectile> projectiles) {
        if (dodgeCount <= 0 || dodgeCooldown > 0 || isDead()) {
            return false;
        }

        double dangerDistance = 55.0; // Bán kính nhận diện nguy hiểm sắp trúng đòn

        for (Projectile p : projectiles) {
            if (p.isDead()) continue;

            double dx = p.getX() - getCenterX();
            double dy = p.getY() - getCenterY();
            double dist = Math.hypot(dx, dy);

            // Nếu đạn đang ở rất gần
            if (dist < dangerDistance) {
                // Kiểm tra xem viên đạn có đang bay hướng về phía NinjaZombie không
                // Tích vô hướng vector vận tốc đạn với vector từ đạn tới ninja
                double toZombieX = getCenterX() - p.getX();
                double toZombieY = getCenterY() - p.getY();
                double dot = p.getVx() * toZombieX + p.getVy() * toZombieY;

                // dot > 0 nghĩa là đạn đang tiến lại gần
                if (dot > 0) {
                    performDodge(p);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Thực hiện cú lướt né (Dash/Dodge) tức thì vuông góc với hướng bay của đạn
     */
    private void performDodge(Projectile p) {
        lastDodgeX = x;
        lastDodgeY = y;
        smokeEffectTimer = 12; // Hiển thị tàn ảnh khói trong 12 frame

        // Hướng bay của đạn
        double pSpeed = Math.hypot(p.getVx(), p.getVy());
        if (pSpeed > 0) {
            // Vector vuông góc với đạn: (-vy, vx) hoặc (vy, -vx)
            double perpX = -p.getVy() / pSpeed;
            double perpY = p.getVx() / pSpeed;

            // Chọn ngẫu nhiên lướt sang trái hoặc sang phải của đường đạn
            if (Math.random() < 0.5) {
                perpX = -perpX;
                perpY = -perpY;
            }

            // Khoảng cách lướt né (Dash distance)
            double dashDistance = 45.0;
            this.x += perpX * dashDistance;
            this.y += perpY * dashDistance;
        } else {
            // Nếu đạn không có vận tốc, lướt ngẫu nhiên ra xa
            this.x += (Math.random() - 0.5) * 60;
            this.y += (Math.random() - 0.5) * 60;
        }

        dodgeCount--;
        dodgeCooldown = 25; // 25 frame (~0.4s) hồi chiêu giữa 2 lần né
    }

    @Override
    public void draw(Graphics2D g2) {
        // Vẽ hiệu ứng tàn ảnh (Ghosting / Smoke) ninja tại vị trí vừa né
        if (smokeEffectTimer > 0) {
            Graphics2D g = (Graphics2D) g2.create();
            float alpha = (float) smokeEffectTimer / 12.0f * 0.5f;
            g.setColor(new Color(150, 150, 180, (int) (alpha * 255)));
            g.fillRoundRect((int) lastDodgeX + 3, (int) lastDodgeY + 8, width - 6, height - 8, 4, 4);
            g.fillOval((int) lastDodgeX + 4, (int) lastDodgeY, width - 8, 14);
            g.dispose();
        }

        // Ngoại hình y hệt Zombie
        super.draw(g2);

        // Hiển thị số lần né còn lại bằng ký hiệu nhỏ mờ phía trên (hoặc khăn trùm kín đáo)
        // Nếu còn né, hiển thị ánh mắt lóe sáng tím nhẹ báo hiệu Ninja
        if (dodgeCount > 0 && hitFlashTimer == 0) {
            g2.setColor(new Color(180, 50, 255, 180));
            g2.fillRect((int) x + 7, (int) y + 4, 3, 3);
            g2.fillRect((int) x + 15, (int) y + 4, 3, 3);
        }
    }

    public int getDodgeCount() { return dodgeCount; }
}

