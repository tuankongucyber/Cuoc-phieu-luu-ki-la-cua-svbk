import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * Java ANM v0 metadata reader and frame-stepped control interpreter. Adapted from the MIT engine
 * credited in licenses/Touhou6-engine-MIT.txt. Bitmap pixels are never loaded; script timing,
 * sprite bounds and RNG requests are retained because they affect enemy lifetime and the shared
 * gameplay RNG.
 */
final class Th06Anm {
    record Sprite(int id, float width, float height) {}

    record Instruction(int time, int opcode, int offset, byte[] args) {
        int i(int n) {
            return ByteBuffer.wrap(args).order(ByteOrder.LITTLE_ENDIAN).getInt(n * 4);
        }
    }

    final Map<Integer, Sprite> sprites = new HashMap<>();
    final Map<Integer, List<Instruction>> scripts = new HashMap<>();

    static Th06Anm read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Th06Anm result = new Th06Anm();
        try {
            for (int base = 0, entries = 0; ; ) {
                if (++entries > 1024 || base < 0 || base + 64 > bytes.length)
                    throw new IOException("Invalid ANM entry");
                int next = b.getInt(base + 0x38),
                        end = next == 0 ? bytes.length : Math.addExact(base, next);
                if (end > bytes.length || end <= base) throw new IOException("Invalid ANM bounds");
                int ns = b.getInt(base), nc = b.getInt(base + 4);
                if (ns < 0
                        || nc < 0
                        || ns > 10000
                        || nc > 10000
                        || base + 64L + ns * 4L + nc * 8L > end)
                    throw new IOException("Invalid ANM table");
                for (int i = 0; i < ns; i++) {
                    int p = Math.addExact(base, b.getInt(base + 64 + i * 4));
                    if (p < base || p + 20 > end) throw new IOException("Invalid ANM sprite");
                    Sprite sprite = new Sprite(b.getInt(p), b.getFloat(p + 12), b.getFloat(p + 16));
                    result.sprites.put(sprite.id, sprite);
                }
                int[] offsets = new int[nc], ids = new int[nc];
                for (int i = 0; i < nc; i++) {
                    int p = base + 64 + ns * 4 + i * 8;
                    ids[i] = b.getInt(p);
                    offsets[i] = Math.addExact(base, b.getInt(p + 4));
                }
                for (int i = 0; i < nc; i++) {
                    int start = offsets[i], limit = end;
                    for (int other : offsets) if (other > start) limit = Math.min(limit, other);
                    if (start < base || start >= limit) throw new IOException("Invalid ANM script");
                    List<Instruction> code = new ArrayList<>();
                    for (int p = start; p + 4 <= limit; ) {
                        int time = Short.toUnsignedInt(b.getShort(p)),
                                op = Byte.toUnsignedInt(bytes[p + 2]),
                                size = Byte.toUnsignedInt(bytes[p + 3]);
                        if (p + 4 + size > limit)
                            throw new IOException("Truncated ANM instruction");
                        int required = op == 16 ? 8 : op == 1 || op == 5 || op == 22 ? 4 : 0;
                        if (size < required) throw new IOException("Truncated ANM arguments");
                        code.add(
                                new Instruction(
                                        time,
                                        op,
                                        p - start,
                                        Arrays.copyOfRange(bytes, p + 4, p + 4 + size)));
                        p += 4 + size;
                        if (time == 0 && op == 0) break;
                    }
                    result.scripts.put(ids[i], List.copyOf(code));
                }
                if (next == 0) break;
                base = end;
            }
        } catch (IndexOutOfBoundsException | ArithmeticException invalid) {
            throw new IOException("Malformed ANM metadata", invalid);
        }
        return result;
    }

    static final class Runner {
        final List<Instruction> code;
        int pc, clock, sprite = -1, randomBase, randomRange;
        boolean halted, dead, pendingRandom;

        Runner(List<Instruction> code) {
            this.code = code;
            ready();
        }

        void tick() {
            if (!dead) {
                clock = Math.min(65535, clock + 1);
                ready();
            }
        }

        void resolve(Th06Vm.Rng rng) {
            if (pendingRandom) {
                sprite = randomBase + rng.u16Range(randomRange);
                pendingRandom = false;
            }
        }

        void interrupt(int label) {
            if (dead) return;
            for (int n = 0; n < code.size(); n++) {
                Instruction i = code.get(n);
                if (i.opcode == 22 && i.args.length >= 4 && i.i(0) == label) {
                    pc = n + 1;
                    clock = i.time;
                    halted = false;
                    ready();
                    return;
                }
            }
        }

        void ready() {
            int budget = 1000;
            while (!halted && !dead && pc < code.size() && --budget > 0) {
                Instruction i = code.get(pc);
                if (i.time > clock) break;
                switch (i.opcode) {
                    case 0 -> dead = true;
                    case 15 -> halted = true;
                    case 1 -> sprite = i.i(0);
                    case 5 -> {
                        int target = i.i(0), found = -1;
                        for (int n = 0; n < code.size(); n++)
                            if (code.get(n).offset == target) {
                                found = n;
                                break;
                            }
                        if (found >= 0) {
                            pc = found;
                            clock = code.get(found).time;
                            continue;
                        }
                    }
                    case 16 -> {
                        randomBase = i.i(0);
                        randomRange = i.i(1) & 65535;
                        pendingRandom = true;
                    }
                    case 21, 24 -> {
                        pc++;
                        halted = true;
                        return;
                    }
                    default -> {
                        /* Pure presentation transforms have no gameplay consumers. */
                    }
                }
                pc++;
            }
        }
    }
}
