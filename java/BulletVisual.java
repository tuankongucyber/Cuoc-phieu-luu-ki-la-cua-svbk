import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Geometry-only bullet art; silhouettes never determine collision or graze radii. */
public enum BulletVisual {
    PELLET(new Ellipse2D.Double(-1, -1, 2, 2)),
    RING(new Ellipse2D.Double(-1, -1, 2, 2)),
    RICE(new Ellipse2D.Double(-1.1, -.42, 2.2, .84)),
    ORB(new Ellipse2D.Double(-1, -1, 2, 2)),
    KUNAI(polygon(1.25,0, -.2,-.52, -.95,-.28, -.65,0, -.95,.28, -.2,.52)),
    SHARD(polygon(1.2,0, -.55,-.55, -.95,0, -.55,.55)),
    BIG_ORB(new Ellipse2D.Double(-1, -1, 2, 2)),
    FIREBALL(polygon(1,0, .55,-.72, -.3,-.8, -1.3,-.35, -.9,0, -1.3,.35, -.3,.8, .55,.72)),
    KNIFE(polygon(1.3,0, .25,-.30, -.55,-.24, -.55,-.43, -.73,-.43, -.73,-.18, -1.05,-.18,
        -1.05,.18, -.73,.18, -.73,.43, -.55,.43, -.55,.24, .25,.30)),
    BEAM(new Rectangle2D.Double(-1.3, -.25, 2.6, .5));

    private final Shape silhouette;
    private static final Color OUTLINE = new Color(14, 10, 26);
    private static final int[] TH06_BASES = {14,30,46,62,78,94,110,118,122,146};
    private static final BulletVisual[] TH06_TYPES = values();
    private record GlyphKey(BulletVisual visual, int color, int halfSize, int facing) {}
    private static final Map<GlyphKey, BufferedImage> GLYPHS = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<GlyphKey, BufferedImage> entry) { return size() > 1024; }
    };
    BulletVisual(Shape silhouette) { this.silhouette = silhouette; }
    private static Shape polygon(double... points) {
        Path2D.Double shape = new Path2D.Double(); shape.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) shape.lineTo(points[i], points[i+1]);
        shape.closePath(); return shape;
    }
    static int th06Type(int sprite) {
        int type = 0;
        while (type < TH06_BASES.length - 1 && sprite >= TH06_BASES[type+1]) type++;
        return type;
    }
    public static BulletVisual fromTh06Sprite(int sprite) { return TH06_TYPES[th06Type(sprite)]; }
    static int th06Color(int sprite) { return Math.floorMod(sprite - TH06_BASES[th06Type(sprite)], 16); }

    public void draw(Graphics2D g, double x, double y, double angle, double radius, Color color) {
        double size = Math.max(this == PELLET ? 3 : 6, radius);
        // Round types have no facing: use primitive circles rather than rotating
        // and stroking a transformed path for every pellet in a dense field.
        if (this == PELLET || this == ORB || this == BIG_ORB) {
            int r = (int) Math.round(size), cx = (int) x, cy = (int) y;
            g.setColor(OUTLINE); g.fillOval(cx-r-1, cy-r-1, r*2+2, r*2+2);
            g.setColor(color); g.fillOval(cx-r, cy-r, r*2, r*2);
            int core = Math.max(2, (int) Math.round(size * .55));
            g.setColor(Color.WHITE); g.fillOval(cx-core/2, cy-core/2, core, core);
            return;
        }
        // Cache geometry painted by Java2D. Quantization affects presentation only;
        // the VM keeps its exact angle and collision/graze radii throughout.
        int facing = this == RING || !Double.isFinite(angle) ? 0 : Math.floorMod((int) Math.round(angle * 128 / (Math.PI * 2)), 128);
        GlyphKey key = new GlyphKey(this, color.getRGB(), (int) Math.round(size * 2), facing);
        BufferedImage glyph;
        synchronized (GLYPHS) {
            glyph = GLYPHS.get(key);
            if (glyph == null) {
                double cachedSize = key.halfSize / 2.0;
                int extent = (int) Math.ceil(cachedSize * 1.5 + 3);
                glyph = new BufferedImage(extent * 2 + 1, extent * 2 + 1, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D ink = glyph.createGraphics();
                ink.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ink.translate(extent, extent); ink.rotate(facing * Math.PI * 2 / 128); ink.scale(cachedSize, cachedSize);
                paintShape(ink, cachedSize, color); ink.dispose(); GLYPHS.put(key, glyph);
            }
        }
        g.drawImage(glyph, (int) Math.round(x) - glyph.getWidth()/2, (int) Math.round(y) - glyph.getHeight()/2, null);
    }
    private void paintShape(Graphics2D ink, double size, Color color) {
        ink.setStroke(new BasicStroke((float) (2.2 / size), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        ink.setColor(OUTLINE); ink.draw(silhouette);
        ink.setColor(color); ink.fill(silhouette);
        ink.setColor(new Color(255, 255, 255, 220));
        switch (this) {
            case RICE: case KUNAI: case SHARD: case KNIFE: case BEAM:
                ink.setStroke(new BasicStroke((float) (1.4 / size)));
                ink.draw(new Line2D.Double(-.35, 0, .65, 0)); break;
            case RING:
                ink.setColor(OUTLINE); ink.fill(new Ellipse2D.Double(-.58, -.58, 1.16, 1.16));
                ink.setColor(Color.WHITE); ink.setStroke(new BasicStroke((float) (1 / size)));
                ink.draw(new Ellipse2D.Double(-.82, -.82, 1.64, 1.64)); break;
            case FIREBALL:
                ink.fill(new Ellipse2D.Double(-.25, -.35, .85, .7)); break;
            default:
                ink.fill(new Ellipse2D.Double(-.35, -.4, .55, .55)); break;
        }
    }
}
