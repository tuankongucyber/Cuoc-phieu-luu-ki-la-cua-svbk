import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.TreeMap;

/** Compare instruction streams across a trial and a localized archive. Ignores spell names only. */
public final class Th06Verify {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java Th06Verify <reference ST.dat> <candidate ST.dat>");
        var reference = scripts(new Th06Archive(Path.of(args[0])));
        var candidate = scripts(new Th06Archive(Path.of(args[1])));
        int matches = 0, differences = 0;
        for (var entry : reference.entrySet()) {
            String name = entry.getKey();
            if (!candidate.containsKey(name)) throw new IllegalArgumentException("Candidate missing " + name);
            byte[] a = entry.getValue().gameplayFingerprint(), b = candidate.get(name).gameplayFingerprint();
            boolean same = Arrays.equals(a, b);
            System.out.println((same ? "MATCH " : "DIFFERENT ") + name + " gameplay-sha256=" + HexFormat.of().formatHex(b));
            if (same) matches++; else differences++;
        }
        System.out.println("Compared " + (matches + differences) + " shared stage scripts. Extra candidate scripts: " + (candidate.size() - reference.size()));
        System.out.println("This checks subroutine instructions, not original-game provenance, timeline scheduling or runtime fidelity.");
        if (differences > 0) throw new IllegalStateException("Gameplay instructions differ in " + differences + " scripts.");
    }
    static TreeMap<String, Th06Ecl> scripts(Th06Archive archive) throws Exception {
        var result = new TreeMap<String, Th06Ecl>();
        for (Th06Archive.Entry entry : archive.entries) if (entry.name().matches("(?i)ecldata[1-7]\\.ecl"))
            result.put(entry.name(), new Th06Ecl(archive.read(entry)));
        return result;
    }
}
