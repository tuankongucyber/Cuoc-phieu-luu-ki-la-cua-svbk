import java.awt.Color;
import java.util.*;

/** Authoritative deterministic simulation, independent of Swing and wall-clock time. */
public final class GameWorld {
    public enum State { PLAYING, LEVEL_UP, VICTORY, DEFEAT }
    public final long seed;
    public final Difficulty difficulty;
    public final Random random;
    public final Player player = new Player(483, 358);
    public final InputState input = new InputState();
    public final RunDirector director = new RunDirector();
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<DropItem> drops = new ArrayList<>();
    public final List<DamageText> damageTexts = new ArrayList<>();
    public final List<Effect> effects = new ArrayList<>();
    public final EnumMap<WeaponId, Double> damageByWeapon = new EnumMap<>(WeaponId.class);
    public final SpatialGrid<Enemy> enemyGrid = new SpatialGrid<>();
    private final SpatialGrid<Projectile> friendlyGrid = new SpatialGrid<>();
    private final List<Enemy> nearbyScratch = new ArrayList<>(), collisionScratch = new ArrayList<>();
    private final List<Projectile> bulletScratch = new ArrayList<>();
    public State state = State.PLAYING;
    public boolean paused;
    public long ticks, score;
    public int grazes, captures, rerolls = 2, grazeRemainder;
    private int grazeExpThisSecond;
    public int noticeTicks, bombFlash;
    public String notice = "", lastDamageSource = "";
    public List<UpgradeChoice> upgradeChoices = Collections.emptyList();
    public Boss boss;
    public Th06Runtime th06;
    public Th06BossId originalPractice;
    public int originalRoute;
    int practiceEncounter = -1;
    private double returnX, returnY;

    public GameWorld(long seed, Difficulty difficulty) {
        this.seed = seed; this.difficulty = difficulty; random = new Random(seed);
        player.addWeapon(new MagicWand());
    }
    public static GameWorld bossTest(long seed, Difficulty difficulty, int encounter) {
        if (encounter < 0 || encounter > 1) throw new IllegalArgumentException("Unknown boss");
        GameWorld world = new GameWorld(seed, difficulty);
        world.practiceEncounter = encounter;
        world.player.addWeapon(new OrbitBlade());
        world.player.addWeapon(new PiercingShot());
        world.player.addWeapon(new ChainLightning());
        for (Weapon weapon : world.player.getWeapons()) while (weapon.getLevel() < 4) weapon.upgrade();
        for (int i = 0; i < 2; i++) {
            world.player.upgrade(PassiveId.DAMAGE); world.player.upgrade(PassiveId.COOLDOWN);
        }
        world.player.upgrade(PassiveId.AREA); world.player.upgrade(PassiveId.MAGNET);
        world.player.bombs = 3;
        world.director.startBossTest(world, encounter);
        return world;
    }
    public static GameWorld originalBossTest(long seed, Difficulty difficulty, Th06BossId identity, int route) throws java.io.IOException {
        GameWorld world = new GameWorld(seed, difficulty);
        world.practiceEncounter = 0; world.originalPractice = identity; world.originalRoute = route;
        world.player.addWeapon(new OrbitBlade()); world.player.addWeapon(new PiercingShot()); world.player.addWeapon(new ChainLightning());
        for (Weapon weapon : world.player.getWeapons()) while (weapon.getLevel() < 4) weapon.upgrade();
        for (int i = 0; i < 2; i++) { world.player.upgrade(PassiveId.DAMAGE); world.player.upgrade(PassiveId.COOLDOWN); }
        world.player.upgrade(PassiveId.AREA); world.player.upgrade(PassiveId.MAGNET); world.player.bombs = 3;
        world.director.segment = RunDirector.Segment.BOSS;
        world.enterOriginalBoss(identity, route);
        return world;
    }
    void enterOriginalBoss(Th06BossId identity, int route) throws java.io.IOException {
        th06 = new Th06Runtime(identity, difficulty, seed, route);
        player.x = 500 - player.width / 2.0; player.y = Th06Runtime.TOP + 384 * Th06Runtime.SCALE - player.height / 2.0;
        player.dashRemaining = 0; syncPlayerPosition();
        boss = new Th06Runtime.Actor(th06, 256); enemies.clear();
    }
    public void dispose() { if (th06 != null) { th06.close(); th06 = null; } }
    void finishOriginalBoss() {
        if (isBossTest() || ++director.completedBosses == 2) {
            director.segment = RunDirector.Segment.COMPLETE; state = State.VICTORY; input.clear();
            if (th06 != null) th06.close();
        } else {
            director.segment = RunDirector.Segment.FIELD; leaveBoss(); player.heal(25);
            player.bombs = Math.min(3, player.bombs + 1); player.gainExp(80); director.nextSpawn = 90;
            notice("VƯỢT BOSS • +1 BOMB • +25 HP • +80 EXP", 180);
        }
    }
    public boolean isBossTest() { return practiceEncounter >= 0; }
    public boolean inBossRoom() { return director.segment == RunDirector.Segment.BOSS; }
    public double cameraX() { return inBossRoom() ? 0 : player.getCenterX() - 500; }
    public double cameraY() { return inBossRoom() ? 0 : player.getCenterY() - 375; }
    public void setPaused(boolean value) { paused = value; input.clear(); }
    public void tick() {
        if (paused || state != State.PLAYING) return;
        ticks++;
        if (ticks % 60 == 1) grazeExpThisSecond = 0;
        if (noticeTicks > 0) noticeTicks--;
        if (bombFlash > 0) bombFlash--;
        for (Iterator<Effect> i = effects.iterator(); i.hasNext();) if (--i.next().life <= 0) i.remove();
        damageTexts.removeIf(text -> { text.update(); return text.isDead(); });
        player.step(input);
        if (inBossRoom()) {
            if (th06 != null) player.clampToBounds(Th06Runtime.ARENA.x, Th06Runtime.ARENA.y, Th06Runtime.ARENA.getMaxX(), Th06Runtime.ARENA.getMaxY());
            else player.clampToBounds(24, 105, 976, 726);
        }
        if (input.bombPressed) {
            if (player.useBomb()) {
                clearHostileBullets(); bombFlash = 18;
                for (Enemy enemy : enemies)
                    if (Math.hypot(enemy.getCenterX() - player.getCenterX(), enemy.getCenterY() - player.getCenterY()) < 900)
                        hitEnemy(enemy, 450, null);
            }
            input.bombPressed = false;
        }
        director.step(this);
        if (state != State.PLAYING) return;
        // Ninja queries only nearby friendly bullets, once per enemy per simulation tick.
        friendlyGrid.clear();
        for (Projectile p : projectiles) if (!p.hostile && !p.dead)
            friendlyGrid.insert(p, p.x - 4, p.y - 4, p.x + 4, p.y + 4);
        for (Enemy enemy : enemies) {
            if (enemy instanceof Th06Runtime.Actor) { enemy.update(); continue; }
            enemy.previousCenterX = enemy.getCenterX(); enemy.previousCenterY = enemy.getCenterY();
            if (enemy.isDead()) continue;
            enemy.update();
            if (enemy instanceof Boss) ((Boss) enemy).fight(this);
            else {
                enemy.moveTowards(player);
                if (enemy instanceof NinjaZombie) {
                    friendlyGrid.query(enemy.x - 65, enemy.y - 65, enemy.x + 90, enemy.y + 90, bulletScratch);
                    ((NinjaZombie) enemy).checkAndDodge(bulletScratch);
                }
                if (enemy instanceof Shooter) ((Shooter) enemy).attack(this);
            }
        }
        rebuildEnemyGrid();
        for (Weapon weapon : player.getWeapons()) weapon.update(this);
        // Weapon pushback changes positions, so refresh the broad phase before swept collision.
        rebuildEnemyGrid();
        for (Enemy enemy : enemies) if (!enemy.isDead()) {
            if (Collision.swept(player.previousCenterX - enemy.previousCenterX, player.previousCenterY - enemy.previousCenterY,
                player.getCenterX() - enemy.getCenterX(), player.getCenterY() - enemy.getCenterY(), Player.BODY_RADIUS + enemy.bodyRadius()))
                hurtPlayer(enemy.getContactDamage(), enemy instanceof Boss ? ((Boss) enemy).title() + " • thân Boss" : enemy.getClass().getSimpleName() + " • va chạm");
        }
        for (Projectile p : projectiles) {
            if (p.dead) continue;
            p.update(this);
            if (p.dead) continue;
            if (p.hostile && (!(p instanceof Th06Runtime.Bullet) || ((Th06Runtime.Bullet) p).active)) updateHostileCollision(p);
            else if (!p.hostile) updateFriendlyCollision(p);
        }
        if (th06 != null) th06.collideBeams(this);
        projectiles.removeIf(p -> p.dead || Math.abs(p.x - player.getCenterX()) > 1800 || Math.abs(p.y - player.getCenterY()) > 1400);
        for (Iterator<Enemy> iterator = enemies.iterator(); iterator.hasNext();) {
            Enemy enemy = iterator.next();
            if (enemy instanceof Boss) continue; // RunDirector owns HP-based phase transitions.
            if (enemy.isDead()) {
                player.incrementKillCount(); score += enemy.getExpReward() * 10L;
                addExperienceDrop(enemy.getCenterX(), enemy.getCenterY(), enemy.getExpReward());
                if (random.nextDouble() < 0.018 && drops.size() < 2000) drops.add(new HealthPotion(enemy.getCenterX(), enemy.getCenterY()));
                iterator.remove();
            } else if (Math.hypot(enemy.getCenterX() - player.getCenterX(), enemy.getCenterY() - player.getCenterY()) > 1800) iterator.remove();
        }
        for (Iterator<DropItem> iterator = drops.iterator(); iterator.hasNext();) {
            DropItem drop = iterator.next(); drop.update(player);
            if (drop.isCollected()) iterator.remove();
        }
        if (player.isDead()) { state = State.DEFEAT; input.clear(); if (th06 != null) th06.close(); }
        else checkLevelUp();
    }
    public void rebuildEnemyGrid() {
        enemyGrid.clear();
        for (Enemy enemy : enemies) if (!enemy.isDead()) {
            double r = enemy.bodyRadius();
            enemyGrid.insert(enemy, Math.min(enemy.previousCenterX, enemy.getCenterX()) - r,
                Math.min(enemy.previousCenterY, enemy.getCenterY()) - r,
                Math.max(enemy.previousCenterX, enemy.getCenterX()) + r, Math.max(enemy.previousCenterY, enemy.getCenterY()) + r);
        }
    }
    public void addEnemy(Enemy enemy) {
        enemy.applyDifficulty(difficulty);
        if (enemy instanceof NinjaZombie) ((NinjaZombie) enemy).setRandom(random);
        enemies.add(enemy);
    }
    public void addProjectile(Projectile projectile) {
        if (projectiles.size() < 6000) projectiles.add(projectile);
    }
    public void clearHostileBullets() { projectiles.removeIf(p -> p.hostile); if (th06 != null) th06.bomb(); }
    public List<Enemy> nearby(double x, double y, double radius) {
        enemyGrid.query(x - radius, y - radius, x + radius, y + radius, nearbyScratch);
        return nearbyScratch;
    }
    public Enemy closestEnemy(double x, double y, double radius, Set<Enemy> excluded) {
        Enemy target = null; double best = radius * radius;
        for (Enemy enemy : nearby(x, y, radius)) if (!enemy.isDead() && (excluded == null || !excluded.contains(enemy))) {
            double dx = enemy.getCenterX() - x, dy = enemy.getCenterY() - y, distance = dx * dx + dy * dy;
            if (distance < best) { target = enemy; best = distance; }
        }
        return target;
    }
    public void hitEnemy(Enemy enemy, double damage, WeaponId source) {
        if (enemy instanceof Th06Runtime.Actor && !((Th06Runtime.Actor) enemy).damageable) return;
        if (enemy.isDead()) return;
        double actual = Math.min(enemy.getHp(), damage);
        enemy.takeDamage(damage);
        if (source != null) damageByWeapon.merge(source, actual, Double::sum);
        if (damageTexts.size() < 24)
            damageTexts.add(new DamageText(enemy.getCenterX(), enemy.y - 8, Integer.toString((int) actual), new Color(200, 210, 220), false));
    }
    void hurtPlayer(double damage, String source) {
        if (player.hit(damage)) {
            lastDamageSource = source;
        }
    }
    private void updateHostileCollision(Projectile p) {
        double ax = p.previousX - player.previousCenterX, ay = p.previousY - player.previousCenterY;
        double bx = p.x - player.getCenterX(), by = p.y - player.getCenterY();
        double distance = Collision.segmentDistanceSquared(ax, ay, bx, by);
        if (distance <= Math.pow(p.radius + Player.BULLET_RADIUS, 2)) {
            hurtPlayer(p.damage, p.sourceLabel); p.dead = true; return;
        }
        if (!p.grazed && player.canGraze() && distance <= Math.pow(p.radius + Player.GRAZE_RADIUS, 2)) {
            p.grazed = true; grazes++; score += 10;
            if (++grazeRemainder >= 10) {
                grazeRemainder -= 10;
                if (grazeExpThisSecond < 2) { player.gainExp(1); grazeExpThisSecond++; }
            }
        }
    }
    private void updateFriendlyCollision(Projectile p) {
        double r = p.radius + 35;
        enemyGrid.query(Math.min(p.previousX, p.x) - r, Math.min(p.previousY, p.y) - r,
            Math.max(p.previousX, p.x) + r, Math.max(p.previousY, p.y) + r, collisionScratch);
        // Sort along the trajectory so piercing is independent of hash-grid traversal order.
        collisionScratch.sort(Comparator.comparingDouble(e -> (e.getCenterX() - p.previousX) * p.vx + (e.getCenterY() - p.previousY) * p.vy));
        for (Enemy enemy : collisionScratch) {
            if (p.dead) break;
            if (!enemy.isDead() && Collision.swept(p.previousX - enemy.previousCenterX, p.previousY - enemy.previousCenterY,
                p.x - enemy.getCenterX(), p.y - enemy.getCenterY(), p.radius + enemy.bodyRadius()) && p.registerHit(enemy))
                hitEnemy(enemy, p.damage, p.source);
        }
    }
    private void addExperienceDrop(double x, double y, int exp) {
        if (drops.size() < 2000) drops.add(new ExpGem(x, y, exp));
        else for (DropItem item : drops) if (item instanceof ExpGem) { ((ExpGem) item).merge(exp); break; }
    }
    public void checkLevelUp() {
        if (isBossTest()) return; // Practice uses a fixed build and never interrupts the fight with upgrade cards.
        if (state == State.PLAYING && !player.isDead() && player.canLevelUp()) {
            state = State.LEVEL_UP; input.clear(); upgradeChoices = BuildSystem.choices(this);
        }
    }
    public void chooseUpgrade(int index) {
        if (paused || state != State.LEVEL_UP || index < 0 || index >= upgradeChoices.size()) return;
        upgradeChoices.get(index).apply();
        player.consumeExpAndLevelUp(); input.clear(); upgradeChoices = Collections.emptyList();
        state = State.PLAYING; checkLevelUp();
    }
    public void reroll() {
        if (!paused && state == State.LEVEL_UP && rerolls > 0) { rerolls--; upgradeChoices = BuildSystem.choices(this); }
    }
    public void spawnFieldEnemy() {
        if (enemies.size() >= 300) return;
        double x = cameraX(), y = cameraY();
        int side = random.nextInt(4);
        double spawnX = side == 2 ? x - 65 : side == 3 ? x + 1065 : x + random.nextInt(1000);
        double spawnY = side == 0 ? y - 65 : side == 1 ? y + 815 : y + random.nextInt(750);
        int roll = random.nextInt(100), seconds = director.fieldTicks / 60;
        Enemy enemy;
        if (seconds < 25 || roll < 28) enemy = new Bat(spawnX, spawnY);
        else if (seconds > 45 && roll < 48) enemy = new Shooter(spawnX, spawnY);
        else if (roll < 68) enemy = new Zombie(spawnX, spawnY);
        else if (roll < 83) enemy = new NinjaZombie(spawnX, spawnY);
        else enemy = new Skeleton(spawnX, spawnY);
        addEnemy(enemy);
    }
    public void enterBoss(int encounter) {
        returnX = player.x; returnY = player.y;
        // The arena transition must not discard earned, uncollected experience.
        for (DropItem drop : drops) if (drop instanceof ExpGem && !drop.isCollected())
            player.gainExp(((ExpGem) drop).getExpValue());
        enemies.clear(); projectiles.clear(); drops.clear(); effects.clear(); damageTexts.clear();
        if (!Boolean.getBoolean("svbk.legacyBosses")) {
            try { enterOriginalBoss(encounter == 0 ? Th06BossId.CIRNO : Th06BossId.REMILIA, 0); return; }
            catch (java.io.IOException error) { throw new IllegalStateException("Không mở được Boss TH06", error); }
        }
        player.x = 483; player.y = 613; player.dashRemaining = 0; syncPlayerPosition();
        boss = new Boss(473, 143, encounter); addEnemy(boss);
    }
    public void leaveBoss() {
        dispose();
        enemies.clear(); projectiles.clear(); drops.clear(); effects.clear(); damageTexts.clear();
        player.x = returnX; player.y = returnY; player.dashRemaining = 0; syncPlayerPosition(); boss = null;
    }
    private void syncPlayerPosition() { player.previousCenterX = player.getCenterX(); player.previousCenterY = player.getCenterY(); input.clear(); }
    public void notice(String text, int duration) { notice = text; noticeTicks = duration; }
    public void addEffect(double x, double y, double x2, double y2, Color color) {
        if (effects.size() < 64) effects.add(new Effect(x, y, x2, y2, color));
    }
    public static final class Effect {
        final double x, y, x2, y2; final Color color; int life = 12;
        Effect(double x, double y, double x2, double y2, Color color) { this.x = x; this.y = y; this.x2 = x2; this.y2 = y2; this.color = color; }
    }
}
