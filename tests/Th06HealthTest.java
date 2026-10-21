/** Real-script regression for phase-local health and short inherited Spell Card pools. */
public final class Th06HealthTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Th06Runtime.Actor actor(GameWorld world) { return (Th06Runtime.Actor) world.boss; }
    static void awaitDamageable(GameWorld world, boolean spell) {
        for (int i = 0; i < 1800; i++) {
            world.th06.tick(world);
            Th06Runtime.Actor boss = actor(world);
            if (boss.originalBoss && boss.damageable && boss.getHp() > 0 && (!spell || !world.th06.observedSpellIds.isEmpty())) return;
        }
        throw new AssertionError("No damageable phase: " + world.originalPractice + " " + world.th06.failure);
    }
    public static void main(String[] args) throws Exception {
        for (Difficulty difficulty : Difficulty.values()) for (Th06BossId id : new Th06BossId[]{Th06BossId.RUMIA, Th06BossId.CIRNO}) {
            GameWorld world = GameWorld.originalBossTest(20261021, difficulty, id, 0);
            try {
                awaitDamageable(world, false);
                Th06Runtime.Actor boss = actor(world);
                double scale = difficulty.enemyHealth;
                check(boss.getMaxHp() >= 6000 * scale - .01, "nonspell minimum HP budget");
                check(Math.abs(boss.getHp() - boss.getMaxHp()) < .01, "first phase starts with a full bar");
                // An exact hit to the callback threshold must advance, not strand a 0% bar.
                for (int phase = 0; world.th06.observedSpellIds.isEmpty() && phase < 10; phase++) {
                    actor(world).takeDamage(actor(world).getHp());
                    awaitDamageable(world, false);
                }
                check(!world.th06.observedSpellIds.isEmpty(), "exact-threshold damage advances to a spell");
                boss = actor(world);
                check(boss.getMaxHp() >= 6000 * scale - .01, "inherited spell gets a full HP budget");
                check(Math.abs(boss.getHp() - boss.getMaxHp()) < .01, "spell starts with its own full bar");
                int spells = world.th06.observedSpellIds.size();
                double initial = boss.getHp();
                for (int i = 0; i < 300; i++) {
                    boss.takeDamage(10 * scale); world.th06.tick(world);
                }
                check(world.th06.observedSpellIds.size() == spells && world.state == GameWorld.State.PLAYING,
                    "five seconds of 600 DPS does not erase the spell");
                check(Math.abs(actor(world).getHp() - (initial - 3000 * scale)) < 60 * scale,
                    "effective damage and displayed phase HP agree");
                System.out.printf("%s %s spell HP %.0f -> %.0f after 5s, max %.0f%n", id.name(), difficulty.name(), initial, actor(world).getHp(), actor(world).getMaxHp());
            } finally { world.dispose(); }
        }
        for (Th06BossId id : new Th06BossId[]{Th06BossId.RUMIA, Th06BossId.CIRNO}) defaultBuild(id);
    }
    static void defaultBuild(Th06BossId id) throws Exception {
        GameWorld world = GameWorld.originalBossTest(20261021, Difficulty.NORMAL, id, 0);
        try {
            world.player.invulnerability = 1000000;
            int spellStart = -1, firstSpellCount = 0;
            for (int frame = 0; frame < 36000; frame++) {
                world.tick();
                int count = world.th06.observedSpellIds.size();
                if (spellStart < 0 && count > 0) { spellStart = frame; firstSpellCount = count; }
                if (spellStart >= 0 && (world.captures > 0 || count > firstSpellCount || world.state == GameWorld.State.VICTORY)) {
                    int duration = frame - spellStart;
                    check(duration >= 600, "practice build must have time to experience the first spell");
                    System.out.printf("%s NORMAL practice build: first spell %.1fs%n", id.name(), duration / 60.0);
                    return;
                }
                check(world.state == GameWorld.State.PLAYING, "practice simulation remains active");
            }
            throw new AssertionError("Practice build did not finish first spell: " + id);
        } finally { world.dispose(); }
    }
}
