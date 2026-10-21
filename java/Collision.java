public final class Collision {
    private Collision() {}
    /** Squared distance from origin to a segment: also works for relative motion. */
    public static double segmentDistanceSquared(double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / length));
        double x = ax + t * dx, y = ay + t * dy;
        return x * x + y * y;
    }
    public static boolean swept(double ax, double ay, double bx, double by, double radius) {
        return segmentDistanceSquared(ax, ay, bx, by) <= radius * radius;
    }
    public static double segmentsDistanceSquared(double ax, double ay, double bx, double by,
                                                  double cx, double cy, double dx, double dy) {
        if (java.awt.geom.Line2D.linesIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0;
        return Math.min(Math.min(segmentDistanceSquared(ax-cx, ay-cy, bx-cx, by-cy), segmentDistanceSquared(ax-dx, ay-dy, bx-dx, by-dy)),
            Math.min(segmentDistanceSquared(cx-ax, cy-ay, dx-ax, dy-ay), segmentDistanceSquared(cx-bx, cy-by, dx-bx, dy-by)));
    }
}
