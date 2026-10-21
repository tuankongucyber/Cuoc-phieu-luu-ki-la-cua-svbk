import java.util.*;
/** Uniform broad phase. Swept objects occupy every cell touched by their AABB. */
public final class SpatialGrid<T> {
    private static final int CELL = 96;
    private final Map<Long, ArrayList<T>> cells = new HashMap<>();
    private final Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    public void clear() { cells.clear(); }
    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
    public void insert(T value, double minX, double minY, double maxX, double maxY) {
        for (int x = (int) Math.floor(minX / CELL); x <= Math.floor(maxX / CELL); x++)
            for (int y = (int) Math.floor(minY / CELL); y <= Math.floor(maxY / CELL); y++)
                cells.computeIfAbsent(key(x, y), k -> new ArrayList<>()).add(value);
    }
    public void query(double minX, double minY, double maxX, double maxY, List<T> result) {
        result.clear();
        seen.clear();
        for (int x = (int) Math.floor(minX / CELL); x <= Math.floor(maxX / CELL); x++)
            for (int y = (int) Math.floor(minY / CELL); y <= Math.floor(maxY / CELL); y++) {
                List<T> bucket = cells.get(key(x, y));
                if (bucket != null) for (T value : bucket) if (seen.add(value)) result.add(value);
            }
    }
}
