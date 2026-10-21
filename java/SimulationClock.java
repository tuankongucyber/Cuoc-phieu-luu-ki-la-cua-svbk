/** Bounded fixed-step accumulator; menus never accumulate simulation debt. */
public final class SimulationClock {
    public static final long STEP_NANOS = 1_000_000_000L / 60;
    private long last, accumulator;
    public int advance(long now, boolean running, Runnable step) {
        if (last == 0) { last = now; return 0; }
        long elapsed = Math.max(0, Math.min(250_000_000L, now - last)); last = now;
        if (!running) { accumulator = 0; return 0; }
        accumulator += elapsed; int count = 0;
        while (accumulator >= STEP_NANOS && count < 5) { accumulator -= STEP_NANOS; step.run(); count++; }
        if (count == 5) accumulator %= STEP_NANOS;
        return count;
    }
}
