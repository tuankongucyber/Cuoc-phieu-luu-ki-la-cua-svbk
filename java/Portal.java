import java.awt.*;

public class Portal {
    private double x, y;
    private int width = 50;
    private int height = 50;
    private double angle = 0;
    private Color outerColor = new Color(100, 200, 255, 180);
    private Color innerColor = new Color(150, 50, 255, 120);
    private String label = "BOSS PORTAL";

    public Portal(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Portal(double x, double y, Color outer, Color inner, String label) {
        this.x = x;
        this.y = y;
        this.outerColor = outer;
        this.innerColor = inner;
        this.label = label;
    }

    public void update() {
        angle += 0.05;
        if (angle > Math.PI * 2) {
            angle = 0;
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }

    public void draw(Graphics2D g2) {
        Graphics2D g = (Graphics2D) g2.create();
        
        g.translate(x + width / 2.0, y + height / 2.0);
        g.rotate(angle);

        g.setColor(outerColor);
        g.setStroke(new BasicStroke(3f));
        g.drawOval(-width / 2, -height / 2, width, height);

        g.setColor(innerColor);
        g.fillOval(-width / 3, -height / 3, width * 2 / 3, height * 2 / 3);

        g.dispose();

        // Vẽ nhãn chữ hướng dẫn trên đầu cổng
        if (label != null && !label.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.setColor(Color.YELLOW);
            int textW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, (int) (x + width / 2.0 - textW / 2.0), (int) y - 8);
        }
    }

    public double getX() { return x; }
    public double getY() { return y; }
}