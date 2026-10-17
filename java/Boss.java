import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * Trùm Chúa quỷ (Vampire Boss).
 * Xuất hiện ở các đợt cao trào hoặc trong phòng Boss.
 * Trong phòng Boss:
 * - Máu 5000
 * - Tốc độ chạy thông thường giữ nguyên (1.25)
 * - Sao chép toàn bộ vũ khí và hệ số sức mạnh của người chơi
 * - Có kỹ năng: Lướt dài (Dash) có báo trước, delay 0.5s, độ dài lướt tỉ lệ thuận với tốc độ người chơi.
 */
public class Boss extends Enemy {
    private double auraPulse = 0;
    private double damageMultiplier = 1.0;
    private List<Weapon> weapons = new ArrayList<>();
    private boolean isMirrorBoss = false;

    // Hỗ trợ vũ khí xoay quanh Boss
    private double orbitAngle = 0;
    private int orbitHitCooldown = 0;

    // Hỗ trợ hào quang nổ của Boss
    private int bossAuraAnimTimer = 0;

    // ================= KỸ NĂNG LƯỚT DÀI (DASH) =================
    // Trạng thái kỹ năng
    private enum DashState {
        IDLE,           // Đang di chuyển thường, chờ hồi chiêu
        WARNING,        // Đang ngắm và vẽ đường cảnh báo (delay 0.5s = 30 frame)
        DASHING,        // Đang lao vút đi với tốc độ cao
        COOLDOWN        // Tạm nghỉ ngắn sau cú lướt
    }

    private DashState dashState = DashState.IDLE;
    private int dashTimer = 180;      // Đếm lặp chiêu lướt (mỗi 4 - 5s tung 1 lần)
    private int warningTimer = 0;     // Đếm 30 frame (0.5 giây) khi đang báo trước
    private int dashingTimer = 0;     // Đếm thời gian lướt thực tế
    private double dashDirX = 0;      // Hướng lướt X
    private double dashDirY = 0;      // Hướng lướt Y
    private double dashSpeed = 0;     // Tốc độ lướt
    private double dashDistance = 0;  // Độ dài cú lướt
    private double playerSpeedRef = 3.5; // Lưu tốc độ người chơi để tính độ dài

    public Boss(double x, double y) {
        super(x, y, 54, 54, 5000, 1.25, 20, 35, new Color(160, 20, 30));
    }

    /**
     * Sao chép chỉ số và vũ khí từ người chơi khi ở phòng Boss:
     * - Tốc độ chạy thông thường của Boss GIỮ NGUYÊN (1.25).
     * - Máu: 5000
     * - Sao chép hệ số sát thương và toàn bộ kho vũ khí.
     * - Lưu tốc độ người chơi để tính độ dài cú lướt tỉ lệ thuận.
     */
    public void copyFromPlayer(Player player) {
        this.isMirrorBoss = true;
        // Tốc độ của Boss giữ nguyên 1.25
        this.speed = 1.25;
        this.maxHp = 5000;
        this.hp = this.maxHp;
        this.damageMultiplier = player.getDamageMultiplier();
        this.contactDamage = Math.max(15, 20 * this.damageMultiplier);
        this.playerSpeedRef = player.getSpeed();

        this.weapons.clear();
        for (Weapon w : player.getWeapons()) {
            this.weapons.add(w.copy());
        }
    }

    @Override
    public void moveTowards(Player player) {
        // Cập nhật tốc độ người chơi mới nhất (nếu người chơi nâng cấp tốc độ)
        if (isMirrorBoss) {
            this.playerSpeedRef = player.getSpeed();
        }

        switch (dashState) {
            case IDLE:
                // Di chuyển thường bám theo người chơi
                super.moveTowards(player);
                dashTimer--;
                if (dashTimer <= 0) {
                    // Bắt đầu chuẩn bị lướt (báo trước 0.5s = 30 frame)
                    dashState = DashState.WARNING;
                    warningTimer = 30; // 0.5s ở 60 FPS

                    // Khóa hướng ngắm vào vị trí người chơi lúc bắt đầu báo trước
                    double dx = player.getCenterX() - getCenterX();
                    double dy = player.getCenterY() - getCenterY();
                    double dist = Math.hypot(dx, dy);
                    if (dist > 0.001) {
                        dashDirX = dx / dist;
                        dashDirY = dy / dist;
                    } else {
                        dashDirX = 1;
                        dashDirY = 0;
                    }

                    // Độ dài cú lướt tỉ lệ thuận với tốc độ người chơi
                    // Ví dụ: người chơi tốc độ 3.5 -> quãng đường ~350px; tốc độ 5.0 -> quãng đường ~500px
                    dashDistance = Math.max(260.0, playerSpeedRef * 100.0);
                    // Lướt nhanh trong 14 frame (~0.23 giây)
                    int dashDuration = 14;
                    dashSpeed = dashDistance / dashDuration;
                }
                break;

            case WARNING:
                // Đứng yên tập trung tụ lực trong 0.5s báo trước
                warningTimer--;
                if (warningTimer <= 0) {
                    dashState = DashState.DASHING;
                    dashingTimer = 14;
                }
                break;

            case DASHING:
                // Lao vút về phía trước theo hướng đã định trước
                x += dashDirX * dashSpeed;
                y += dashDirY * dashSpeed;

                // Giới hạn trong sàn đấu phòng Boss nếu đang ở phòng Boss
                if (isMirrorBoss) {
                    clampToBounds(25, 25, 1000 - 25, 750 - 25);
                }

                dashingTimer--;
                if (dashingTimer <= 0) {
                    dashState = DashState.COOLDOWN;
                    dashTimer = 25; // Nghỉ 25 frame sau cú lướt
                }
                break;

            case COOLDOWN:
                // Đứng khựng lại một nhịp nhỏ hồi phục tư thế
                dashTimer--;
                if (dashTimer <= 0) {
                    dashState = DashState.IDLE;
                    dashTimer = 220; // 3.6 giây sau tung cú lướt tiếp theo
                }
                break;
        }
    }

    public void clampToBounds(double minX, double minY, double maxX, double maxY) {
        if (x < minX) x = minX;
        if (y < minY) y = minY;
        if (x > maxX - width) x = maxX - width;
        if (y > maxY - height) y = maxY - height;
    }

    @Override
    public void update() {
        super.update();
        auraPulse += 0.08;
    }

    /**
     * Cập nhật vũ khí của Boss nhắm vào Player
     */
    public void updateWeapons(Player player, List<Projectile> projectiles, List<DamageText> damageTexts, Sound sound) {
        if (!isMirrorBoss || isDead() || player.isDead()) return;

        if (bossAuraAnimTimer > 0) {
            bossAuraAnimTimer--;
        }

        if (orbitHitCooldown > 0) {
            orbitHitCooldown--;
        }

        for (Weapon weapon : weapons) {
            if (weapon instanceof MagicWand) {
                updateBossMagicWand((MagicWand) weapon, player, projectiles, sound);
            } else if (weapon instanceof OrbitBlade) {
                updateBossOrbitBlade((OrbitBlade) weapon, player, damageTexts, sound);
            } else if (weapon instanceof HolyAura) {
                updateBossHolyAura((HolyAura) weapon, player, damageTexts, sound);
            }
        }
    }

    private void updateBossMagicWand(MagicWand wand, Player player, List<Projectile> projectiles, Sound sound) {
        wand.timer++;
        if (wand.timer >= wand.cooldown) {
            wand.timer = 0;
            double dx = player.getCenterX() - getCenterX();
            double dy = player.getCenterY() - getCenterY();
            double dist = Math.hypot(dx, dy);

            if (dist > 0) {
                double baseAngle = Math.atan2(dy, dx);
                int count = wand.getLevel(); // Số tia đạn tăng theo cấp
                double spread = 0.2;
                double speed = 7.0;

                for (int i = 0; i < count; i++) {
                    double angle = baseAngle + (i - (count - 1) / 2.0) * spread;
                    double vx = Math.cos(angle) * speed;
                    double vy = Math.sin(angle) * speed;
                    double dmg = wand.getCurrentDamage() * damageMultiplier;

                    // Đạn thù địch (hostile = true) có màu đỏ rực của Boss
                    projectiles.add(new Projectile(
                        getCenterX(), getCenterY(),
                        vx, vy, dmg, 7, new Color(255, 40, 80), 100, true
                    ));
                }
                sound.playShoot();
            }
        }
    }

    private void updateBossOrbitBlade(OrbitBlade blade, Player player, List<DamageText> damageTexts, Sound sound) {
        orbitAngle += 0.07;
        if (orbitAngle >= Math.PI * 2) {
            orbitAngle -= Math.PI * 2;
        }

        int bladeCount = blade.getLevel();
        double radius = 75;
        double bladeHitRadius = 22;

        for (int i = 0; i < bladeCount; i++) {
            double angle = orbitAngle + i * (Math.PI * 2 / bladeCount);
            double bx = getCenterX() + Math.cos(angle) * radius;
            double by = getCenterY() + Math.sin(angle) * radius;

            double dist = Math.hypot(player.getCenterX() - bx, player.getCenterY() - by);
            if (dist < bladeHitRadius + player.getWidth() / 2.0) {
                if (orbitHitCooldown <= 0 && player.hitFlashTimer == 0) {
                    double dmg = blade.getCurrentDamage() * damageMultiplier;
                    player.takeDamage(dmg);
                    damageTexts.add(new DamageText(player.getX(), player.getY() - 10, "-" + (int) dmg, new Color(255, 120, 40), true));
                    sound.playHit();
                    orbitHitCooldown = 15;
                }
            }
        }
    }

    private void updateBossHolyAura(HolyAura aura, Player player, List<DamageText> damageTexts, Sound sound) {
        aura.timer++;
        if (aura.timer >= aura.cooldown) {
            aura.timer = 0;
            bossAuraAnimTimer = 15;
            sound.playHit();

            double dist = Math.hypot(player.getCenterX() - getCenterX(), player.getCenterY() - getCenterY());
            double radius = aura.getAuraRadius();

            if (dist <= radius && player.hitFlashTimer == 0) {
                double dmg = aura.getCurrentDamage() * damageMultiplier;
                player.takeDamage(dmg);
                damageTexts.add(new DamageText(player.getX(), player.getY() - 10, "-" + (int) dmg, new Color(255, 60, 60), true));

                // Đẩy lùi player khỏi Boss
                if (dist > 0) {
                    double pushX = (player.getCenterX() - getCenterX()) / dist * 25;
                    double pushY = (player.getCenterY() - getCenterY()) / dist * 25;
                    player.setX(player.getX() + pushX);
                    player.setY(player.getY() + pushY);
                }
            }
        }
    }

    @Override
    public void draw(Graphics2D g2) {
        // 1. Vẽ vệt cảnh báo (Warning line & indicator) khi đang tụ lực lướt (delay 0.5s)
        if (dashState == DashState.WARNING) {
            drawDashWarning(g2);
        }

        // 2. Vệt bóng mờ tốc độ cao khi đang lướt (Dash afterimage)
        if (dashState == DashState.DASHING) {
            g2.setColor(new Color(255, 50, 80, 110));
            g2.fillRoundRect((int) (x - dashDirX * 22), (int) (y - dashDirY * 22), width, height, 10, 10);
            g2.setColor(new Color(255, 50, 80, 60));
            g2.fillRoundRect((int) (x - dashDirX * 42), (int) (y - dashDirY * 42), width, height, 10, 10);
        }

        // Hào quang quỷ dữ đỏ rực xung quanh Boss
        int pulseSize = (int) (Math.sin(auraPulse) * 8);
        g2.setColor(dashState == DashState.WARNING ? new Color(255, 230, 50, 120) : new Color(255, 30, 30, 60));
        g2.fillOval((int) x - 12 - pulseSize / 2, (int) y - 12 - pulseSize / 2, width + 24 + pulseSize, height + 24 + pulseSize);

        // Vẽ hiệu ứng vũ khí sao chép của Boss nếu có
        drawBossWeapons(g2);

        if (hitFlashTimer > 0) {
            g2.setColor(Color.WHITE);
            g2.fillRect((int) x, (int) y, width, height);
        } else {
            // Thân giáp đỏ sẫm (hoặc sáng vàng cam cảnh báo khi tụ lực)
            if (dashState == DashState.WARNING) {
                g2.setColor(new Color(230, 40, 30));
            } else {
                g2.setColor(mainColor);
            }
            g2.fillRoundRect((int) x, (int) y, width, height, 10, 10);

            // Cặp sừng quỷ màu đen nhọn hoắt
            g2.setColor(new Color(30, 10, 10));
            int[] leftHornX = {(int) x + 6, (int) x - 4, (int) x + 14};
            int[] leftHornY = {(int) y + 6, (int) y - 16, (int) y + 2};
            g2.fillPolygon(leftHornX, leftHornY, 3);

            int[] rightHornX = {(int) x + width - 6, (int) x + width + 4, (int) x + width - 14};
            int[] rightHornY = {(int) y + 6, (int) y - 16, (int) y + 2};
            g2.fillPolygon(rightHornX, rightHornY, 3);

            // Mắt quỷ phát sáng
            g2.setColor(dashState == DashState.WARNING ? Color.WHITE : (isMirrorBoss ? new Color(255, 220, 50) : Color.YELLOW));
            g2.fillRect((int) x + 14, (int) y + 16, 7, 5);
            g2.fillRect((int) x + width - 21, (int) y + 16, 7, 5);

            // Miệng nhe răng nanh
            g2.setColor(Color.WHITE);
            g2.fillRect((int) x + 18, (int) y + 34, 18, 4);
        }

        // Vẽ thanh máu lớn hoành tráng trên đầu Boss
        int barW = width + 24;
        int barH = 7;
        int barX = (int) x - 12;
        int barY = (int) y - 16;

        g2.setColor(new Color(20, 20, 20, 220));
        g2.fillRect(barX, barY, barW, barH);

        int curW = (int) ((hp / maxHp) * barW);
        g2.setColor(isMirrorBoss ? new Color(255, 30, 80) : new Color(255, 40, 40));
        g2.fillRect(barX, barY, curW, barH);

        g2.setColor(Color.ORANGE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRect(barX, barY, barW, barH);
        g2.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Vẽ dải cảnh báo màu đỏ báo trước hướng và khoảng cách lướt
     */
    private void drawDashWarning(Graphics2D g2) {
        double startX = getCenterX();
        double startY = getCenterY();
        double endX = startX + dashDirX * dashDistance;
        double endY = startY + dashDirY * dashDistance;

        // Vệt đỏ mờ bề rộng toàn thân
        g2.setColor(new Color(255, 40, 40, 85));
        g2.setStroke(new BasicStroke(28.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int) startX, (int) startY, (int) endX, (int) endY);

        // Tia laser cảnh báo đậm ở giữa
        g2.setColor(new Color(255, 230, 80, 200));
        g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2.drawLine((int) startX, (int) startY, (int) endX, (int) endY);

        // Vòng tròn đích đến cú lướt
        g2.setColor(new Color(255, 50, 50, 180));
        g2.drawOval((int) endX - 16, (int) endY - 16, 32, 32);

        // Khôi phục nét vẽ mặc định
        g2.setStroke(new BasicStroke(1.0f));
    }

    private void drawBossWeapons(Graphics2D g2) {
        if (!isMirrorBoss) return;

        for (Weapon w : weapons) {
            if (w instanceof OrbitBlade) {
                int bladeCount = w.getLevel();
                double radius = 75;
                for (int i = 0; i < bladeCount; i++) {
                    double angle = orbitAngle + i * (Math.PI * 2 / bladeCount);
                    double bx = getCenterX() + Math.cos(angle) * radius;
                    double by = getCenterY() + Math.sin(angle) * radius;

                    AffineTransform old = g2.getTransform();
                    g2.translate(bx, by);
                    g2.rotate(angle + Math.PI / 2);

                    // Vệt sáng đỏ tím ma mị của Boss
                    g2.setColor(new Color(255, 30, 70, 130));
                    g2.fillOval(-11, -17, 22, 34);

                    // Thân đao quỷ
                    g2.setColor(new Color(255, 70, 70));
                    int[] xPoints = {0, 6, 0, -6};
                    int[] yPoints = {-16, 0, 14, 0};
                    g2.fillPolygon(xPoints, yPoints, 4);

                    // Cán đao đen
                    g2.setColor(new Color(30, 10, 10));
                    g2.fillRect(-2, 10, 4, 8);

                    g2.setTransform(old);
                }
            } else if (w instanceof HolyAura) {
                double radius = ((HolyAura) w).getAuraRadius();
                // Hào quang hắc ám của Boss
                g2.setColor(new Color(255, 40, 40, 25));
                g2.fillOval((int) (getCenterX() - radius), (int) (getCenterY() - radius), (int) radius * 2, (int) radius * 2);

                if (bossAuraAnimTimer > 0) {
                    float progress = 1.0f - ((float) bossAuraAnimTimer / 15);
                    int currentR = (int) (radius * progress);
                    int alpha = (int) (200 * (1.0f - progress));

                    g2.setColor(new Color(255, 50, 80, alpha));
                    g2.setStroke(new BasicStroke(3.0f));
                    g2.drawOval((int) (getCenterX() - currentR), (int) (getCenterY() - currentR), currentR * 2, currentR * 2);
                    g2.setStroke(new BasicStroke(1.0f));
                }
            }
        }
    }

    public boolean isMirrorBoss() { return isMirrorBoss; }
    public List<Weapon> getWeapons() { return weapons; }
}
