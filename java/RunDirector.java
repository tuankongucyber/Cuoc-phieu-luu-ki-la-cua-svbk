/** Separate clocks for field waves, announcement, and individual boss phases. */
public final class RunDirector {
    public enum Segment { FIELD, WARNING, BOSS, COMPLETE }
    Segment segment = Segment.FIELD;
    int fieldTicks, warningTicks, phaseTicks, phase, completedBosses, nextSpawn;
    int hitsAtPhaseStart, bombsAtPhaseStart;
    public static final int MID_BOSS_AT = 5 * 60 * 60, FINAL_BOSS_AT = 9 * 60 * 60;
    void startBossTest(GameWorld world, int encounter) {
        segment = Segment.BOSS; completedBosses = encounter; phase = 0;
        fieldTicks = encounter == 0 ? MID_BOSS_AT : FINAL_BOSS_AT;
        world.enterBoss(encounter); startPhase(world);
    }
    public void step(GameWorld world) {
        if (segment == Segment.FIELD) {
            fieldTicks++;
            if (fieldTicks >= (completedBosses == 0 ? MID_BOSS_AT : FINAL_BOSS_AT)) {
                segment = Segment.WARNING; warningTicks = 120; world.input.clear(); return;
            }
            if (--nextSpawn <= 0) {
                world.spawnFieldEnemy();
                int interval = Math.max(world.difficulty.minimumInterval,
                    world.difficulty.spawnInterval - (fieldTicks / 600) * 3);
                nextSpawn = interval;
            }
        } else if (segment == Segment.WARNING) {
            if (--warningTicks <= 0) {
                world.enterBoss(completedBosses);
                segment = Segment.BOSS; phase = 0; startPhase(world);
            }
        } else if (segment == Segment.BOSS) {
            phaseTicks++;
            if (world.th06 != null) { world.th06.tick(world); return; }
            if (world.boss.isDead()) {
                world.score += 1000;
                if (world.player.hitsTaken == hitsAtPhaseStart && world.player.bombsUsed == bombsAtPhaseStart) {
                    world.captures++; world.score += 2500;
                    world.notice("CAPTURE! +2500", 120);
                } else world.notice("PHÁ SPELL CARD", 90);
                world.clearHostileBullets();
                if (++phase < 3) startPhase(world);
                else {
                    completedBosses++;
                    if (completedBosses == 2 || world.isBossTest()) {
                        segment = Segment.COMPLETE; world.state = GameWorld.State.VICTORY; world.input.clear();
                    } else {
                        segment = Segment.FIELD;
                        world.leaveBoss(); world.player.heal(25);
                        world.player.bombs = Math.min(3, world.player.bombs + 1);
                        world.player.gainExp(80);
                        world.notice("VƯỢT BOSS • +1 BOMB • +25 HP • +80 EXP", 180);
                        nextSpawn = 90;
                    }
                }
            }
        }
    }
    private void startPhase(GameWorld world) {
        phaseTicks = 0;
        world.boss.beginPhase(phase);
        world.projectiles.clear();
        world.effects.clear();
        hitsAtPhaseStart = world.player.hitsTaken; bombsAtPhaseStart = world.player.bombsUsed;
    }
    public Segment getSegment() { return segment; }
    public int getFieldTicks() { return fieldTicks; }
}
