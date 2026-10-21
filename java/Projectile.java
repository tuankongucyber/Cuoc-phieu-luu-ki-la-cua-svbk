import java.awt.*;
import java.util.*;
/** Visual size and collision size are deliberately separate. Velocity is px / simulation tick. */
public class Projectile {
    double x, y, previousX, previousY, vx, vy, damage;
    final int radius, lifeSpan;
    int age, pierce = 1;
    boolean dead, hostile, grazed, knife;
    BulletVisual visual = BulletVisual.ORB;
    final Color color;
    WeaponId source;
    String sourceLabel = "Đạn địch";
    double turnRate, storedAngle, releaseSpeed;
    int freezeAt = -1, aimAt = -1, releaseAt = -1;
    private Set<Enemy> hitEnemies;
    public Projectile(double x, double y, double vx, double vy, double damage, int radius, Color color, int lifeSpan) {
        this(x, y, vx, vy, damage, radius, color, lifeSpan, false);
    }
    public Projectile(double x, double y, double vx, double vy, double damage, int radius, Color color, int lifeSpan, boolean hostile) {
        this.x = previousX = x; this.y = previousY = y; this.vx = vx; this.vy = vy;
        this.damage = damage; this.radius = radius; this.color = color; this.lifeSpan = lifeSpan; this.hostile = hostile;
    }
    public void update() { update(null); }
    public void update(GameWorld world) {
        previousX = x; previousY = y;
        if (++age > lifeSpan) { dead = true; return; }
        if (freezeAt > 0 && age >= freezeAt) {
            if (age == freezeAt) { storedAngle = Math.atan2(vy, vx); vx = vy = 0; }
            if (age == aimAt && world != null) {
                double dx = world.player.getCenterX() - x, dy = world.player.getCenterY() - y;
                if (Math.hypot(dx, dy) > 80) storedAngle = Math.atan2(dy, dx);
            }
            if (age >= releaseAt) {
                double speed = Math.min(releaseSpeed, (age - releaseAt + 1) * 0.07);
                vx = Math.cos(storedAngle) * speed; vy = Math.sin(storedAngle) * speed;
            }
        } else if (turnRate != 0) {
            double cosine = Math.cos(turnRate), sine = Math.sin(turnRate), oldVx = vx;
            vx = oldVx * cosine - vy * sine; vy = oldVx * sine + vy * cosine;
        }
        x += vx; y += vy;
    }
    public boolean registerHit(Enemy enemy) {
        if (hitEnemies != null && hitEnemies.contains(enemy)) return false;
        if (pierce > 1) {
            if (hitEnemies == null) hitEnemies = Collections.newSetFromMap(new IdentityHashMap<>());
            hitEnemies.add(enemy);
        }
        if (--pierce <= 0) dead = true;
        return true;
    }
    public void draw(Graphics2D g) {
        boolean frozen = freezeAt > 0 && age >= freezeAt && age < releaseAt;
        if (hostile) {
            double angle = frozen || (vx == 0 && vy == 0) ? storedAngle : Math.atan2(vy, vx);
            (knife ? BulletVisual.KNIFE : visual).draw(g, x, y, angle, radius + 2,
                frozen ? new Color(125, 225, 255) : color);
            if (frozen && (aimAt < 0 || age >= aimAt)) {
                g.setColor(new Color(160, 235, 255, 150));
                g.drawLine((int) x, (int) y, (int) (x + Math.cos(storedAngle) * 16), (int) (y + Math.sin(storedAngle) * 16));
            }
            return;
        }
        g.setColor(hostile ? new Color(20, 12, 30) : new Color(color.getRed(), color.getGreen(), color.getBlue(), 85));
        g.fillOval((int) x - radius - 2, (int) y - radius - 2, radius * 2 + 4, radius * 2 + 4);
        g.setColor(frozen ? new Color(125, 225, 255) : hostile ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), 135));
        if (knife) {
            double angle = frozen ? storedAngle : Math.atan2(vy, vx);
            Graphics2D blade = (Graphics2D) g.create();
            blade.translate(x, y); blade.rotate(angle);
            blade.fillPolygon(new int[]{10, -6, -3, -6}, new int[]{0, -3, 0, 3}, 4); blade.dispose();
        } else g.fillOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
        if (frozen && (aimAt < 0 || age >= aimAt)) {
            g.setColor(new Color(160, 235, 255, 150));
            g.drawLine((int) x, (int) y, (int) (x + Math.cos(storedAngle) * 16), (int) (y + Math.sin(storedAngle) * 16));
        }
        if (hostile) { g.setColor(Color.WHITE); g.fillOval((int) x - 1, (int) y - 1, 3, 3); }
    }
    public Rectangle getBounds() { return new Rectangle((int) x - radius, (int) y - radius, radius * 2, radius * 2); }
    public double getDamage() { return damage; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public int getRadius() { return radius; }
    public boolean isDead() { return dead; }
    public void setDead(boolean value) { dead = value; }
    public boolean isHostile() { return hostile; }
    public void setHostile(boolean value) { hostile = value; }
}
