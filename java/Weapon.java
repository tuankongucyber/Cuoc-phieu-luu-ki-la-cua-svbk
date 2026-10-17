import java.awt.Graphics2D;
import java.util.List;

/**
 * Lớp cha trừu tượng cho mọi vũ khí tự động trong game.
 * Mỗi vũ khí có cấp độ, thời gian hồi chiêu, và cơ chế tấn công riêng biệt.
 */
public abstract class Weapon {
    protected String name;
    protected int level = 1;
    protected int maxLevel = Integer.MAX_VALUE; // Không giới hạn cấp độ
    protected int cooldown;
    protected int timer = 0;
    protected double baseDamage;
    protected double currentDamage;

    public Weapon(String name, int cooldown, double baseDamage) {
        this.name = name;
        this.cooldown = cooldown;
        this.baseDamage = baseDamage;
        this.currentDamage = baseDamage;
    }

    public abstract void update(Player player, List<Enemy> enemies, List<Projectile> projectiles, List<DamageText> damageTexts, Sound sound);
    public abstract void draw(Graphics2D g2, Player player);
    public abstract void upgrade();
    public abstract String getDescription();
    public abstract Weapon copy();

    public String getName() { return name; }
    public int getLevel() { return level; }
    public int getMaxLevel() { return maxLevel; }
    public boolean isMaxLevel() { return level >= maxLevel; }
    public double getCurrentDamage() { return currentDamage; }
}
