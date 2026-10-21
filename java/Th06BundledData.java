import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Small, offline Boss data resource shipped with the game; no executable or textures. */
public final class Th06BundledData {
    private Th06BundledData() {}

    static final String RESOURCE = "th06-boss-data.zip";
    static final String SHA256 = "14498320dbffe1f901d6cb5a81670b1df7b9a7fc646b48b750e39992f6b5561b";

    private static InputStream open() throws IOException {
        String override = System.getProperty("svbk.th06.bundle");
        if (override != null) {
            Path path = Path.of(override);
            return Files.isRegularFile(path) ? Files.newInputStream(path) : null;
        }
        InputStream resource = Th06BundledData.class.getResourceAsStream("/" + RESOURCE);
        if (resource != null) return resource;
        // Support plain javac/IDE launches as well as the resource copied by run.bat/JAR builds.
        List<Path> roots = new ArrayList<>();
        try {
            roots.add(
                    Path.of(
                                    Th06BundledData.class
                                            .getProtectionDomain()
                                            .getCodeSource()
                                            .getLocation()
                                            .toURI())
                            .resolve("resources"));
        } catch (Exception unavailable) {
            /* Resource lookup below still supports project launches. */
        }
        roots.add(Path.of("java", "resources"));
        roots.add(Path.of("resources"));
        for (Path root : roots) {
            Path path = root.resolve(RESOURCE);
            if (Files.isRegularFile(path)) return Files.newInputStream(path);
        }
        return null;
    }

    public static boolean available() {
        try (InputStream in = open()) {
            return in != null;
        } catch (IOException absent) {
            return false;
        }
    }

    public static synchronized boolean install() throws IOException {
        byte[] packed;
        try (InputStream in = open()) {
            if (in == null) return false;
            packed = in.readNBytes(256 * 1024 + 1);
        }
        if (packed.length > 256 * 1024)
            throw new IOException("Gói Boss đi kèm vượt giới hạn kích thước.");
        try {
            if (!HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(packed))
                    .equals(SHA256))
                throw new IOException("Gói Boss đi kèm bị hỏng. Hãy cập nhật lại dự án.");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(packed))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String name = entry.getName();
                if (!name.matches("ecldata[1-7]\\.ecl|etama[34]\\.anm|stg[1-7]enm2?\\.anm")
                        || files.containsKey(name))
                    throw new IOException("Gói Boss đi kèm có tệp không hợp lệ.");
                byte[] bytes = zip.readNBytes(128 * 1024 + 1);
                total += bytes.length;
                if (bytes.length > 128 * 1024 || total > 512 * 1024)
                    throw new IOException("Dữ liệu Boss đi kèm quá lớn.");
                files.put(name, bytes);
            }
        }
        if (files.size() != 21) throw new IOException("Gói Boss đi kèm chưa đầy đủ.");
        Path dir = Th06Data.directory();
        Files.createDirectories(dir);
        for (var file : files.entrySet())
            Th06Data.writeAtomic(dir.resolve(file.getKey()), file.getValue());
        return true;
    }
}
