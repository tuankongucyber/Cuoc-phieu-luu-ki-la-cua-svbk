import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** Validates original ECL bytecode and inventories declarations. This does not execute ECL. */
public final class Th06Ecl {
    public record Instruction(int offset, int time, int opcode, int difficultyMask, byte[] arguments) {}
    public record Subroutine(int id, List<Instruction> instructions) {}
    public record Spell(int subroutine, int time, int id, String name) {}
    public final List<Subroutine> subroutines;
    public final List<Spell> spells;
    public Th06Ecl(byte[] bytes) throws IOException {
        this(bytes, Charset.forName("windows-31j"));
    }
    public Th06Ecl(byte[] bytes, Charset nameCharset) throws IOException {
        if (bytes.length < 16) throw new IOException("Truncated ECL header.");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = Short.toUnsignedInt(b.getShort(0)), mainCount = Short.toUnsignedInt(b.getShort(2));
        int tableEnd = 16 + count * 4;
        if (count == 0 || count > 10000 || mainCount > 3 || tableEnd > bytes.length)
            throw new IOException("Invalid TH06 ECL header.");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int timeline = b.getInt(4 + i * 4);
            if (timeline != 0 && (timeline < tableEnd || timeline >= bytes.length))
                throw new IOException("Invalid timeline offset.");
            if (timeline != 0) offsets.add(timeline);
        }
        for (int i = 0; i < count; i++) {
            int offset = b.getInt(16 + i * 4);
            if (offset < tableEnd || offset >= bytes.length) throw new IOException("Invalid subroutine offset.");
            offsets.add(offset);
        }
        List<Subroutine> subs = new ArrayList<>(); List<Spell> declarations = new ArrayList<>();
        for (int sub = 0; sub < count; sub++) {
            int start = b.getInt(16 + sub * 4), end = bytes.length;
            for (int other : offsets) if (other > start) end = Math.min(end, other);
            List<Instruction> instructions = new ArrayList<>();
            for (int p = start; p < end;) {
                if (end - p < 12) throw new IOException("Truncated ECL instruction in sub " + sub);
                int time = b.getInt(p), opcode = Short.toUnsignedInt(b.getShort(p + 4));
                int size = Short.toUnsignedInt(b.getShort(p + 6));
                if (time == -1 || opcode == 65535) break;
                if (size < 12 || size > end - p) throw new IOException("Invalid ECL instruction length at " + p);
                byte[] arguments = java.util.Arrays.copyOfRange(bytes, p + 12, p + size);
                instructions.add(new Instruction(p, time, opcode, Byte.toUnsignedInt(bytes[p + 9]), arguments));
                if (opcode == 93) {
                    if (arguments.length < 38) throw new IOException("Truncated Spell Card declaration.");
                    int nameEnd = 4; while (nameEnd < 38 && arguments[nameEnd] != 0) nameEnd++;
                    String name = new String(arguments, 4, nameEnd - 4, nameCharset);
                    int id = Byte.toUnsignedInt(arguments[2]) | Byte.toUnsignedInt(arguments[3]) << 8;
                    declarations.add(new Spell(sub, time, id, name));
                }
                p += size;
            }
            subs.add(new Subroutine(sub, List.copyOf(instructions)));
        }
        subroutines = List.copyOf(subs); spells = List.copyOf(declarations);
    }
    /** Ignore only displayed spell names; retain timing, branches, movements and firing parameters. */
    public byte[] gameplayFingerprint() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (Subroutine sub : subroutines) {
                digest.update(ByteBuffer.allocate(8).putInt(sub.id()).putInt(sub.instructions().size()).array());
                for (Instruction instruction : sub.instructions()) {
                    digest.update(ByteBuffer.allocate(12).putInt(instruction.time()).putInt(instruction.opcode())
                        .putInt(instruction.difficultyMask()).array());
                    byte[] arguments = instruction.arguments();
                    digest.update(ByteBuffer.allocate(4).putInt(arguments.length).array());
                    digest.update(arguments, 0, instruction.opcode() == 93 ? 4 : arguments.length);
                }
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
