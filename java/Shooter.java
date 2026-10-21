import java.awt.*;
public final class Shooter extends Enemy {
    int fireTimer = 100;
    double aim;
    private int warningLength = 30;
    public Shooter(double x, double y) { super(x, y, 28, 28, 38, 1.0, 8, 4, new Color(235, 115, 175)); }
    @Override public void applyDifficulty(Difficulty difficulty) {
        super.applyDifficulty(difficulty);
        warningLength = difficulty.bossWarning;
    }
    @Override public void moveTowards(Player player) {
        double dx = player.getCenterX() - getCenterX(), dy = player.getCenterY() - getCenterY(), distance = Math.hypot(dx, dy);
        aim = Math.atan2(dy, dx);
        if (distance > 320) super.moveTowards(player);
        else if (distance < 220 && distance > 0) { x -= dx / distance * speed; y -= dy / distance * speed; }
    }
    public void attack(GameWorld world) {
        if (--fireTimer <= 0) {
            if (Math.abs(getCenterX() - world.player.getCenterX()) < 460
                && Math.abs(getCenterY() - world.player.getCenterY()) < 330)
                BulletPattern.AIMED.emit(world, getCenterX(), getCenterY(), aim, 0, "Quái bắn xa");
            fireTimer = 160 + world.random.nextInt(40);
        }
    }
    @Override public void draw(Graphics2D g) {
        int cx = (int) getCenterX(), cy = (int) getCenterY();
        if (fireTimer <= warningLength) {
            g.setColor(new Color(255, 160, 180, 180)); g.drawOval(cx - 24, cy - 24, 48, 48);
            g.drawLine(cx, cy, cx + (int) (Math.cos(aim) * 55), cy + (int) (Math.sin(aim) * 55));
        }
        g.setColor(hitFlashTimer > 0 ? Color.WHITE : mainColor);
        g.fillPolygon(new int[] {cx, cx + 14, cx, cx - 14}, new int[] {cy - 14, cy, cy + 14, cy}, 4);
        drawHealthBar(g);
    }
}
