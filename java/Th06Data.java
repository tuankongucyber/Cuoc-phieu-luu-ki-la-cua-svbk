import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Resolves the bundled Boss scripts/metadata into the game's writable local cache. */
public final class Th06Data {
    private Th06Data() {}
    public static Path directory() {
        String override = System.getProperty("svbk.th06.data");
        if (override != null) return Path.of(override).toAbsolutePath();
        String local = System.getenv("LOCALAPPDATA");
        return Path.of(local == null ? System.getProperty("user.home") : local, "SVBK", "TH06Reference");
    }
    public static boolean available() { return ready(2, 6) || Th06BundledData.available() || nonempty(directory().resolve("reference-data.zip")); }
    public static boolean ready(int... stages) {
        Path dir = directory();
        if (!nonempty(dir.resolve("etama3.anm")) || !nonempty(dir.resolve("etama4.anm"))) return false;
        for (int stage : stages) {
            if (stage < 1 || stage > 7) throw new IllegalArgumentException("Invalid TH06 stage");
            if (!nonempty(dir.resolve("ecldata" + stage + ".ecl")) || !nonempty(dir.resolve("stg" + stage + "enm.anm"))) return false;
            if ((stage == 1 || stage == 2 || stage >= 5) && !nonempty(dir.resolve("stg" + stage + "enm2.anm"))) return false;
        }
        return true;
    }
    private static boolean nonempty(Path file) {
        try { return Files.isRegularFile(file) && Files.size(file) > 0; }
        catch (IOException absent) { return false; }
    }
    static void writeAtomic(Path file, byte[] data) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "th06-", ".part");
        try { Files.write(temporary, data); publish(temporary, file); }
        finally { Files.deleteIfExists(temporary); }
    }
    static void publish(Path temporary, Path file) throws IOException {
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    public static synchronized Path prepare(int stage) throws IOException {
        Path dir = directory(); Files.createDirectories(dir);
        Path script = dir.resolve("ecldata" + stage + ".ecl");
        if (ready(stage)) return script;
        if (Th06BundledData.install() && ready(stage)) return script;
        Path pack = dir.resolve("reference-data.zip");
        if (!Files.isRegularFile(pack)) throw new IOException("Thiếu dữ liệu TH06 trong " + dir);
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith("st.dat") && !name.endsWith("cm.dat")) continue;
                if (entry.getSize() > 64 * 1024 * 1024) throw new IOException("Invalid stage archive size");
                byte[] data;
                try (var stream = zip.getInputStream(entry)) { data = stream.readNBytes(64 * 1024 * 1024 + 1); }
                if (data.length > 64 * 1024 * 1024) throw new IOException("Stage archive too large");
                Th06Archive archive = new Th06Archive(data);
                for (Th06Archive.Entry file : archive.entries) {
                    // Flat allowlist: no bitmaps, music, executable or archive-controlled output paths.
                    String basename = file.name().toLowerCase(Locale.ROOT);
                    if (basename.matches("ecldata[1-7]\\.ecl|etama[34]\\.anm|stg[1-7]enm2?\\.anm"))
                        writeAtomic(dir.resolve(basename), archive.read(file));
                }
            }
        }
        if (!ready(stage)) throw new IOException("Gói dữ liệu thiếu script/metadata cho màn " + stage);
        return script;
    }
}
