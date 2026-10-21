import java.awt.*;
public abstract class Weapon {
    public final WeaponId id;
    protected int level = 1, timer;
    protected final int baseCooldown;
    protected Weapon(WeaponId id, int cooldown) { this.id = id; baseCooldown = cooldown; }
    public void update(GameWorld world) {
        if (++timer >= Math.max(1, Math.round(baseCooldown * world.player.cooldownMultiplier()))) {
            timer = 0; fire(world);
        }
    }
    protected abstract void fire(GameWorld world);
    public void draw(Graphics2D g, Player player) {}
    public void upgrade() {
        if (isMaxLevel()) throw new IllegalStateException("Weapon already maxed");
        level++;
    }
    public boolean isEvolved() { return id == WeaponId.PRISM || id == WeaponId.DUAL_ORBIT; }
    public boolean isMaxLevel() { return level >= getMaxLevel(); }
    public int getMaxLevel() { return isEvolved() ? 1 : 5; }
    public int getLevel() { return level; }
    public String getName() { return id.label; }
    public String getDescription() { return "Cấp " + level + "/" + getMaxLevel() + " • tăng sức mạnh theo cấp"; }
    public String getUpgradeDescription() {
        Weapon next = create(id);
        next.level = Math.min(getMaxLevel(), level + 1);
        return next.getDescription();
    }
    protected String cooldownText() { return String.format(java.util.Locale.ROOT, "%.2f s", baseCooldown / 60.0); }
    /** Keep one central shot even for an even projectile count: an evolution must not lose its aimed ray. */
    public static double fanOffset(int index, double spread) {
        return index == 0 ? 0 : ((index + 1) / 2) * (index % 2 == 1 ? 1 : -1) * spread;
    }
    public static Weapon create(WeaponId id) {
        switch (id) {
            case WAND: return new MagicWand();
            case ORBIT: return new OrbitBlade();
            case AURA: return new HolyAura();
            case PIERCING: return new PiercingShot();
            case CHAIN: return new ChainLightning();
            case PRISM: return new MagicWand(true);
            case DUAL_ORBIT: return new OrbitBlade(true);
            default: throw new IllegalArgumentException("Unknown weapon");
        }
    }
}
