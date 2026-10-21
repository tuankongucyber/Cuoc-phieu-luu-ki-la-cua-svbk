public enum PassiveId {
    DAMAGE("Sức mạnh", "+10% sát thương"),
    COOLDOWN("Hồi chiêu", "-8% thời gian hồi"),
    AREA("Phạm vi", "+10% phạm vi"),
    AMOUNT("Đa đạn", "+1 đạn / mục tiêu"),
    SPEED("Nhanh nhẹn", "+5% tốc độ"),
    MAGNET("Thu thập", "+20% bán kính hút");
    public final String label, description;
    PassiveId(String label, String description) { this.label = label; this.description = description; }
}
