import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Usage: java Th06Inspect <original ST.dat, extracted .ecl, or game directory>. Read-only. */
public final class Th06Inspect {
    private static java.nio.charset.Charset nameCharset = java.nio.charset.Charset.forName("windows-31j");
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) throw new IllegalArgumentException("Usage: java Th06Inspect <TH06 directory, ST.dat or .ecl> [--charset windows-31j|GBK]");
        if (args.length == 3) {
            if (!args[1].equals("--charset")) throw new IllegalArgumentException("Expected --charset");
            nameCharset = java.nio.charset.Charset.forName(args[2]);
        }
        inspect(Path.of(args[0]));
    }
    static void inspect(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                var candidates = files.filter(Files::isRegularFile).filter(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".ecl") || name.endsWith("st.dat");
                }).sorted().toList();
                if (candidates.isEmpty()) throw new IOException("No TH06 stage archive or extracted ECL found in " + path);
                for (Path candidate : candidates) inspect(candidate);
            }
            return;
        }
        byte[] bytes = Files.readAllBytes(path);
        System.out.println("SOURCE " + path.toAbsolutePath());
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".ecl")) report(path.getFileName().toString(), bytes);
        else {
            Th06Archive archive = new Th06Archive(bytes); int found = 0;
            for (Th06Archive.Entry entry : archive.entries) if (entry.name().toLowerCase(Locale.ROOT).endsWith(".ecl")) {
                report(entry.name(), archive.read(entry)); found++;
            }
            if (found == 0) throw new IOException("Archive contains no ECL scripts.");
        }
    }
    private static void report(String name, byte[] bytes) throws Exception {
        Th06Ecl script = new Th06Ecl(bytes, nameCharset);
        long instructions = script.subroutines.stream().mapToLong(s -> s.instructions().size()).sum();
        System.out.println("ECL " + name + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            + " subs=" + script.subroutines.size() + " instructions=" + instructions + " spells=" + script.spells.size());
        System.out.println("  gameplay-sha256=" + HexFormat.of().formatHex(script.gameplayFingerprint()));
        for (Th06Ecl.Spell spell : script.spells)
            System.out.println("  sub=" + spell.subroutine() + " frame=" + spell.time() + " id=" + spell.id() + " " + spell.name());
        System.out.println("  Inventory only; bytecode execution/faithful gameplay port is not implemented yet.");
    }
}
