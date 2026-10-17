import java.awt.Color;

/**
 * Lớp đại diện cho một thẻ nâng cấp xuất hiện khi người chơi thăng cấp (Level Up).
 */
public class UpgradeOption {
    private String title;
    private String description;
    private String tag;
    private Color tagColor;
    private Runnable action;

    public UpgradeOption(String title, String description, String tag, Color tagColor, Runnable action) {
        this.title = title;
        this.description = description;
        this.tag = tag;
        this.tagColor = tagColor;
        this.action = action;
    }

    public void apply() {
        if (action != null) {
            action.run();
        }
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTag() { return tag; }
    public Color getTagColor() { return tagColor; }
}
