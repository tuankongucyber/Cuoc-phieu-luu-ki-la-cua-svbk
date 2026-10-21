import java.awt.*;
/** Moving, layered, HP-only boss encounters. See docs/BOSS_REFERENCES.md. */
public class Boss extends Enemy {
    public final int encounter;
    int phase, phaseAge, dashTicks, dashWait, dashRemaining, recoveryTicks;
    double aim, dashAim, dashDistance, dashDx, dashDy;
    private Difficulty difficulty = Difficulty.NORMAL;
    public Boss(double x, double y) { this(x, y, 0); }
    public Boss(double x, double y, int encounter) {
        super(x, y, 54, 54, 4800, 1.25, 18, 50, new Color(200, 65, 110)); this.encounter = encounter;
    }
    @Override public void applyDifficulty(Difficulty d) { difficulty = d; super.applyDifficulty(d); }
    public void beginPhase(int phase) {
        this.phase = phase; phaseAge = 0; dead = false;
        maxHp = ((encounter == 0 ? 4800 : 8000) + phase * (encounter == 0 ? 1500 : 2200)) * difficulty.enemyHealth;
        hp = maxHp; dashTicks = 0; dashWait = difficulty.bossDashCooldown; dashRemaining = 0; recoveryTicks = 0;
        dashAim = aim; dashDistance = 260;
    }
    public String title() { return encounter == 0 ? "NGƯỜI GÁC CỔNG" : "KẺ PHÁ VỠ RANH GIỚI"; }
    public String spellName() {
        String[] middle = {"Băng giao cắt", "Vòng quỹ đạo giao nhau", "Đóng băng • Tái khởi động"};
        String[] last = {"Scarlet • Chuỗi hồng đạn", "Đồng hồ • Dao ngưng đọng", "Phong tỏa chuyển động"};
        return (encounter == 0 ? middle : last)[phase];
    }
    @Override public void moveTowards(Player player) {}
    public void fight(GameWorld world) {
        phaseAge++;
        aim = Math.atan2(world.player.getCenterY() - getCenterY(), world.player.getCenterX() - getCenterX());
        if (phaseAge <= 60) return;
        boolean dashing = encounter == 1 && phase == 2;
        if (dashing) updateDash(world);
        boolean chargingDash = dashing && dashTicks == 0 && dashWait <= difficulty.bossWarning;
        if (dashTicks == 0 && !chargingDash) {
            double targetX = 500 + Math.sin(phaseAge * .012 + phase * .7) * 230;
            double targetY = 185 + Math.cos(phaseAge * .009) * 40;
            double dx = targetX - getCenterX(), dy = targetY - getCenterY(), length = Math.hypot(dx, dy);
            double step = Math.min(length, recoveryTicks > 0 ? 7 : speed);
            if (length > 1) { x += dx / length * step; y += dy / length * step; }
        }
        // Existing layers remain dangerous during a dash, but no new point-blank barrage is added.
        if (dashTicks == 0 && recoveryTicks == 0) BossPatterns.update(world, this, phaseAge - 60);
    }
    private void updateDash(GameWorld world) {
        if (recoveryTicks > 0) { recoveryTicks--; return; }
        if (dashTicks > 0) {
            x += dashDx; y += dashDy; clamp();
            if (--dashTicks == 0) {
                dashWait = dashRemaining > 0 ? difficulty.bossWarning + 12 : difficulty.bossDashCooldown;
                if (dashRemaining == 0) recoveryTicks = 60;
            }
            return;
        }
        dashWait--;
        if (dashWait == difficulty.bossWarning) {
            dashAim = aim;
            dashDistance = Math.min(300, Math.hypot(world.player.getCenterX() - getCenterX(), world.player.getCenterY() - getCenterY()));
        }
        if (dashWait <= 0) {
            if (dashRemaining == 0) dashRemaining = difficulty.bossDashCount;
            dashRemaining--;
            dashDx = Math.cos(dashAim) * dashDistance / 14; dashDy = Math.sin(dashAim) * dashDistance / 14;
            dashTicks = 14;
        }
    }
    private void clamp() { x = Math.max(25, Math.min(921, x)); y = Math.max(110, Math.min(671, y)); }
    public boolean isWarning() {
        return phaseAge <= 60 || (phaseAge - 60) % BossPatterns.cycle(this) < difficulty.bossWarning
            || (encounter == 1 && phase == 2 && dashWait <= difficulty.bossWarning && dashTicks == 0);
    }
    @Override public void draw(Graphics2D g) {
        int cx = (int) getCenterX(), cy = (int) getCenterY();
        if (isWarning()) {
            g.setColor(new Color(255, 200, 90, 200)); g.drawOval(cx - 42, cy - 42, 84, 84);
            if (encounter == 1 && phase == 2 && dashWait <= difficulty.bossWarning)
                g.drawLine(cx, cy, cx + (int) (Math.cos(dashAim) * dashDistance), cy + (int) (Math.sin(dashAim) * dashDistance));
        }
        int untilWall = BossPatterns.wallWarningUntil(Math.max(0, phaseAge - 60), difficulty.bossWarning);
        if (encounter == 1 && phase == 2 && phaseAge > 60 && untilWall >= 0 && recoveryTicks == 0 && dashTicks == 0) {
            int gap = (int) BossPatterns.wallGap(phaseAge - 60 + untilWall);
            int halfWidth = difficulty == Difficulty.EASY ? 70 : difficulty == Difficulty.HARD ? 50 : 60;
            g.setColor(new Color(105, 200, 245, 150));
            g.drawLine(40, 115, gap - halfWidth, 115); g.drawLine(gap + halfWidth, 115, 960, 115);
        }
        g.setColor(hitFlashTimer > 0 ? Color.WHITE : mainColor);
        g.fillRoundRect((int) x, (int) y, width, height, 18, 18);
        g.setColor(Color.WHITE); g.drawOval(cx - 12, cy - 12, 24, 24);
    }
}
