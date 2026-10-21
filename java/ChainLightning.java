import java.awt.Color;
import java.util.*;
public class ChainLightning extends Weapon {
    public ChainLightning() { super(WeaponId.CHAIN, 65); }
    @Override protected void fire(GameWorld world) {
        double x = world.player.getCenterX(), y = world.player.getCenterY();
        Set<Enemy> struck = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 2 + level + world.player.extraProjectiles(); i++) {
            Enemy target = world.closestEnemy(x, y, (i == 0 ? 500 : 170) * world.player.areaMultiplier(), struck);
            if (target == null) break;
            struck.add(target);
            world.hitEnemy(target, (25 + (level - 1) * 10) * world.player.getDamageMultiplier(), id);
            world.addEffect(x, y, target.getCenterX(), target.getCenterY(), new Color(160, 150, 255));
            x = target.getCenterX(); y = target.getCenterY();
        }
    }
    @Override public String getDescription() { return (25 + (level - 1) * 10) + " sát thương, " + (2 + level) + " mục tiêu. Nhảy tối đa 170 px; hồi " + cooldownText() + ". Mạnh khi quái tụ đàn."; }
}
