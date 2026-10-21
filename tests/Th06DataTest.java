import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

public final class Th06DataTest {
    interface Action { void run() throws IOException; }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Action action, String message) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError(message);
    }
    static final class Bits {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int value, count;
        void put(long n, int size) {
            for (int bit = size - 1; bit >= 0; bit--) {
                value = (value << 1) | (int) ((n >>> bit) & 1);
                if (++count == 8) { stream.write(value); value = count = 0; }
            }
        }
        void variable(int n) { put(3, 2); put(Integer.toUnsignedLong(n), 32); }
        byte[] finish() { if (count != 0) { stream.write(value << (8 - count)); count = 0; } return stream.toByteArray(); }
    }
    static byte[] ecl() {
        ByteBuffer b = ByteBuffer.allocate(82).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 1).putShort((short) 0);
        b.position(16); b.putInt(20);
        b.putInt(60).putShort((short) 93).putShort((short) 50).putShort((short) 0x0e00).putShort((short) 0);
        b.putShort((short) 0).putShort((short) 5);
        b.put("凍符「パーフェクトフリーズ」".getBytes(Charset.forName("windows-31j")));
        b.position(70); b.putInt(-1).putShort((short) -1).putShort((short) 12).putInt(0);
        return b.array();
    }
    static byte[] archive(byte[] ecl) {
        Bits compressed = new Bits();
        for (byte value : ecl) { compressed.put(1, 1); compressed.put(value & 255, 8); }
        byte[] body = compressed.finish();
        int dataOffset = 20, directoryOffset = dataOffset + body.length;
        Bits header = new Bits(); header.variable(1); header.variable(directoryOffset);
        Bits directory = new Bits(); directory.variable(0); directory.variable(0);
        directory.variable(0); directory.variable(dataOffset); directory.variable(ecl.length);
        for (byte value : "ecldata2.ecl\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) directory.put(value & 255, 8);
        byte[] table = directory.finish(), result = new byte[directoryOffset + table.length];
        System.arraycopy(new byte[]{'P', 'B', 'G', '3'}, 0, result, 0, 4);
        byte[] h = header.finish(); System.arraycopy(h, 0, result, 4, h.length);
        System.arraycopy(body, 0, result, dataOffset, body.length);
        System.arraycopy(table, 0, result, directoryOffset, table.length);
        return result;
    }
    public static void main(String[] args) throws Exception {
        byte[] original = ecl();
        Th06Archive archive = new Th06Archive(archive(original));
        check(archive.entries.size() == 1 && archive.entries.get(0).name().equals("ecldata2.ecl"), "PBG3 directory");
        check(Arrays.equals(original, archive.read(archive.entries.get(0))), "LZSS literal roundtrip");
        Th06Ecl script = new Th06Ecl(original);
        check(script.subroutines.size() == 1 && script.spells.size() == 1, "ECL declaration inventory");
        check(script.spells.get(0).id() == 5 && script.spells.get(0).time() == 60, "spell identity and frame");
        check(script.spells.get(0).name().equals("凍符「パーフェクトフリーズ」"), "CP932 original name");
        check(script.subroutines.get(0).instructions().get(0).difficultyMask() == 14, "difficulty mask retained");
        byte[] renamed = original.clone(); renamed[36] = 'X';
        check(Arrays.equals(script.gameplayFingerprint(), new Th06Ecl(renamed).gameplayFingerprint()), "displayed names excluded from gameplay comparison");
        byte[] retimed = original.clone(); ByteBuffer.wrap(retimed).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 61);
        check(!Arrays.equals(script.gameplayFingerprint(), new Th06Ecl(retimed).gameplayFingerprint()), "timing changes detected");
        byte[] newId = original.clone(); newId[34] = 6;
        check(!Arrays.equals(script.gameplayFingerprint(), new Th06Ecl(newId).gameplayFingerprint()), "spell identity changes detected");
        byte[] translated = original.clone(); Arrays.fill(translated, 36, 70, (byte) 0);
        byte[] chineseName = "冻符「Perfect Freeze」".getBytes(Charset.forName("GBK"));
        System.arraycopy(chineseName, 0, translated, 36, chineseName.length);
        check(new Th06Ecl(translated, Charset.forName("GBK")).spells.get(0).name().equals("冻符「Perfect Freeze」"), "localized GBK names decoded explicitly");
        Bits overlap = new Bits(); overlap.put(1, 1); overlap.put('A', 8);
        overlap.put(0, 1); overlap.put(1, 13); overlap.put(2, 4);
        check(Arrays.equals(Th06Archive.decompress(overlap.finish(), 6), "AAAAAA".getBytes()), "overlapping dictionary match");
        rejects(() -> new Th06Archive(new byte[8]), "reject bad magic");
        rejects(() -> new Th06Archive(Arrays.copyOf(archive(original), 12)), "reject truncated directory");
        rejects(() -> Th06Archive.decompress(new byte[]{0, 0}, 6), "reject premature stream end");
        rejects(() -> Th06Archive.decompress(new byte[]{(byte) 255}, 6), "reject truncated literal");
        rejects(() -> new Th06Ecl(new byte[8]), "reject truncated ECL");
        byte[] badLength = original.clone(); badLength[26] = 1; badLength[27] = 0;
        rejects(() -> new Th06Ecl(badLength), "reject instruction shorter than header");
        byte[] badOffset = original.clone(); ByteBuffer.wrap(badOffset).order(ByteOrder.LITTLE_ENDIAN).putInt(16, Integer.MAX_VALUE);
        rejects(() -> new Th06Ecl(badOffset), "reject out-of-file subroutine");
        System.out.println("PASS Th06DataTest: PBG3, LZSS overlap, original ECL names/masks and malformed input");
    }
}
