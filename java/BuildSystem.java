import java.util.*;
public final class BuildSystem {
    public static java.util.List<UpgradeChoice> choices(GameWorld world) {
        Player player = world.player;
        java.util.List<UpgradeChoice> pool = new ArrayList<>(), evolutions = new ArrayList<>();
        evolution(world, evolutions, WeaponId.WAND, PassiveId.COOLDOWN, WeaponId.PRISM);
        evolution(world, evolutions, WeaponId.ORBIT, PassiveId.AREA, WeaponId.DUAL_ORBIT);
        for (WeaponId id : new WeaponId[] {WeaponId.WAND, WeaponId.ORBIT, WeaponId.AURA, WeaponId.PIERCING, WeaponId.CHAIN}) {
            // Evolved weapons occupy their base slot permanently.
            if ((id == WeaponId.WAND && player.getWeapon(WeaponId.PRISM) != null)
                || (id == WeaponId.ORBIT && player.getWeapon(WeaponId.DUAL_ORBIT) != null)) continue;
            Weapon weapon = player.getWeapon(id);
            if (weapon == null && player.getWeapons().size() < 4) {
                pool.add(new UpgradeChoice("weapon:" + id, "Mở khóa " + id.label, Weapon.create(id).getDescription(), 0, 5,
                    () -> player.getWeapons().size() < 4 && player.getWeapon(id) == null, () -> player.addWeapon(Weapon.create(id))));
            } else if (weapon != null && !weapon.isMaxLevel()) {
                pool.add(new UpgradeChoice("weapon:" + id, id.label, weapon.getUpgradeDescription(), weapon.getLevel(), weapon.getMaxLevel(),
                    () -> !weapon.isMaxLevel(), weapon::upgrade));
            }
        }
        for (PassiveId id : PassiveId.values()) if (player.canUpgrade(id)) {
            pool.add(new UpgradeChoice("passive:" + id, id.label,
                id.description, player.passiveLevel(id), 3, () -> player.canUpgrade(id), () -> player.upgrade(id)));
        }
        Collections.shuffle(pool, world.random);
        Collections.shuffle(evolutions, world.random);
        // Eligible evolutions are guaranteed on the next choice screen, including rerolls.
        java.util.List<UpgradeChoice> result = new ArrayList<>(evolutions);
        for (UpgradeChoice choice : pool) if (result.size() < 3) result.add(choice);
        result.removeIf(choice -> !choice.isValid() || (choice.maxLevel > 0 && choice.currentLevel >= choice.maxLevel));
        if (result.size() < 3 && player.getHp() < player.getMaxHp())
            result.add(new UpgradeChoice("heal", "Hồi 25 HP", "Hồi máu ngay lập tức.", () -> player.getHp() < player.getMaxHp(), () -> player.heal(25)));
        int index = 0;
        while (result.size() < 3) {
            final int amount = 250 * (++index);
            result.add(new UpgradeChoice("score:" + amount, "+" + amount + " điểm", "Thưởng điểm khi các lựa chọn build đã cạn.",
                () -> true, () -> world.score += amount));
        }
        return result;
    }
    private static void evolution(GameWorld world, java.util.List<UpgradeChoice> result, WeaponId base, PassiveId passive, WeaponId evolved) {
        Player player = world.player;
        Weapon weapon = player.getWeapon(base);
        if (weapon != null && weapon.isMaxLevel() && player.passiveLevel(passive) == 3) {
            result.add(new UpgradeChoice("evolve:" + evolved, "TIẾN HÓA • " + evolved.label,
                base.label + " cấp 5 + " + passive.label + " cấp 3; giữ nguyên slot.",
                0, 1,
                () -> player.getWeapon(base) == weapon && player.passiveLevel(passive) == 3,
                () -> player.evolve(base, Weapon.create(evolved))));
        }
    }
}
