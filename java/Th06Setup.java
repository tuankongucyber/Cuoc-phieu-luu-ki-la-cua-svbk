import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.function.IntConsumer;

/** First-run data setup using only the JDK. Downloads are data, never executable code. */
public final class Th06Setup {
    private Th06Setup() {}

    static final URI PACK =
            URI.create(
                    "https://github.com/CNTianQi233/th06-sdl2/releases/download/Archived-fix1.1.0/th06-sdl2-game-data.zip");
    static final String SHA256 = "b65ce0913227f555978f9c453d2303782aa096c75109d251db598ed7b4718441";
    static final long MAX_BYTES = 96L * 1024 * 1024;

    public static void ensure(IntConsumer progress, int... stages) throws IOException {
        if (Th06Data.ready(stages)) return;
        if (Th06BundledData.available()) {
            progress.accept(-1);
            Th06BundledData.install();
            if (!Th06Data.ready(stages)) throw new IOException("Gói Boss đi kèm thiếu dữ liệu.");
            progress.accept(100);
            return;
        }
        Path dir = Th06Data.directory();
        Files.createDirectories(dir);
        Path pack = dir.resolve("reference-data.zip");
        if (!Files.isRegularFile(pack) || !digest(pack).equals(SHA256)) {
            if (Boolean.getBoolean("svbk.th06.offline"))
                throw new IOException(
                        "Chưa có dữ liệu Boss. Kết nối mạng hoặc chọn gói ZIP dữ liệu.");
            download(PACK, pack, SHA256, progress);
        }
        progress.accept(-1);
        for (int stage : stages) Th06Data.prepare(stage);
        progress.accept(100);
    }

    public static void importPack(Path source, IntConsumer progress) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_BYTES) {
            throw new IOException("Gói ZIP dữ liệu không hợp lệ hoặc vượt giới hạn kích thước.");
        }
        Path dir = Th06Data.directory();
        Files.createDirectories(dir);
        if (!digest(source).equals(SHA256))
            throw new IOException(
                    "Gói ZIP không đúng phiên bản dữ liệu Boss (SHA-256 không khớp).");
        Path target = dir.resolve("reference-data.zip");
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            Path temporary = Files.createTempFile(dir, "th06-import-", ".part");
            try {
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                Th06Data.publish(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        progress.accept(-1);
        for (int stage = 1; stage <= 7; stage++) Th06Data.prepare(stage);
        progress.accept(100);
    }

    static String digest(Path file) throws IOException {
        MessageDigest hash = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void download(URI source, Path destination, String expectedHash, IntConsumer progress)
            throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "th06-download-", ".part");
        HttpURLConnection connection = (HttpURLConnection) source.toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "SVBK-Java-Game/1.0");
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if (status != 200)
                throw new IOException(
                        "Không tải được dữ liệu Boss (HTTP "
                                + status
                                + "). Có thể chọn gói ZIP thủ công.");
            long expectedSize = connection.getContentLengthLong();
            if (expectedSize > MAX_BYTES)
                throw new IOException("Gói dữ liệu vượt giới hạn kích thước.");
            MessageDigest hash = sha256();
            long total = 0;
            int lastPercent = 0;
            progress.accept(0);
            try (InputStream in = connection.getInputStream();
                    OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedIOException("Đã dừng tải dữ liệu.");
                    total += n;
                    if (total > MAX_BYTES)
                        throw new IOException("Gói dữ liệu vượt giới hạn kích thước.");
                    hash.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    int percent =
                            expectedSize > 0 ? (int) Math.min(99, total * 100 / expectedSize) : 0;
                    if (percent != lastPercent) {
                        progress.accept(percent);
                        lastPercent = percent;
                    }
                }
            }
            if (!HexFormat.of().formatHex(hash.digest()).equals(expectedHash))
                throw new IOException(
                        "Dữ liệu tải về chưa đầy đủ hoặc sai phiên bản (SHA-256 không khớp). Hãy"
                            + " thử lại.");
            Th06Data.publish(temporary, destination);
        } finally {
            connection.disconnect();
            Files.deleteIfExists(temporary);
        }
    }

    public static void main(String[] args) throws IOException {
        ensure(
                p ->
                        System.out.println(
                                p < 0
                                        ? "Preparing ECL/ANM metadata..."
                                        : "Downloading boss data: " + p + "%"),
                1,
                2,
                3,
                4,
                5,
                6,
                7);
        System.out.println("Boss data ready: " + Th06Data.directory());
    }
}
