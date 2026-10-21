import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Original game scripts/metadata stay in a local cache, never in the Git checkout. */
public final class Th06Data {
    private Th06Data() {}
    public static Path directory() {
        String override = System.getProperty("svbk.th06.data");
        if (override != null) return Path.of(override).toAbsolutePath();
        String local = System.getenv("LOCALAPPDATA");
        return Path.of(local == null ? System.getProperty("user.home") : local, "SVBK", "TH06Reference");
    }
    public static Path host() {
        String override = System.getProperty("svbk.th06.host");
        if (override != null) return Path.of(override);
        try {
            String name = Files.readString(directory().resolve("host-path.txt")).trim();
            if (name.matches("th06-host-[a-f0-9]{16}\\.exe")) return directory().resolve(name);
        } catch (IOException absent) { /* Compatibility with earlier local builds. */ }
        return directory().resolve("th06-host.exe");
    }
    public static boolean available() { return Files.isRegularFile(host()) && (Files.isRegularFile(directory().resolve("reference-data.zip")) || Files.isRegularFile(directory().resolve("ecldata7.ecl"))); }
    public static synchronized Path prepare(int stage) throws IOException {
        Path dir = directory(); Files.createDirectories(dir);
        Path script = dir.resolve("ecldata" + stage + ".ecl");
        if (Files.isRegularFile(script) && Files.isRegularFile(dir.resolve("etama3.anm")) && Files.isRegularFile(dir.resolve("etama4.anm"))
            && Files.isRegularFile(dir.resolve("stg" + stage + "enm.anm"))) return script;
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
                        Files.write(dir.resolve(basename), archive.read(file));
                }
            }
        }
        if (!Files.isRegularFile(script) || !Files.isRegularFile(dir.resolve("etama3.anm"))) throw new IOException("TH06 archive lacks required scripts/metadata");
        return script;
    }
}
