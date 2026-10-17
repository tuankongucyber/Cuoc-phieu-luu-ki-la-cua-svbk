import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Bảng điều khiển và màn hình chơi game chính (GamePanel).
 * Chạy vòng lặp game 60 FPS mượt mà, quản lý trạng thái,
 * sự kiện bàn phím/chuột, va chạm thực thể, hệ thống quái vật và giao diện HUD.
 */
public class GamePanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    public static final int SCREEN_WIDTH = 1000;
    public static final int SCREEN_HEIGHT = 750;

    public enum GameState {
        PLAYING,
        LEVEL_UP,
        GAME_OVER
    }

    private GameState state = GameState.PLAYING;
    private Timer gameTimer;
    private Random random = new Random();
    // Phòng Boss & Cổng dịch chuyển
    private Portal portal = null;
    private boolean portalSpawned = false;
    private boolean inBossRoom = false;
    private boolean bossDefeatedInRoom = false;
    private double returnPlayerX = 0;
    private double returnPlayerY = 0;
    private List<Enemy> savedEnemies = new ArrayList<>();
    private List<DropItem> savedDropItems = new ArrayList<>();
    private List<Projectile> savedProjectiles = new ArrayList<>();

    // Camera
    private double cameraX = 0;
    private double cameraY = 0;

    // Thực thể game
    private Player player;
    private List<Enemy> enemies = new ArrayList<>();
    private List<Projectile> projectiles = new ArrayList<>();
    private List<DropItem> dropItems = new ArrayList<>();
    private List<DamageText> damageTexts = new ArrayList<>();
    private Sound sound = new Sound();

    // Đồng hồ & Tỉ lệ xuất hiện quái
    private int frameCount = 0;
    private int elapsedSeconds = 0;
    private int spawnInterval = 65; // Càng chơi lâu quái ra càng dồn dập
    private boolean bossSpawned1Min = false;
    private boolean bossSpawned2Min = false;

    // Nâng cấp (Level Up)
    private List<UpgradeOption> activeUpgradeChoices = new ArrayList<>();
    private Rectangle[] upgradeCardBounds = new Rectangle[3];
    private int hoveredCardIndex = -1;

    public GamePanel() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(18, 18, 24));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        initGame();

        // Vòng lặp game 60 FPS (~16ms mỗi frame)
        gameTimer = new Timer(16, e -> gameLoop());
        gameTimer.start();
    }

    /**
     * Khởi tạo hoặc đặt lại toàn bộ ván game
     */
    private void initGame() {
        player = new Player(SCREEN_WIDTH / 2.0 - 17, SCREEN_HEIGHT / 2.0 - 17);
        // Tặng vũ khí khởi đầu: Gậy phép Magic Wand
        player.addWeapon(new MagicWand());

        enemies.clear();
        projectiles.clear();
        dropItems.clear();
        damageTexts.clear();

        frameCount = 0;
        elapsedSeconds = 0;
        spawnInterval = 65;
        bossSpawned1Min = false;
        bossSpawned2Min = false;
        state = GameState.PLAYING;
        portal = null;
        portalSpawned = false;
        inBossRoom = false;
        bossDefeatedInRoom = false;
        savedEnemies.clear();
        savedDropItems.clear();
        savedProjectiles.clear();
    }

    /**
     * Vòng lặp chính thực thi mỗi frame
     */
    private void gameLoop() {
        if (state == GameState.PLAYING) {
            updateGame();
        }
        repaint();
    }

    /**
     * Cập nhật logic game
     */
    private void updateGame() {
        frameCount++;
        if (frameCount % 60 == 0) {
            elapsedSeconds++;
            // Tăng độ khó: giảm thời gian chờ giữa các lần sinh quái
            if (spawnInterval > 15 && elapsedSeconds % 10 == 0) {
                spawnInterval -= 3;
            }
        }

        // 1. Cập nhật Player di chuyển
        player.move();
        if (inBossRoom) {
            // Giới hạn người chơi trong phòng đấu kín (kích thước 1000x750)
            player.clampToBounds(20, 20, SCREEN_WIDTH - 20, SCREEN_HEIGHT - 20);
        }
        player.update();

        // Cập nhật vị trí Camera
        if (inBossRoom) {
            // Camera cố định tại phòng đấu Boss
            cameraX = 0;
            cameraY = 0;
        } else {
            // Camera bám theo Player
            cameraX = player.getX() + (player.getWidth() / 2.0) - (SCREEN_WIDTH / 2.0);
            cameraY = player.getY() + (player.getHeight() / 2.0) - (SCREEN_HEIGHT / 2.0);
        }

        // 2. Cập nhật vũ khí của Player
        for (Weapon weapon : player.getWeapons()) {
            weapon.update(player, enemies, projectiles, damageTexts, sound);
        }

        // 3. Cơ chế Cổng dịch chuyển Portal
        // Ở sàn thường: sau 60 giây xuất hiện Cổng Boss ngay gần vị trí Player
        if (!inBossRoom && elapsedSeconds >= 60 && !portalSpawned) {
            portalSpawned = true;
            portal = new Portal(player.getX() + 60, player.getY(), new Color(100, 200, 255, 180), new Color(180, 50, 255, 150), "BOSS PORTAL");
        }

        // Cập nhật portal & kiểm tra dịch chuyển
        if (portal != null) {
            portal.update();
            if (player.getBounds().intersects(portal.getBounds())) {
                if (!inBossRoom) {
                    enterBossRoom();
                } else {
                    exitBossRoom();
                }
            }
        }

        // 4. Sinh quái vật định kỳ
        spawnEnemies();

        // 5. Cập nhật quái vật di chuyển & vũ khí Boss
        for (Enemy enemy : enemies) {
            enemy.moveTowards(player);
            enemy.update();

            // Nếu là Boss có vũ khí (Mirror Boss), cập nhật vũ khí của Boss
            if (enemy instanceof Boss) {
                ((Boss) enemy).updateWeapons(player, projectiles, damageTexts, sound);
                if (player.isDead()) {
                    state = GameState.GAME_OVER;
                    sound.playGameOver();
                    return;
                }
            }

            // Va chạm gây sát thương lên người chơi
            if (player.getBounds().intersects(enemy.getBounds())) {
                if (player.hitFlashTimer == 0) {
                    player.takeDamage(enemy.getContactDamage());
                    damageTexts.add(new DamageText(player.getX(), player.getY() - 10, "-" + (int) enemy.getContactDamage(), Color.RED, true));
                    sound.playHit();
                    if (player.isDead()) {
                        state = GameState.GAME_OVER;
                        sound.playGameOver();
                        return;
                    }
                }
            }
        }

        // 6. Cập nhật đạn và va chạm đạn
        Iterator<Projectile> projIt = projectiles.iterator();
        while (projIt.hasNext()) {
            Projectile p = projIt.next();
            p.update();

            if (p.isHostile()) {
                // Đạn của Boss nhắm vào Player
                if (!player.isDead() && p.getBounds().intersects(player.getBounds())) {
                    if (player.hitFlashTimer == 0) {
                        player.takeDamage(p.getDamage());
                        damageTexts.add(new DamageText(player.getX(), player.getY() - 10, "-" + (int) p.getDamage(), Color.RED, true));
                        sound.playHit();
                        p.setDead(true);
                        if (player.isDead()) {
                            state = GameState.GAME_OVER;
                            sound.playGameOver();
                            return;
                        }
                    }
                }
            } else {
                // Đạn của người chơi nhắm vào quái vật
                // Kiểm tra quái né đạn (nếu là NinjaZombie có phản xạ né)
                for (Enemy enemy : enemies) {
                    if (!enemy.isDead() && enemy instanceof NinjaZombie) {
                        NinjaZombie ninja = (NinjaZombie) enemy;
                        if (ninja.checkAndDodge(projectiles)) {
                            damageTexts.add(new DamageText(ninja.getX(), ninja.getY() - 15, "DODGE!", new Color(180, 100, 255), true));
                        }
                    }
                }

                // Kiểm tra va chạm với quái
                for (Enemy enemy : enemies) {
                    if (!enemy.isDead() && p.getBounds().intersects(enemy.getBounds())) {
                        enemy.takeDamage(p.getDamage());
                        damageTexts.add(new DamageText(enemy.getX(), enemy.getY() - 10, String.valueOf((int) p.getDamage()), Color.YELLOW, false));
                        sound.playHit();
                        p.setDead(true);
                        break;
                    }
                }
            }

            if (p.isDead()) {
                projIt.remove();
            }
        }

        // 7. Xử lý quái chết & rơi vật phẩm
        Iterator<Enemy> enemyIt = enemies.iterator();
        while (enemyIt.hasNext()) {
            Enemy enemy = enemyIt.next();
            if (enemy.isDead()) {
                player.incrementKillCount();
                // Rơi ngọc kinh nghiệm
                dropItems.add(new ExpGem(enemy.getCenterX(), enemy.getCenterY(), enemy.getExpReward()));

                // 6% tỉ lệ rơi thêm bình máu
                if (random.nextDouble() < 0.06) {
                    dropItems.add(new HealthPotion(enemy.getCenterX() + 10, enemy.getCenterY()));
                }

                // Nếu là Boss trong Boss Room bị hạ gục
                if (inBossRoom && enemy instanceof Boss) {
                    bossDefeatedInRoom = true;
                    // Rơi siêu ngọc EXP thưởng lớn và bình máu
                    dropItems.add(new ExpGem(enemy.getCenterX() - 20, enemy.getCenterY(), 50));
                    dropItems.add(new ExpGem(enemy.getCenterX() + 20, enemy.getCenterY(), 50));
                    dropItems.add(new HealthPotion(enemy.getCenterX(), enemy.getCenterY() + 20));
                    // Mở cổng Portal màu vàng quay về thế giới chính
                    portal = new Portal(SCREEN_WIDTH / 2.0 - 25, SCREEN_HEIGHT / 2.0 - 25,
                            new Color(255, 215, 0, 200), new Color(255, 120, 0, 160), "RETURN PORTAL");
                }

                enemyIt.remove();
            }
        }

        // 8. Cập nhật vật phẩm rơi rớt
        Iterator<DropItem> itemIt = dropItems.iterator();
        while (itemIt.hasNext()) {
            DropItem item = itemIt.next();
            item.update(player);
            if (item.isCollected()) {
                sound.playGem();
                // Nếu nhặt xong mà đủ điểm lên cấp
                if (player.getCurrentExp() >= player.getExpToNextLevel()) {
                    triggerLevelUp();
                }
                itemIt.remove();
            }
        }

        // 9. Cập nhật số sát thương nổi
        damageTexts.removeIf(dt -> {
            dt.update();
            return dt.isDead();
        });
    }

    /**
     * Cơ chế xuất hiện quái vật theo thời gian
     */
    private void spawnEnemies() {
        // Trong không gian phòng Boss: không sinh quái thường
        if (inBossRoom) {
            return;
        }

        int margin = 50; // Khoảng cách sinh quái bên ngoài mép màn hình

        // Sinh Boss phút thứ 1 và 2 tại vị trí phía trên/dưới màn hình hiện tại
        if (elapsedSeconds >= 60 && !bossSpawned1Min) {
            bossSpawned1Min = true;
            enemies.add(new Boss(cameraX + SCREEN_WIDTH / 2.0, cameraY - margin));
        }
        if (elapsedSeconds >= 120 && !bossSpawned2Min) {
            bossSpawned2Min = true;
            enemies.add(new Boss(cameraX + SCREEN_WIDTH / 2.0, cameraY + SCREEN_HEIGHT + margin));
        }


        if (frameCount % spawnInterval == 0) {
            int side = random.nextInt(4); // 0: Trên, 1: Dưới, 2: Trái, 3: Phải
            double spawnX = 0, spawnY = 0;

            switch (side) {
                case 0: // Phía trên màn hình
                    spawnX = cameraX + random.nextInt(SCREEN_WIDTH);
                    spawnY = cameraY - margin;
                    break;
                case 1: // Phía dưới màn hình
                    spawnX = cameraX + random.nextInt(SCREEN_WIDTH);
                    spawnY = cameraY + SCREEN_HEIGHT + margin;
                    break;
                case 2: // Bên trái màn hình
                    spawnX = cameraX - margin;
                    spawnY = cameraY + random.nextInt(SCREEN_HEIGHT);
                    break;
                case 3: // Bên phải màn hình
                    spawnX = cameraX + SCREEN_WIDTH + margin;
                    spawnY = cameraY + random.nextInt(SCREEN_HEIGHT);
                    break;
            }

            int roll = random.nextInt(100);
            if (elapsedSeconds < 20) {
                enemies.add(new Bat(spawnX, spawnY));
            } else if (elapsedSeconds < 50) {
                if (roll < 50) enemies.add(new Bat(spawnX, spawnY));
                else if (roll < 80) enemies.add(new Zombie(spawnX, spawnY));
                else enemies.add(new NinjaZombie(spawnX, spawnY)); // 20% sinh NinjaZombie
            } else {
                if (roll < 30) enemies.add(new Bat(spawnX, spawnY));
                else if (roll < 55) enemies.add(new Zombie(spawnX, spawnY));
                else if (roll < 80) enemies.add(new NinjaZombie(spawnX, spawnY)); // 25% sinh NinjaZombie
                else enemies.add(new Skeleton(spawnX, spawnY));
            }
        }
    }

    /**
     * Kích hoạt giao diện Lên cấp (Level Up)
     */
    private void triggerLevelUp() {
        // 1. Tạm dừng di chuyển của người chơi (tránh kẹt phím)
        player.setUp(false);
        player.setDown(false);
        player.setLeft(false);
        player.setRight(false);

        // 2. Chuyển trạng thái game & phát âm thanh
        state = GameState.LEVEL_UP;
        sound.playLevelUp();
        
        // 3. Tạo 3 lựa chọn nâng cấp
        generateUpgradeChoices();
    }

    /**
     * Tạo 3 lựa chọn nâng cấp ngẫu nhiên phong phú
     */
    private void generateUpgradeChoices() {
        List<UpgradeOption> pool = new ArrayList<>();

        // Kiểm tra Gậy phép Magic Wand
        Weapon wand = player.getWeapon(MagicWand.class);
        if (wand != null && !wand.isMaxLevel()) {
            pool.add(new UpgradeOption("Nâng cấp Magic Wand (Lv " + (wand.getLevel() + 1) + ")",
                wand.getDescription(), "VŨ KHÍ", new Color(70, 180, 255), wand::upgrade));
        }

        // Kiểm tra Vòng phi đao OrbitBlade
        Weapon blade = player.getWeapon(OrbitBlade.class);
        if (blade == null) {
            pool.add(new UpgradeOption("Mở khóa Orbit Blades (Lv 1)",
                "Nhận phi đao hộ thể xoay tròn quanh người.", "VŨ KHÍ MỚI", new Color(255, 200, 50),
                () -> player.addWeapon(new OrbitBlade())));
        } else if (!blade.isMaxLevel()) {
            pool.add(new UpgradeOption("Nâng cấp Orbit Blades (Lv " + (blade.getLevel() + 1) + ")",
                blade.getDescription(), "VŨ KHÍ", new Color(255, 200, 50), blade::upgrade));
        }

        // Kiểm tra Hào quang HolyAura
        Weapon aura = player.getWeapon(HolyAura.class);
        if (aura == null) {
            pool.add(new UpgradeOption("Mở khóa Holy Aura (Lv 1)",
                "Tỏa sóng xung kích đẩy lùi và hủy diệt quái xung quanh.", "VŨ KHÍ MỚI", new Color(255, 230, 100),
                () -> player.addWeapon(new HolyAura())));
        } else if (!aura.isMaxLevel()) {
            pool.add(new UpgradeOption("Nâng cấp Holy Aura (Lv " + (aura.getLevel() + 1) + ")",
                aura.getDescription(), "VŨ KHÍ", new Color(255, 230, 100), aura::upgrade));
        }

        // Các nâng cấp nội tại của nhân vật
        pool.add(new UpgradeOption("Tăng Tốc Độ Di Chuyển (+15%)",
            "Giúp nhân vật lạng lách né quái nhanh nhẹn hơn.", "CHỈ SỐ", new Color(80, 250, 150),
            () -> player.setSpeed(player.getSpeed() * 1.15)));

        pool.add(new UpgradeOption("Tăng Bán Kính Thu Thập (+35%)",
            "Hút hạt kinh nghiệm và bình máu từ khoảng cách xa hơn.", "NỘI TẠI", new Color(200, 100, 255),
            () -> player.setPickupRadius(player.getPickupRadius() * 1.35)));

        pool.add(new UpgradeOption("Tăng Sức Mạnh Toàn Diện (+20%)",
            "Gia tăng 20% lượng sát thương gây ra cho tất cả vũ khí.", "SỨC MẠNH", new Color(255, 90, 90),
            () -> player.setDamageMultiplier(player.getDamageMultiplier() * 1.2)));

        pool.add(new UpgradeOption("Hồi Phục Sinh Lực Toàn Bộ",
            "Lập tức hồi đầy 100% lượng máu của bạn.", "HỒI MÁU", new Color(50, 220, 100),
            () -> player.setHp(player.getMaxHp())));

        // Lấy 3 lựa chọn ngẫu nhiên
        Collections.shuffle(pool);
        activeUpgradeChoices = pool.subList(0, Math.min(3, pool.size()));

        // Thiết lập kích thước thẻ hiển thị
        int cardW = 260;
        int cardH = 340;
        int startX = (SCREEN_WIDTH - (3 * cardW + 2 * 30)) / 2;
        int cardY = (SCREEN_HEIGHT - cardH) / 2 + 30;

        for (int i = 0; i < activeUpgradeChoices.size(); i++) {
            upgradeCardBounds[i] = new Rectangle(startX + i * (cardW + 30), cardY, cardW, cardH);
        }
    }

    private void selectUpgrade(int index) {
        if (index >= 0 && index < activeUpgradeChoices.size()) {
            // Áp dụng chỉ số nâng cấp đã chọn
            activeUpgradeChoices.get(index).apply();
            
            // Trừ EXP đã tích lũy và tăng Cấp độ nhân vật
            player.consumeExpAndLevelUp();

            state = GameState.PLAYING;

            // Kiểm tra xem lượng EXP dư còn lại có đủ để Lên cấp tiếp hay không
            if (player.getCurrentExp() >= player.getExpToNextLevel()) {
                triggerLevelUp();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Bật khử răng cưa
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ================= 1. VẼ THẾ GIỚI GAME (CÓ TÁC DỤNG BỞI CAMERA) =================
        // Dịch chuyển hệ tọa độ theo Camera
        g2.translate(-cameraX, -cameraY);

        // Vẽ nền thế giới (tùy theo sàn thường hay phòng Boss)
        drawArena(g2);

        // Vẽ cổng dịch chuyển nếu đang mở
        if (portal != null) {
            portal.draw(g2);
        }

        for (DropItem item : dropItems) {
            item.draw(g2);
        }
        for (Enemy enemy : enemies) {
            enemy.draw(g2);
        }
        for (Projectile p : projectiles) {
            p.draw(g2);
        }
        player.draw(g2);
        for (Weapon weapon : player.getWeapons()) {
            weapon.draw(g2, player);
        }
        for (DamageText dt : damageTexts) {
            dt.draw(g2);
        }

        // Khôi phục lại hệ tọa độ màn hình tĩnh cho giao diện HUD / Menu
        g2.translate(cameraX, cameraY);

        // ================= 2. VẼ GIAO DIỆN HUD / MENU (TỌA ĐỘ MÀN HÌNH TĨNH) =================
        drawHUD(g2);

        if (state == GameState.LEVEL_UP) {
            drawLevelUpModal(g2);
        }

        if (state == GameState.GAME_OVER) {
            drawGameOverScreen(g2);
        }
    }

    /**
     * Dịch chuyển vào không gian Boss Room biệt lập
     */
    private void enterBossRoom() {
        inBossRoom = true;
        portal = null; // Đóng cổng vào
        bossDefeatedInRoom = false;

        // Lưu lại vị trí của player ở sàn đấu ngoài
        returnPlayerX = player.getX();
        returnPlayerY = player.getY();

        // Lưu lại danh sách quái và đồ ở sàn ngoài để không bị mất
        savedEnemies = new ArrayList<>(enemies);
        savedDropItems = new ArrayList<>(dropItems);
        savedProjectiles = new ArrayList<>(projectiles);

        enemies.clear();
        dropItems.clear();
        projectiles.clear();

        // Đặt người chơi ở góc dưới phòng đấu Boss
        player.setX(SCREEN_WIDTH / 2.0 - 17);
        player.setY(SCREEN_HEIGHT - 120);

        // Sinh Boss hoành tráng ở phía trên phòng và sao chép toàn bộ chỉ số & vũ khí từ người chơi
        Boss boss = new Boss(SCREEN_WIDTH / 2.0 - 27, 100);
        boss.copyFromPlayer(player);
        enemies.add(boss);

        // Thông báo sát thương nổi thông báo vào trận Boss
        damageTexts.add(new DamageText(player.getX(), player.getY() - 30, "BOSS BATTLE! MIRROR BOSS", Color.MAGENTA, true));
    }

    /**
     * Rời khỏi phòng Boss để quay lại thế giới chính
     */
    private void exitBossRoom() {
        inBossRoom = false;
        portal = null; // Đóng cổng ra

        // Khôi phục vị trí người chơi sàn ngoài
        player.setX(returnPlayerX);
        player.setY(returnPlayerY);

        // Khôi phục quái và vật phẩm cũ
        enemies = new ArrayList<>(savedEnemies);
        dropItems = new ArrayList<>(savedDropItems);
        projectiles = new ArrayList<>(savedProjectiles);

        savedEnemies.clear();
        savedDropItems.clear();
        savedProjectiles.clear();

        damageTexts.add(new DamageText(player.getX(), player.getY() - 30, "VICTORY! RETURNED", Color.YELLOW, true));
    }

    /**
     * Vẽ bản đồ đấu trường với gạch đá và viền thành trì
     */
    private void drawArena(Graphics2D g2) {
        if (inBossRoom) {
            // Sàn đấu Boss Room biệt lập: Không gian hắc ám rực lửa
            int tileSize = 50;
            for (int x = 0; x < SCREEN_WIDTH; x += tileSize) {
                for (int y = 0; y < SCREEN_HEIGHT; y += tileSize) {
                    if ((x / tileSize + y / tileSize) % 2 == 0) {
                        g2.setColor(new Color(45, 12, 18));
                    } else {
                        g2.setColor(new Color(30, 8, 12));
                    }
                    g2.fillRect(x, y, tileSize, tileSize);
                    g2.setColor(new Color(75, 20, 25));
                    g2.drawRect(x, y, tileSize, tileSize);
                }
            }

            // Viền rào chắn phong ấn đấu trường Boss
            g2.setColor(new Color(255, 50, 60));
            g2.setStroke(new BasicStroke(6f));
            g2.drawRect(10, 10, SCREEN_WIDTH - 20, SCREEN_HEIGHT - 20);
            g2.setStroke(new BasicStroke(1f));
        } else {
            int tileSize = 60;

            // Tính toán tọa độ bắt đầu vẽ dựa trên Camera để tạo hiệu ứng cuộn vô hạn
            long startX = (long) Math.floor(cameraX / tileSize) * tileSize;
            long startY = (long) Math.floor(cameraY / tileSize) * tileSize;

            for (long x = startX; x < cameraX + SCREEN_WIDTH + tileSize; x += tileSize) {
                for (long y = startY; y < cameraY + SCREEN_HEIGHT + tileSize; y += tileSize) {
                    // Xen kẽ màu ô gạch
                    if ((Math.abs(x / tileSize) + Math.abs(y / tileSize)) % 2 == 0) {
                        g2.setColor(new Color(24, 25, 33));
                    } else {
                        g2.setColor(new Color(19, 20, 27));
                    }
                    g2.fillRect((int) x, (int) y, tileSize, tileSize);
                    g2.setColor(new Color(35, 37, 48));
                    g2.drawRect((int) x, (int) y, tileSize, tileSize);
                }
            }
        }
    }

    /**
     * Vẽ giao diện thông tin trạng thái người chơi (HUD)
     */
    private void drawHUD(Graphics2D g2) {
        // Thanh EXP toàn màn hình trên đỉnh
        int barH = 14;
        g2.setColor(new Color(30, 30, 45));
        g2.fillRect(0, 0, SCREEN_WIDTH, barH);

        double expProgress = (double) player.getCurrentExp() / player.getExpToNextLevel();
        int expW = (int) (SCREEN_WIDTH * expProgress);
        g2.setColor(new Color(60, 180, 255));
        g2.fillRect(0, 0, expW, barH);

        // Cấp độ nhân vật
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("LV " + player.getLevel(), 15, barH + 22);

        // Đồng hồ sinh tồn (Timer)
        int min = elapsedSeconds / 60;
        int sec = elapsedSeconds % 60;
        String timeStr = String.format("%02d:%02d", min, sec);
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        g2.drawString(timeStr, SCREEN_WIDTH / 2 - 30, barH + 26);

        // Số quái đã diệt
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString("KILLS: " + player.getKillCount(), SCREEN_WIDTH - 120, barH + 22);

        // Máu người chơi
        g2.drawString(String.format("HP: %d / %d", (int) player.getHp(), (int) player.getMaxHp()), 15, barH + 45);

        // Danh sách vũ khí sở hữu
        int wx = 15;
        for (Weapon w : player.getWeapons()) {
            g2.setColor(new Color(40, 45, 65, 200));
            g2.fillRoundRect(wx, barH + 52, 100, 24, 6, 6);
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Arial", Font.BOLD, 11));
            g2.drawString(w.getName() + " L" + w.getLevel(), wx + 8, barH + 68);
            wx += 110;
        }
    }

    /**
     * Vẽ bảng chọn 3 thẻ nâng cấp khi lên cấp
     */
    private void drawLevelUpModal(Graphics2D g2) {
        // Lớp phủ đen mờ hậu cảnh
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        // Tiêu đề Lên Cấp
        g2.setColor(new Color(255, 215, 0));
        g2.setFont(new Font("Arial", Font.BOLD, 36));
        String title = "★ LEVEL UP! CHỌN NÂNG CẤP ★";
        int titleW = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (SCREEN_WIDTH - titleW) / 2, (SCREEN_HEIGHT - 340) / 2 - 15);

        g2.setFont(new Font("Arial", Font.PLAIN, 15));
        g2.setColor(new Color(200, 200, 200));
        String hint = "Nhấp chuột vào thẻ bài hoặc bấm phím 1, 2, 3 để chọn";
        int hintW = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, (SCREEN_WIDTH - hintW) / 2, (SCREEN_HEIGHT - 340) / 2 + 15);

        // Vẽ 3 thẻ bài
        for (int i = 0; i < activeUpgradeChoices.size(); i++) {
            UpgradeOption opt = activeUpgradeChoices.get(i);
            Rectangle rect = upgradeCardBounds[i];
            boolean hovered = (i == hoveredCardIndex);

            // Nền thẻ
            if (hovered) {
                g2.setColor(new Color(35, 45, 75));
            } else {
                g2.setColor(new Color(25, 30, 48));
            }
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 14, 14);

            // Viền thẻ
            g2.setColor(hovered ? Color.YELLOW : new Color(70, 80, 120));
            g2.setStroke(new BasicStroke(hovered ? 3.0f : 1.5f));
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 14, 14);
            g2.setStroke(new BasicStroke(1.0f));

            // Số thứ tự phím tắt [1], [2], [3]
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 13));
            g2.drawString("[" + (i + 1) + "]", rect.x + 15, rect.y + 28);

            // Nhãn thể loại thẻ
            g2.setColor(opt.getTagColor());
            g2.fillRoundRect(rect.x + rect.width - 95, rect.y + 14, 80, 20, 8, 8);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.drawString(opt.getTag(), rect.x + rect.width - 85, rect.y + 28);

            // Tên thẻ nâng cấp
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            drawWrappedString(g2, opt.getTitle(), rect.x + 15, rect.y + 70, rect.width - 30);

            // Đường phân cách
            g2.setColor(new Color(60, 70, 95));
            g2.drawLine(rect.x + 15, rect.y + 130, rect.x + rect.width - 15, rect.y + 130);

            // Mô tả chi tiết hiệu ứng
            g2.setColor(new Color(200, 210, 230));
            g2.setFont(new Font("Arial", Font.PLAIN, 14));
            drawWrappedString(g2, opt.getDescription(), rect.x + 15, rect.y + 160, rect.width - 30);
        }
    }

    /**
     * Vẽ màn hình Game Over
     */
    private void drawGameOverScreen(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g2.setColor(new Color(255, 50, 50));
        g2.setFont(new Font("Arial", Font.BOLD, 50));
        String overText = "BẠN ĐÃ TỬ NẠN!";
        int tw = g2.getFontMetrics().stringWidth(overText);
        g2.drawString(overText, (SCREEN_WIDTH - tw) / 2, SCREEN_HEIGHT / 2 - 80);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        int min = elapsedSeconds / 60;
        int sec = elapsedSeconds % 60;
        String stats1 = String.format("Thời gian sinh tồn: %02d phút %02d giây", min, sec);
        String stats2 = "Số quái vật đã tiêu diệt: " + player.getKillCount();
        String stats3 = "Cấp độ đạt được: Cấp " + player.getLevel();

        g2.drawString(stats1, (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(stats1)) / 2, SCREEN_HEIGHT / 2 - 10);
        g2.drawString(stats2, (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(stats2)) / 2, SCREEN_HEIGHT / 2 + 25);
        g2.drawString(stats3, (SCREEN_WIDTH - g2.getFontMetrics().stringWidth(stats3)) / 2, SCREEN_HEIGHT / 2 + 60);

        // Nút chơi lại
        g2.setColor(new Color(70, 180, 255));
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        String restartText = ">> BẤM PHÍM SPACE HOẶC NHẤP CHUỘT ĐỂ CHƠI LẠI <<";
        int rw = g2.getFontMetrics().stringWidth(restartText);
        g2.drawString(restartText, (SCREEN_WIDTH - rw) / 2, SCREEN_HEIGHT / 2 + 130);
    }

    /**
     * Hỗ trợ vẽ chuỗi văn bản tự động xuống dòng đẹp mắt
     */
    private void drawWrappedString(Graphics2D g2, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        int currentY = y;

        for (String word : words) {
            if (fm.stringWidth(currentLine + word) < maxWidth) {
                currentLine.append(word).append(" ");
            } else {
                g2.drawString(currentLine.toString(), x, currentY);
                currentLine = new StringBuilder(word + " ");
                currentY += fm.getHeight() + 2;
            }
        }
        if (currentLine.length() > 0) {
            g2.drawString(currentLine.toString(), x, currentY);
        }
    }

    // ================= Xử lý Sự kiện Bàn phím =================
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        if (state == GameState.PLAYING) {
            if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) player.setUp(true);
            if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) player.setDown(true);
            if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) player.setLeft(true);
            if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) player.setRight(true);
        } else if (state == GameState.LEVEL_UP) {
            if (code == KeyEvent.VK_1) selectUpgrade(0);
            if (code == KeyEvent.VK_2) selectUpgrade(1);
            if (code == KeyEvent.VK_3) selectUpgrade(2);
        } else if (state == GameState.GAME_OVER) {
            if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                initGame();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) player.setUp(false);
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) player.setDown(false);
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) player.setLeft(false);
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) player.setRight(false);
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    // ================= Xử lý Sự kiện Chuột =================
    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = e.getPoint();
        if (state == GameState.LEVEL_UP) {
            for (int i = 0; i < activeUpgradeChoices.size(); i++) {
                if (upgradeCardBounds[i] != null && upgradeCardBounds[i].contains(p)) {
                    selectUpgrade(i);
                    break;
                }
            }
        } else if (state == GameState.GAME_OVER) {
            initGame();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Point p = e.getPoint();
        if (state == GameState.LEVEL_UP) {
            hoveredCardIndex = -1;
            for (int i = 0; i < activeUpgradeChoices.size(); i++) {
                if (upgradeCardBounds[i] != null && upgradeCardBounds[i].contains(p)) {
                    hoveredCardIndex = i;
                    break;
                }
            }
        }
    }

    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {}
}
