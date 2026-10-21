import java.awt.*;
import java.util.Locale;

public final class GameRenderer {
    private static final Color GOLD = new Color(255, 215, 100), MUTED = new Color(180, 195, 215);
    public static final Rectangle[] CARDS = {new Rectangle(55, 250, 284, 270), new Rectangle(358, 250, 284, 270), new Rectangle(661, 250, 284, 270)};
    public static final Rectangle REROLL = new Rectangle(360, 550, 280, 46);
    public void draw(Graphics2D original, GameWorld world) {
        Graphics2D g = (Graphics2D) original.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double cx = world.cameraX(), cy = world.cameraY();
        g.setColor(world.inBossRoom() ? new Color(30, 15, 32) : new Color(17, 24, 32));
        g.fillRect(0, 0, 1000, 750);
        g.translate(-cx, -cy);
        g.setColor(world.inBossRoom() ? new Color(60, 35, 60) : new Color(30, 42, 52));
        for (int x = (int) Math.floor(cx / 60) * 60; x < cx + 1060; x += 60) g.drawLine(x, (int) cy, x, (int) cy + 750);
        for (int y = (int) Math.floor(cy / 60) * 60; y < cy + 810; y += 60) g.drawLine((int) cx, y, (int) cx + 1000, y);
        if (world.inBossRoom()) {
            g.setColor(new Color(220, 90, 135));
            if (world.th06 != null) g.draw(Th06Runtime.ARENA); else g.drawRect(24, 105, 952, 621);
        }
        Shape combatClip = g.getClip();
        if (world.th06 != null) g.clip(Th06Runtime.ARENA);
        for (DropItem drop : world.drops) if (visible(drop.getX(), drop.getY(), cx, cy)) drop.draw(g);
        for (Projectile p : world.projectiles) if (!p.hostile && !p.dead && visible(p.x, p.y, cx, cy)) p.draw(g);
        for (Weapon w : world.player.getWeapons()) w.draw(g, world.player);
        for (Enemy e : world.enemies) if (!e.isDead() && visible(e.getCenterX(), e.getCenterY(), cx, cy)) e.draw(g);
        for (GameWorld.Effect e : world.effects) {
            g.setColor(new Color(e.color.getRed(), e.color.getGreen(), e.color.getBlue(), e.life * 10));
            g.drawLine((int) e.x, (int) e.y, (int) e.x2, (int) e.y2);
        }
        for (DamageText text : world.damageTexts) text.draw(g);
        world.player.draw(g);
        // Hostile bullets are always above friendly attacks, effects and damage numbers.
        for (Projectile p : world.projectiles) if (p.hostile && !p.dead && visible(p.x, p.y, cx, cy)) p.draw(g);
        if (world.th06 != null) world.th06.drawBeams(g);
        if (world.player.focused) {
            int x = (int) world.player.getCenterX(), y = (int) world.player.getCenterY();
            g.setColor(Color.BLACK); g.fillOval(x - 6, y - 6, 12, 12);
            g.setColor(Color.WHITE); g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(Color.RED); g.fillOval(x - 2, y - 2, 4, 4);
        }
        g.setClip(combatClip);
        g.translate(cx, cy);
        if (world.bombFlash > 0) {
            g.setColor(new Color(150, 190, 255, world.bombFlash * 4)); g.fillRect(0, 0, 1000, 750);
        }
        drawHud(g, world);
        if (world.director.segment == RunDirector.Segment.WARNING) {
            g.setColor(new Color(15, 10, 20, 215)); g.fillRect(0, 290, 1000, 130);
            centered(g, "CẢNH BÁO • BOSS ĐANG ĐẾN", 340, 28, GOLD);
            centered(g, "Đấu trường sẽ đóng sau " + ((world.director.warningTicks + 59) / 60) + " giây", 378, 17, MUTED);
        }
        if (world.noticeTicks > 0) centered(g, world.notice, 690, 16, GOLD);
        centered(g, "Shift: Focus   •   X: Dash   •   Space: Bomb   •   Esc: Tạm dừng", 733, 14, MUTED);
        if (world.state == GameWorld.State.LEVEL_UP) drawLevelUp(g, world);
        if (world.state == GameWorld.State.VICTORY || world.state == GameWorld.State.DEFEAT) drawResult(g, world);
        g.dispose();
    }
    private static boolean visible(double x, double y, double cx, double cy) {
        return x > cx - 70 && x < cx + 1070 && y > cy - 70 && y < cy + 820;
    }
    private void drawHud(Graphics2D g, GameWorld w) {
        g.setColor(new Color(10, 15, 25, 235)); g.fillRect(0, 0, 1000, w.inBossRoom() ? 100 : 88);
        g.setColor(new Color(35, 50, 70)); g.fillRect(0, 0, 1000, 7);
        g.setColor(new Color(80, 185, 235));
        g.fillRect(0, 0, (int) (1000 * Math.min(1, (double) w.player.getCurrentExp() / w.player.getExpToNextLevel())), 7);
        label(g, "HP " + (int) w.player.getHp() + "/100   Lv " + w.player.getLevel(), 16, 30, 17, Color.WHITE);
        label(g, "Bomb " + w.player.bombs + "/3   Dash " + (w.player.dashCooldown == 0 ? "SẴN SÀNG" : String.format(Locale.ROOT, "%.1fs", w.player.dashCooldown / 60.0)), 225, 30, 15, MUTED);
        label(g, "Graze " + w.grazes + "   Điểm " + w.score, 530, 30, 15, GOLD);
        label(g, (w.isBossTest() ? "TEST BOSS" : time(w.director.fieldTicks) + " / 09:00") + " • " + w.difficulty, 790, 30, 15, MUTED);
        int x = 16;
        for (Weapon weapon : w.player.getWeapons()) {
            label(g, weapon.getName() + (weapon.isEvolved() ? " ★" : " " + weapon.getLevel() + "/5"), x, 53, 13, MUTED); x += 180;
        }
        StringBuilder passives = new StringBuilder("Nội tại: ");
        w.player.getPassives().forEach((id, rank) -> passives.append(id.label).append(" ").append(rank).append("/3  "));
        label(g, passives.toString(), 16, 75, 12, MUTED);
        if (w.inBossRoom() && w.boss != null) {
            Boss boss = w.boss;
            label(g, boss.title(), 16, 96, 13, GOLD);
            g.setColor(new Color(60, 30, 50)); g.fillRect(535, 83, 360, 10);
            g.setColor(new Color(235, 90, 150)); g.fillRect(535, 83, (int) (360 * boss.getHp() / boss.getMaxHp()), 10);
            label(g, "HP " + (int) Math.ceil(100 * boss.getHp() / boss.getMaxHp()) + "%", 910, 96, 13, Color.WHITE);
        }
    }
    private void drawLevelUp(Graphics2D g, GameWorld w) {
        dim(g);
        centered(g, "LÊN CẤP • CHỌN HƯỚNG BUILD", 190, 30, GOLD);
        centered(g, "Vũ khí " + w.player.getWeapons().size() + "/4   •   Nội tại " + w.player.getPassives().size() + "/4", 220, 16, MUTED);
        for (int i = 0; i < w.upgradeChoices.size(); i++) {
            Rectangle r = CARDS[i]; UpgradeChoice choice = w.upgradeChoices.get(i);
            g.setColor(new Color(27, 40, 60)); g.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
            g.setColor(new Color(75, 150, 195)); g.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
            label(g, "[" + (i + 1) + "]", r.x + 20, r.y + 33, 20, GOLD);
            label(g, choice.progressLabel(), r.x + 128, r.y + 32, 14, GOLD);
            wrapped(g, choice.title, r.x + 20, r.y + 80, r.width - 40, 19, Color.WHITE);
            wrapped(g, choice.description, r.x + 20, r.y + 160, r.width - 40, 15, MUTED);
        }
        g.setColor(w.rerolls > 0 ? new Color(45, 80, 105) : new Color(40, 45, 55));
        g.fillRoundRect(REROLL.x, REROLL.y, REROLL.width, REROLL.height, 10, 10);
        centered(g, "[R] Đổi lựa chọn • còn " + w.rerolls, 580, 17, Color.WHITE);
        centered(g, "Phím 1 / 2 / 3 hoặc nhấp thẻ • Esc vẫn tạm dừng được", 640, 15, MUTED);
    }
    private void drawResult(Graphics2D g, GameWorld w) {
        dim(g);
        centered(g, w.state == GameWorld.State.VICTORY ? (w.isBossTest() ? "HOÀN THÀNH TEST BOSS!" : "VƯỢT QUA RANH GIỚI!") : "HÀNH TRÌNH KẾT THÚC", 145, 32, GOLD);
        centered(g, "Thời gian " + time(w.ticks) + " • Hạ " + w.player.getKillCount() + " quái • Lv " + w.player.getLevel(), 185, 17, Color.WHITE);
        centered(g, "Điểm " + w.score + " • Graze " + w.grazes + " • Capture " + w.captures + (w.th06 != null || w.originalPractice != null ? "" : "/" + (w.isBossTest() ? 3 : 6)) + " • Bomb đã dùng " + w.player.bombsUsed, 215, 17, MUTED);
        if (w.state == GameWorld.State.DEFEAT) centered(g, "Đòn cuối: " + w.lastDamageSource, 245, 16, new Color(255, 150, 155));
        int y = 285;
        for (Weapon weapon : w.player.getWeapons()) {
            label(g, weapon.getName() + (weapon.isEvolved() ? " ★" : " cấp " + weapon.getLevel()), 225, y, 17, Color.WHITE);
            label(g, String.format(Locale.ROOT, "%,.0f sát thương", w.damageByWeapon.getOrDefault(weapon.id, 0.0)), 590, y, 16, MUTED);
            y += 32;
        }
        // Include damage of weapons that were replaced by evolution.
        for (WeaponId id : w.damageByWeapon.keySet()) if (w.player.getWeapon(id) == null) {
            label(g, id.label + " (trước tiến hóa)", 225, y, 15, MUTED);
            label(g, String.format(Locale.ROOT, "%,.0f", w.damageByWeapon.get(id)), 590, y, 15, MUTED); y += 26;
        }
        wrapped(g, passiveSummary(w.player), 225, y + 15, 560, 14, MUTED);
        centered(g, "Seed " + w.seed + " • " + w.difficulty, 605, 14, MUTED);
        centered(g, "Enter: chơi lại cùng seed   •   Esc: về Start", 655, 18, GOLD);
    }
    private String passiveSummary(Player player) {
        StringBuilder text = new StringBuilder("Nội tại: ");
        player.getPassives().forEach((id, rank) -> text.append(id.label).append(" ").append(rank).append("/3; "));
        return text.toString();
    }
    private static void dim(Graphics2D g) { g.setColor(new Color(0, 0, 0, 205)); g.fillRect(0, 0, 1000, 750); }
    public static String time(long ticks) { return String.format(Locale.ROOT, "%02d:%02d", ticks / 3600, (ticks / 60) % 60); }
    public static void label(Graphics2D g, String text, int x, int y, int size, Color color) {
        g.setFont(new Font("Arial", Font.PLAIN, size)); g.setColor(color); g.drawString(text, x, y);
    }
    public static void centered(Graphics2D g, String text, int y, int size, Color color) {
        g.setFont(new Font("Arial", Font.PLAIN, size)); g.setColor(color);
        g.drawString(text, (1000 - g.getFontMetrics().stringWidth(text)) / 2, y);
    }
    private static void wrapped(Graphics2D g, String text, int x, int y, int width, int size, Color color) {
        g.setFont(new Font("Arial", Font.PLAIN, size)); g.setColor(color);
        String line = "";
        for (String word : text.split(" ")) {
            if (g.getFontMetrics().stringWidth(line + word) > width && !line.isEmpty()) {
                g.drawString(line, x, y); y += size + 7; line = "";
            }
            line += word + " ";
        }
        g.drawString(line, x, y);
    }
}
