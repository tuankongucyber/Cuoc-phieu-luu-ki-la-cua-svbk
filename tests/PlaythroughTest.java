import java.util.*;
import java.nio.file.*;

/** A steering bot exercises real inputs and level choices; it never changes HP, stats, timers, or bullet rules. */
public final class PlaythroughTest {
    enum Build {
        TARGET(new WeaponId[]{WeaponId.WAND, WeaponId.PIERCING, WeaponId.CHAIN, WeaponId.ORBIT},
            new PassiveId[]{PassiveId.COOLDOWN, PassiveId.DAMAGE, PassiveId.AMOUNT, PassiveId.AREA}),
        AREA(new WeaponId[]{WeaponId.WAND, WeaponId.CHAIN, WeaponId.AURA, WeaponId.PIERCING},
            new PassiveId[]{PassiveId.DAMAGE, PassiveId.COOLDOWN, PassiveId.AREA, PassiveId.MAGNET}),
        CONTROL(new WeaponId[]{WeaponId.WAND, WeaponId.ORBIT, WeaponId.AURA, WeaponId.CHAIN},
            new PassiveId[]{PassiveId.AREA, PassiveId.DAMAGE, PassiveId.COOLDOWN, PassiveId.SPEED});
        final WeaponId[] weapons; final PassiveId[] passives;
        Build(WeaponId[] weapons, PassiveId[] passives) { this.weapons = weapons; this.passives = passives; }
    }
    static int bestUpgrade(GameWorld world, Build build) {
        int choice = 0; double best = -1e9;
        for (int i = 0; i < world.upgradeChoices.size(); i++) {
            UpgradeChoice c = world.upgradeChoices.get(i); double rank = -100;
            if (c.id.startsWith("evolve:")) rank = 100;
            for (int n = 0; n < build.weapons.length; n++) if (c.id.equals("weapon:" + build.weapons[n])) {
                Weapon equipped = world.player.getWeapon(build.weapons[n]);
                rank = (equipped == null ? 55 : 60) - n * 6 + (equipped == null ? 0 : equipped.getLevel() * 2);
            }
            for (int n = 0; n < build.passives.length; n++) if (c.id.equals("passive:" + build.passives[n]))
                rank = 40 - n * 3 + world.player.passiveLevel(build.passives[n]) * 3;
            if (c.id.equals("heal")) rank = world.player.getHp() < 60 ? 80 : 0;
            if (rank > best) { best = rank; choice = i; }
        }
        if (best < 0 && world.rerolls > 0) { world.reroll(); return bestUpgrade(world, build); }
        return choice;
    }
    static void steer(GameWorld world, boolean allowBomb) {
        Player p = world.player; double px = p.getCenterX(), py = p.getCenterY();
        double targetX = px, targetY = py, nearestGem = Double.MAX_VALUE;
        if (world.inBossRoom()) {
            targetX = 500; targetY = 550;
            // Read an approaching wall's visible gap before it is inside the short collision horizon.
            if (world.boss.encounter == 1 && world.boss.phase == 2) {
                Projectile row = null;
                for (Projectile bullet : world.projectiles) if (bullet.hostile && Math.abs(bullet.vx) < 1e-8 && bullet.vy > 1
                    && bullet.y < py && (row == null || bullet.y > row.y)) row = bullet;
                if (row != null && (py - row.y) / row.vy < 180) {
                    List<Double> xs = new ArrayList<>();
                    for (Projectile bullet : world.projectiles) if (bullet.hostile && Math.abs(bullet.y - row.y) < 1
                        && Math.abs(bullet.vx) < 1e-8) xs.add(bullet.x);
                    Collections.sort(xs); double largest = 0;
                    for (int n = 1; n < xs.size(); n++) if (xs.get(n) - xs.get(n - 1) > largest) {
                        largest = xs.get(n) - xs.get(n - 1); targetX = (xs.get(n) + xs.get(n - 1)) / 2;
                    }
                }
            }
        }
        else {
            for (DropItem item : world.drops) {
                double d = Math.hypot(item.getX() - px, item.getY() - py);
                if (d < nearestGem) { nearestGem = d; targetX = item.getX(); targetY = item.getY(); }
            }
            if (nearestGem == Double.MAX_VALUE) {
                targetX = px + Math.cos(world.ticks * 0.0018) * 80;
                targetY = py + Math.sin(world.ticks * 0.0018) * 80;
            }
        }
        int bestX = 0, bestY = 0; boolean focus = false;
        double best = Double.MAX_VALUE, stationaryDanger = 0;
        for (int mode = 0; mode < 2; mode++) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            double length = Math.hypot(dx, dy), speed = p.getSpeed() * (mode == 1 ? .45 : 1);
            double vx = length == 0 ? 0 : dx / length * speed, vy = length == 0 ? 0 : dy / length * speed;
            double risk = 0;
            for (Projectile b : world.projectiles) if (b.hostile && !b.dead) {
                double rx = b.x - px, ry = b.y - py, rvx = b.vx - vx, rvy = b.vy - vy;
                double len = rvx * rvx + rvy * rvy;
                double t = len == 0 ? 0 : Math.max(0, Math.min(30, -(rx * rvx + ry * rvy) / len));
                double distance = Math.hypot(rx + rvx * t, ry + rvy * t);
                double clearance = world.th06 == null ? 26 : b.radius + Player.BULLET_RADIUS + 14;
                if (distance < clearance) risk += (clearance - distance) * (clearance - distance) / (1 + t * .08);
            }
            if (world.th06 != null) for (Th06Runtime.Beam beam : world.th06.beams) {
                double distance = Math.sqrt(Collision.segmentsDistanceSquared(beam.x1(), beam.y1(), beam.x2(), beam.y2(), px, py, px + vx * 30, py + vy * 30));
                double clearance = beam.width() / 4 + Player.BULLET_RADIUS + 18;
                if (distance < clearance) risk += Math.pow(clearance - distance, 2) * (beam.active() ? 4 : 1);
            }
            for (Enemy enemy : world.enemies) if (!enemy.isDead() && enemy.getContactDamage() > 0) {
                double rx = enemy.getCenterX() - px, ry = enemy.getCenterY() - py;
                if (enemy instanceof Boss) {
                    Boss boss = (Boss) enemy;
                    if (boss.encounter == 1 && boss.phase == 2 && boss.dashTicks == 0
                        && boss.dashWait <= world.difficulty.bossWarning) {
                        double startX = rx - vx * boss.dashWait, startY = ry - vy * boss.dashWait;
                        double endX = startX + Math.cos(boss.dashAim) * boss.dashDistance - vx * 14;
                        double endY = startY + Math.sin(boss.dashAim) * boss.dashDistance - vy * 14;
                        double distanceToDash = Math.sqrt(Collision.segmentDistanceSquared(startX, startY, endX, endY));
                        double clearanceFromDash = boss.bodyRadius() + 35;
                        if (distanceToDash < clearanceFromDash) risk += Math.pow(clearanceFromDash - distanceToDash, 2) * 3;
                    }
                }
                double distance = Math.hypot(rx, ry), evx = 0, evy = 0;
                if (enemy instanceof Th06Runtime.Actor) {
                    evx = enemy.getCenterX() - enemy.previousCenterX; evy = enemy.getCenterY() - enemy.previousCenterY;
                } else if (enemy instanceof Boss && ((Boss) enemy).dashTicks > 0) {
                    evx = ((Boss) enemy).dashDx; evy = ((Boss) enemy).dashDy;
                } else if (!(enemy instanceof Boss) && distance > 0) {
                    evx = -rx / distance * enemy.speed; evy = -ry / distance * enemy.speed;
                }
                double rvx = evx - vx, rvy = evy - vy, len = rvx * rvx + rvy * rvy;
                double t = len == 0 ? 0 : Math.max(0, Math.min(24, -(rx * rvx + ry * rvy) / len));
                double closest = Math.hypot(rx + rvx * t, ry + rvy * t);
                double clearance = enemy.bodyRadius() + 29;
                if (closest < clearance) risk += (clearance - closest) * (clearance - closest) * 1.8;
            }
            if (dx == 0 && dy == 0 && mode == 0) stationaryDanger = risk;
            double nx = px + vx * 24, ny = py + vy * 24;
            if (world.th06 != null) {
                if (nx < Th06Runtime.ARENA.getMinX() + 22 || nx > Th06Runtime.ARENA.getMaxX() - 22
                    || ny < Th06Runtime.ARENA.getMinY() + 22 || ny > Th06Runtime.ARENA.getMaxY() - 22) risk += 100000;
            } else if (world.inBossRoom() && (nx < 60 || nx > 940 || ny < 150 || ny > 690)) risk += 100000;
            double targetCost = Math.hypot(px + vx * 14 - targetX, py + vy * 14 - targetY);
            double total = risk * 12 + targetCost;
            if (total < best) { best = total; bestX = dx; bestY = dy; focus = mode == 1; }
        }
        world.input.left = bestX < 0; world.input.right = bestX > 0;
        world.input.up = bestY < 0; world.input.down = bestY > 0; world.input.focus = focus;
        if (allowBomb && p.invulnerability == 0 && p.bombs > 0 && best > 5000 && stationaryDanger > 150)
            world.input.bombPressed = true;
    }
    public static void main(String[] args) throws Exception {
        StringBuilder report = new StringBuilder();
        int victories = 0;
        for (Build build : Build.values()) {
            GameWorld world = new GameWorld(20261020, Difficulty.NORMAL);
            int updates = 0;
            while (world.state != GameWorld.State.VICTORY && world.state != GameWorld.State.DEFEAT && updates < 108000) {
                if (world.state == GameWorld.State.LEVEL_UP) { world.chooseUpgrade(bestUpgrade(world, build)); continue; }
                // Two legal controller policies: continuous micro-dodging, or a held-input
                // cadence for the speed/control build to avoid frame-to-frame direction chatter.
                int cadence = world.th06 != null && build != Build.CONTROL ? 1 : 4;
                if (world.ticks % cadence == 0) steer(world, true);
                world.tick(); updates++;
            }
            StringBuilder equipment = new StringBuilder();
            for (Weapon w : world.player.getWeapons()) equipment.append(w.id).append(":").append(w.getLevel()).append(" ");
            String line = String.format(Locale.ROOT,
                "%s seed=%d result=%s time=%s HP=%.1f level=%d kills=%d graze=%d bombs=%d captures=%d build=[%s]",
                build, world.seed, world.state, GameRenderer.time(world.ticks), world.player.getHp(), world.player.getLevel(),
                world.player.getKillCount(), world.grazes, world.player.bombsUsed, world.captures, equipment);
            System.out.println(line); System.out.flush(); report.append(line).append("\n");
            if (world.state == GameWorld.State.VICTORY) victories++;
            world.dispose();
        }
        if (args.length > 0) Files.writeString(Path.of(args[0], "playthroughs.txt"), report);
        if (victories != 3) throw new AssertionError("Expected all three build routes to finish");
    }
}
