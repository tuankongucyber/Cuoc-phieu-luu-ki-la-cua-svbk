import java.awt.Color;
/**
 * Layered danmaku schedules. Design references and pinned source revisions:
 * docs/BOSS_REFERENCES.md (Touhou Tales of Danmaku / BulletML).
 * Independent Java emitters; no engine or assets imported.
 */
public final class BossPatterns {
    private static final Color PINK = new Color(255, 115, 170), BLUE = new Color(115, 190, 255);
    private static final Color GOLD = new Color(255, 195, 90), PURPLE = new Color(195, 135, 255);
    private BossPatterns() {}
    public static int cycle(Boss boss) {
        return boss.encounter == 0 ? new int[]{360, 192, 300}[boss.phase] : new int[]{360, 420, 210}[boss.phase];
    }
    public static void update(GameWorld w, Boss b, int t) {
        int local = t % cycle(b), lead = w.difficulty.bossWarning, beat = local - lead;
        if (beat < 0) return;
        switch (b.encounter * 3 + b.phase) {
            case 0: icicleCross(w, b, t, beat); break;
            case 1: orbitLattice(w, b, t, beat); break;
            case 2: freeze(w, b, t, beat); break;
            case 3: scarletThreads(w, b, t, beat); break;
            case 4: clockKnives(w, b, t, beat); break;
            case 5: movingBoundary(w, b, t, beat); break;
            default: throw new IllegalArgumentException("Boss phase");
        }
    }
    private static int count(GameWorld w, int easy, int normal, int hard) {
        return w.difficulty == Difficulty.EASY ? easy : w.difficulty == Difficulty.HARD ? hard : normal;
    }
    private static void icicleCross(GameWorld w, Boss b, int t, int beat) {
        // Crossing side fans, then central aimed pressure: change the required dodge within one spell.
        int round = t / cycle(b);
        if (beat < 180 && beat % 36 == 0) {
            double sweep = (beat / 36 - 2) * .055;
            for (int side : new int[]{-1, 1}) {
                double x = 500 + side * 310, y = 155 + (beat / 36 % 3) * 24;
                double angle = Math.PI / 2 + side * (.48 + sweep);
                for (int row = 0; row < 2; row++)
                    fan(w, b, x, y, angle, count(w, 4, 6, 8), .14, 1.85 + row * .45, BLUE);
            }
        }
        if (beat >= 195 && beat <= 255 && beat % 20 == 15)
            fan(w, b, b.getCenterX(), b.getCenterY(), b.aim, count(w, 5, 9, 11), .105, 3.0, PINK);
        if (beat == 275)
            ring(w, b, b.getCenterX(), b.getCenterY(), count(w, 32, 44, 52), round * .17,
                Math.PI / 2 + Math.sin(round) * .5, .23, 2.1, 0, GOLD);
    }
    private static void orbitLattice(GameWorld w, Boss b, int t, int beat) {
        if (beat % 54 != 0) return;
        double gap = Math.PI / 2 + Math.sin(t * .004) * .65; // Fixed world motion: never follows player aim.
        for (int side : new int[]{-1, 1}) {
            double x = b.getCenterX() + side * 100, y = b.getCenterY() + Math.sin(t * .025) * side * 35;
            ring(w, b, x, y, count(w, 28, 36, 44), t * .013 * side, gap + side * .22,
                w.difficulty == Difficulty.EASY ? .24 : .16, 2.0, side * .0025, BLUE);
        }
    }
    private static void freeze(GameWorld w, Boss b, int t, int beat) {
        if (beat <= 24 && beat % 12 == 0) {
            int n = count(w, 20, 28, 36);
            for (int i = 0; i < n; i++) {
                Projectile p = shot(w, b, b.getCenterX(), b.getCenterY(),
                    i * Math.PI * 2 / n + beat * .025 + t * .002, 1.5 + beat * .025, BLUE);
                if (p != null) {
                    p.freezeAt = 55; p.aimAt = 100; p.releaseAt = 128;
                    p.releaseSpeed = 2.8 * w.difficulty.bulletSpeed();
                }
            }
        }
        if (beat >= 150 && beat % 45 == 0)
            fan(w, b, b.getCenterX(), b.getCenterY(), b.aim, count(w, 3, 5, 7), .16, 2.7, PINK);
    }
    private static void scarletThreads(GameWorld w, Boss b, int t, int beat) {
        // Scarlet Shoot-inspired strings: faster heads and slower beads separate into curved lanes.
        if (beat < 210 && beat % 42 == 0) {
            int n = count(w, 18, 24, 30);
            double rotation = t / cycle(b) * .27 + beat * .009;
            for (int i = 0; i < n; i++) for (int bead = 0; bead < 5; bead++) {
                double angle = i * Math.PI * 2 / n + rotation;
                Projectile p = shot(w, b, b.getCenterX(), b.getCenterY(), angle,
                    1.45 + bead * .34, bead == 4 ? GOLD : PINK);
                if (p != null) p.turnRate = (i % 2 == 0 ? 1 : -1) * .0015;
            }
        }
        if (beat >= 225 && beat < 285 && beat % 20 == 5)
            fan(w, b, b.getCenterX(), b.getCenterY(), b.aim, count(w, 5, 7, 9), .13, 3.3, BLUE);
    }
    private static void clockKnives(GameWorld w, Boss b, int t, int beat) {
        // An arena clock is laid out first; all hands lock and launch on the same global beat.
        if (beat <= 72 && beat % 24 == 0) {
            int n = count(w, 24, 32, 40), layer = beat / 24;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + layer * .075 + t / cycle(b) * .18;
                double x = 500 + Math.cos(a) * (210 + layer * 28);
                double y = 340 + Math.sin(a) * (150 + layer * 22);
                Projectile p = shot(w, b, x, y, a + Math.PI + (layer % 2 == 0 ? .5 : -.5), .01, layer % 2 == 0 ? PURPLE : BLUE);
                if (p != null) {
                    p.knife = true; p.freezeAt = 1; p.aimAt = -1;
                    p.releaseAt = 150 - beat; p.releaseSpeed = 2.7 * w.difficulty.bulletSpeed();
                }
            }
        }
        if (beat >= 190 && beat <= 286 && beat % 24 == 22) {
            for (int i = 0; i < count(w, 7, 11, 15); i++) {
                Projectile p = shot(w, b, b.getCenterX(), b.getCenterY(), b.aim + (i - count(w, 7, 11, 15) / 2) * .10, 3.2, GOLD);
                if (p != null) p.knife = true;
            }
        }
    }
    private static void movingBoundary(GameWorld w, Boss b, int t, int beat) {
        if (beat % 72 == 0) {
            double gapX = wallGap(t);
            double width = w.difficulty == Difficulty.EASY ? 140 : w.difficulty == Difficulty.HARD ? 100 : 120;
            int spacing = w.difficulty == Difficulty.EASY ? 28 : w.difficulty == Difficulty.HARD ? 18 : 23;
            for (int x = 40; x <= 960; x += spacing)
                if (Math.abs(x - gapX) > width / 2) shot(w, b, x, 115, Math.PI / 2, 2.1, BLUE);
        }
        if (beat % 12 == 0)
            spokes(w, b, b.getCenterX(), b.getCenterY(), count(w, 2, 3, 4), -t * .047, 2.0, .002, GOLD);
        if (beat % 96 == 44)
            fan(w, b, b.getCenterX(), b.getCenterY(), b.aim, count(w, 3, 5, 7), .14, 2.8, PINK);
    }
    public static double wallGap(int t) { return 500 + Math.sin(Math.floor(t / 72.0) * .55) * 220; }
    public static int wallWarningUntil(int t, int lead) {
        int local = t % 210, beat = local - lead;
        if (beat < 0) return -beat;
        int until = 72 - beat % 72;
        return until <= lead && local + until < 210 ? until : -1;
    }
    private static void fan(GameWorld w, Boss b, double x, double y, double angle, int n, double spacing, double speed, Color color) {
        for (int i = 0; i < n; i++) shot(w, b, x, y, angle + (i - (n - 1) / 2.0) * spacing, speed, color);
    }
    private static void spokes(GameWorld w, Boss b, double x, double y, int n, double angle, double speed, double curve, Color color) {
        for (int i = 0; i < n; i++) {
            Projectile p = shot(w, b, x, y, angle + i * Math.PI * 2 / n, speed, color);
            if (p != null) p.turnRate = curve;
        }
    }
    private static void ring(GameWorld w, Boss b, double x, double y, int n, double angle, double gapAngle,
                             double gapWidth, double speed, double curve, Color color) {
        for (int i = 0; i < n; i++) {
            double a = angle + i * Math.PI * 2 / n;
            if (Math.abs(Math.atan2(Math.sin(a - gapAngle), Math.cos(a - gapAngle))) <= gapWidth) continue;
            Projectile p = shot(w, b, x, y, a, speed, color);
            if (p != null) p.turnRate = curve;
        }
    }
    private static Projectile shot(GameWorld w, Boss b, double x, double y, double angle, double speed, Color color) {
        if (Math.hypot(x - w.player.getCenterX(), y - w.player.getCenterY()) < 42) return null;
        double v = speed * w.difficulty.bulletSpeed();
        Projectile p = new Projectile(x, y, Math.cos(angle) * v, Math.sin(angle) * v,
            12 * w.difficulty.enemyDamage, 4, color, 450, true);
        p.sourceLabel = b.title() + " • đạn"; w.addProjectile(p); return p;
    }
}
