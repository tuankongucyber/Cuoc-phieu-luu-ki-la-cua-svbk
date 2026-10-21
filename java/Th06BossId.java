/** One entry point per intact original encounter. Never composes attacks across bosses. */
public enum Th06BossId {
    RUMIA("Rumia", 1, 10), CIRNO("Cirno", 2, 21), MEILING("Hong Meiling", 3, 17),
    PATCHOULI("Patchouli Knowledge", 4, 26), SAKUYA("Sakuya Izayoi", 5, 21),
    REMILIA("Remilia Scarlet", 6, 15), FLANDRE("Flandre Scarlet • Extra", 7, 31),
    DAIYOUSEI("Daiyousei • giữa màn 2", 2, 20), KOAKUMA("Koakuma • giữa màn 4", 4, 21),
    RUMIA_MID("Rumia • giữa màn 1", 1, 8), MEILING_MID("Meiling • giữa màn 3", 3, 9),
    SAKUYA_MID("Sakuya • giữa màn 5", 5, 12), SAKUYA_STAGE6("Sakuya • giữa màn 6", 6, 8),
    PATCHOULI_EXTRA("Patchouli • giữa màn Extra", 7, 16);
    public final String label;
    public final int stage, entry;
    Th06BossId(String label, int stage, int entry) { this.label = label; this.stage = stage; this.entry = entry; }
    @Override public String toString() { return label; }
}
