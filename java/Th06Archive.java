import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Read-only TH06 PBG3 reader. Format references: docs/TH06_PORT.md. */
public final class Th06Archive {
    public record Entry(String name, int offset, int size, long checksum) {}
    private final byte[] bytes;
    public final List<Entry> entries;
    public Th06Archive(Path path) throws IOException { this(Files.readAllBytes(path)); }
    Th06Archive(byte[] data) throws IOException {
        bytes = data.clone();
        if (data.length < 8 || data[0] != 'P' || data[1] != 'B' || data[2] != 'G' || data[3] != '3')
            throw new IOException("Expected a TH06 PBG3 archive (not a remake or a newer game).");
        Bits header = new Bits(data, 4, data.length);
        int count = header.variable(), directory = header.variable();
        if (count < 1 || count > 10000 || directory < 8 || directory >= data.length)
            throw new IOException("Invalid PBG3 directory.");
        Bits table = new Bits(data, directory, data.length);
        List<Entry> found = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            table.variable(); table.variable();
            long checksum = Integer.toUnsignedLong(table.variable());
            int offset = table.variable(), size = table.variable();
            StringBuilder name = new StringBuilder();
            for (int n = 0; n < 255; n++) {
                int c = table.read(8);
                if (c == 0) break;
                name.append((char) c);
                if (n == 254) throw new IOException("Unterminated PBG3 filename.");
            }
            if (offset < 8 || offset >= directory || size < 0 || size > 64 * 1024 * 1024)
                throw new IOException("Invalid PBG3 entry: " + name);
            found.add(new Entry(name.toString(), offset, size, checksum));
        }
        entries = List.copyOf(found);
    }
    public byte[] read(Entry entry) throws IOException {
        if (!entries.contains(entry)) throw new IOException("Entry does not belong to this archive.");
        int end = bytes.length;
        for (Entry candidate : entries) if (candidate.offset > entry.offset) end = Math.min(end, candidate.offset);
        return decompress(Arrays.copyOfRange(bytes, entry.offset, end), entry.size);
    }
    static byte[] decompress(byte[] compressed, int expected) throws IOException {
        if (expected < 0 || expected > 64 * 1024 * 1024) throw new IOException("Invalid output size.");
        byte[] result = new byte[expected], dictionary = new byte[8192];
        Bits bits = new Bits(compressed, 0, compressed.length);
        int written = 0, head = 1;
        while (written < expected) {
            if (bits.read(1) == 1) {
                byte value = (byte) bits.read(8);
                result[written++] = dictionary[head] = value; head = (head + 1) & 8191;
            } else {
                int offset = bits.read(13);
                if (offset == 0) throw new IOException("LZSS stream ends before its declared size.");
                int length = bits.read(4) + 3;
                if (length > expected - written) throw new IOException("LZSS match exceeds declared size.");
                for (int i = 0; i < length; i++) {
                    byte value = dictionary[(offset + i) & 8191];
                    result[written++] = dictionary[head] = value; head = (head + 1) & 8191;
                }
            }
        }
        return result;
    }
    private static final class Bits {
        private final byte[] bytes;
        private final int end;
        private long bit;
        Bits(byte[] bytes, int start, int end) { this.bytes = bytes; this.bit = start * 8L; this.end = end; }
        int read(int count) throws IOException {
            if (bit + count > end * 8L) throw new IOException("Truncated PBG3 bitstream.");
            int value = 0;
            for (int i = 0; i < count; i++, bit++)
                value = (value << 1) | ((bytes[(int) (bit / 8)] >>> (7 - bit % 8)) & 1);
            return value;
        }
        int variable() throws IOException { return read((read(2) + 1) * 8); }
    }
}
