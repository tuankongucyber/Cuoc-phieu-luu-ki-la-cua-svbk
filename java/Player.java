import java.awt.*;
import java.util.*;

public class Player extends Entity {
    public static final double BULLET_RADIUS = 4, BODY_RADIUS = 12, GRAZE_RADIUS = 18;
    public static final int HIT_INVULNERABILITY = 45, DASH_TICKS = 9, DASH_COOLDOWN = 120;
    private final java.util.List<Weapon> weapons = new ArrayList<>();
    private final EnumMap<PassiveId, Integer> passives = new EnumMap<>(PassiveId.class);
    private int level = 1, exp, kills;
    int invulnerability, dashRemaining, dashCooldown, bombs = 2, bombsUsed, hitsTaken;
    boolean focused, dashedThisTick;
    double previousCenterX, previousCenterY, lastDx = 0, lastDy = -1, dashDx, dashDy;
    // Legacy movement setters remain usable by tools that inspect the player.
    private final InputState movement = new InputState();

    public Player(double x, double y) {
        super(x, y, 34, 34, 100, 3.5);
        previousCenterX = x + width / 2.0; previousCenterY = y + height / 2.0;
    }
    public void step(InputState input) {
        previousCenterX = getCenterX(); previousCenterY = getCenterY();
        if (invulnerability > 0) invulnerability--;
        if (hitFlashTimer > 0) hitFlashTimer--;
        if (dashCooldown > 0) dashCooldown--;
        focused = input.focus;
        double dx = input.dx(), dy = input.dy(), length = Math.hypot(dx, dy);
        if (length > 0) { dx /= length; dy /= length; lastDx = dx; lastDy = dy; }
        if (input.dashPressed && dashCooldown == 0 && dashRemaining == 0) {
            dashRemaining = DASH_TICKS; dashCooldown = DASH_COOLDOWN;
            dashDx = lastDx; dashDy = lastDy;
        }
        input.dashPressed = false;
        dashedThisTick = dashRemaining > 0;
        if (dashedThisTick) {
            x += dashDx * (120.0 / DASH_TICKS); y += dashDy * (120.0 / DASH_TICKS);
            dashRemaining--;
        } else {
            double step = getSpeed() * (focused ? 0.45 : 1);
            x += dx * step; y += dy * step;
        }
    }
    /** All player damage, including boss attacks, must pass this gate. */
    public boolean hit(double damage) {
        if (dead || invulnerability > 0 || damage <= 0) return false;
        super.takeDamage(damage);
        invulnerability = HIT_INVULNERABILITY;
        hitsTaken++;
        return true;
    }
    @Override public void takeDamage(double damage) { hit(damage); }
    @Override public void update() { if (hitFlashTimer > 0) hitFlashTimer--; }
    public void move() { step(movement); }
    public boolean useBomb() {
        if (dead || bombs <= 0) return false;
        bombs--; bombsUsed++; invulnerability = Math.max(invulnerability, 60); return true;
    }
    public boolean canGraze() { return !dead && invulnerability == 0 && !dashedThisTick; }
    public void heal(double amount) { if (!dead) hp = Math.min(maxHp, hp + amount); }
    public void clampToBounds(double minX, double minY, double maxX, double maxY) {
        x = Math.max(minX, Math.min(maxX - width, x));
        y = Math.max(minY, Math.min(maxY - height, y));
    }
    public boolean gainExp(int amount) { exp += Math.max(0, amount); return exp >= getExpToNextLevel(); }
    public boolean canLevelUp() { return exp >= getExpToNextLevel(); }
    public void consumeExpAndLevelUp() {
        if (!canLevelUp()) throw new IllegalStateException("Not enough EXP");
        exp -= getExpToNextLevel(); level++;
    }
    public int getExpToNextLevel() { return 10 + 5 * (level - 1); }
    public int getLevel() { return level; }
    public int getCurrentExp() { return exp; }
    public int getKillCount() { return kills; }
    public void incrementKillCount() { kills++; }
    public int passiveLevel(PassiveId id) { return passives.getOrDefault(id, 0); }
    public Map<PassiveId, Integer> getPassives() { return Collections.unmodifiableMap(passives); }
    public boolean canUpgrade(PassiveId id) {
        return passiveLevel(id) < 3 && (passives.containsKey(id) || passives.size() < 4);
    }
    public void upgrade(PassiveId id) {
        if (!canUpgrade(id)) throw new IllegalStateException("Passive cap");
        passives.put(id, passiveLevel(id) + 1);
    }
    public double getDamageMultiplier() { return 1 + 0.1 * passiveLevel(PassiveId.DAMAGE); }
    public double cooldownMultiplier() { return 1 - 0.08 * passiveLevel(PassiveId.COOLDOWN); }
    public double areaMultiplier() { return 1 + 0.1 * passiveLevel(PassiveId.AREA); }
    public int extraProjectiles() { return passiveLevel(PassiveId.AMOUNT); }
    @Override public double getSpeed() { return speed * (1 + 0.05 * passiveLevel(PassiveId.SPEED)); }
    public double getPickupRadius() { return 90 * (1 + 0.2 * passiveLevel(PassiveId.MAGNET)); }
    public java.util.List<Weapon> getWeapons() { return Collections.unmodifiableList(weapons); }
    public Weapon getWeapon(WeaponId id) {
        for (Weapon weapon : weapons) if (weapon.id == id) return weapon;
        return null;
    }
    public Weapon getWeapon(Class<? extends Weapon> type) {
        for (Weapon weapon : weapons) if (type.isInstance(weapon)) return weapon;
        return null;
    }
    public void addWeapon(Weapon weapon) {
        if (weapons.size() >= 4 || getWeapon(weapon.id) != null) throw new IllegalStateException("Weapon cap");
        weapons.add(weapon);
    }
    public void evolve(WeaponId base, Weapon evolved) {
        Weapon old = getWeapon(base);
        if (old == null) throw new IllegalStateException("Missing base weapon");
        weapons.set(weapons.indexOf(old), evolved);
    }
    public void setUp(boolean value) { movement.up = value; }
    public void setDown(boolean value) { movement.down = value; }
    public void setLeft(boolean value) { movement.left = value; }
    public void setRight(boolean value) { movement.right = value; }
    public boolean isUp() { return movement.up; }
    @Override public void draw(Graphics2D g) {
        int cx = (int) getCenterX(), cy = (int) getCenterY();
        // Always visible, including during damage blinking; leave the central hitbox unobstructed.
        int barX = cx - 24, barY = (int) y - 13;
        double fraction = Math.max(0, Math.min(1, getHp() / getMaxHp()));
        g.setColor(new Color(8, 12, 18, 230)); g.fillRoundRect(barX - 2, barY - 2, 52, 10, 4, 4);
        g.setColor(new Color(85, 40, 50)); g.fillRect(barX, barY, 48, 6);
        g.setColor(fraction > .5 ? new Color(95, 230, 140) : fraction > .25 ? new Color(255, 200, 80) : new Color(255, 90, 100));
        g.fillRect(barX, barY, (int) Math.round(48 * fraction), 6);
        if (invulnerability == 0 || invulnerability % 6 < 3) {
            g.setColor(hitFlashTimer > 0 ? Color.WHITE : new Color(70, 160, 240));
            g.fillOval((int) x, (int) y, width, height);
            g.setColor(new Color(255, 210, 90)); g.fillOval(cx - 7, cy - 10, 14, 16);
        }
        if (focused) {
            g.setColor(new Color(255, 255, 255, 80)); g.drawOval(cx - 18, cy - 18, 36, 36);
            g.setColor(Color.BLACK); g.fillOval(cx - 6, cy - 6, 12, 12);
            g.setColor(Color.WHITE); g.fillOval(cx - 4, cy - 4, 8, 8);
            g.setColor(Color.RED); g.fillOval(cx - 2, cy - 2, 4, 4);
        }
    }
}
