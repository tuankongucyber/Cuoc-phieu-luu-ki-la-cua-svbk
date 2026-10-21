import java.util.function.BooleanSupplier;
/** Stable IDs are also used by seeded tests and automated playthroughs. */
public final class UpgradeChoice {
    public final String id, title, description;
    public final int currentLevel, maxLevel;
    private final Runnable effect;
    private final BooleanSupplier valid;
    public UpgradeChoice(String id, String title, String description, BooleanSupplier valid, Runnable effect) {
        this(id, title, description, -1, -1, valid, effect);
    }
    public UpgradeChoice(String id, String title, String description, int currentLevel, int maxLevel, BooleanSupplier valid, Runnable effect) {
        this.id = id; this.title = title; this.description = description; this.valid = valid; this.effect = effect;
        this.currentLevel = currentLevel; this.maxLevel = maxLevel;
    }
    public String progressLabel() { return maxLevel <= 0 ? "" : currentLevel + "/" + maxLevel + " → " + (currentLevel + 1) + "/" + maxLevel; }
    public boolean isValid() { return valid.getAsBoolean(); }
    public void apply() { if (!isValid()) throw new IllegalStateException("Stale upgrade"); effect.run(); }
}
