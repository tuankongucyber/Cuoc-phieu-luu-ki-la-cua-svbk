import java.io.*;
import java.nio.file.*;

/** Verifies that a fresh checkout can prepare all encounters while downloads are disabled. */
public final class Th06BundledDataTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String dataBefore = System.getProperty("svbk.th06.data"),
                offlineBefore = System.getProperty("svbk.th06.offline"),
                bundleBefore = System.getProperty("svbk.th06.bundle");
        Path fresh = Files.createTempDirectory("svbk-bundled-test-");
        try {
            System.setProperty("svbk.th06.data", fresh.toString());
            System.setProperty("svbk.th06.offline", "true");
            System.clearProperty("svbk.th06.bundle");
            try (InputStream resource =
                    Th06BundledData.class.getResourceAsStream("/" + Th06BundledData.RESOURCE)) {
                check(resource != null, "build includes the Boss ZIP on the classpath");
                check(
                        resource.readAllBytes().length == 53413,
                        "small offline pack is included instead of the full download");
            }
            Th06Setup.ensure(p -> {}, 1, 2, 3, 4, 5, 6, 7);
            check(
                    Th06Data.ready(1, 2, 3, 4, 5, 6, 7),
                    "all encounters prepare without network or an existing pack");
            try (var files = Files.list(fresh)) {
                check(files.count() == 21, "only 21 script/metadata files are prepared");
            }
            check(
                    !Files.exists(fresh.resolve("reference-data.zip")),
                    "full download is not needed");
            try (var files = Files.list(fresh)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Path corrupt = fresh.resolve("corrupt.zip");
            Files.write(corrupt, new byte[] {1, 2, 3});
            System.setProperty("svbk.th06.bundle", corrupt.toString());
            try {
                Th06BundledData.install();
                throw new AssertionError("corrupt bundled resource must fail");
            } catch (IOException expected) {
                check(
                        expected.getMessage().contains("bị hỏng"),
                        "corruption has an actionable message");
            }
            try (var files = Files.list(fresh)) {
                check(files.count() == 1, "corrupt resource writes no cache files");
            }
        } finally {
            restore("svbk.th06.data", dataBefore);
            restore("svbk.th06.offline", offlineBefore);
            restore("svbk.th06.bundle", bundleBefore);
            try (var files = Files.list(fresh)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(fresh);
        }
        System.out.println(
                "PASS Th06BundledDataTest: classpath resource, all 14 encounters offline, no full"
                    + " download, corruption rejection");
    }

    static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
