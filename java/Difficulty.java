/** Các hệ số được áp dụng một lần khi sinh quái, cố định trong mỗi ván. */
public enum Difficulty {
    EASY("Dễ", 85, 22, 0.8, 0.75, 0.7, 1, 40, 45, 300, 1),
    NORMAL("Thường", 65, 15, 1.0, 1.0, 1.0, 2, 25, 30, 220, 1),
    HARD("Khó", 45, 10, 1.2, 1.4, 1.35, 3, 16, 22, 150, 2);

    final String label;
    final int spawnInterval, minimumInterval;
    final double enemySpeed, enemyHealth, enemyDamage;
    final int ninjaDodges, ninjaCooldown, bossWarning, bossDashCooldown, bossDashCount;

    Difficulty(String label, int spawnInterval, int minimumInterval, double enemySpeed,
               double enemyHealth, double enemyDamage, int ninjaDodges, int ninjaCooldown,
               int bossWarning, int bossDashCooldown, int bossDashCount) {
        this.label = label;
        this.spawnInterval = spawnInterval;
        this.minimumInterval = minimumInterval;
        this.enemySpeed = enemySpeed;
        this.enemyHealth = enemyHealth;
        this.enemyDamage = enemyDamage;
        this.ninjaDodges = ninjaDodges;
        this.ninjaCooldown = ninjaCooldown;
        this.bossWarning = bossWarning;
        this.bossDashCooldown = bossDashCooldown;
        this.bossDashCount = bossDashCount;
    }

    @Override public String toString() { return label; }
}
