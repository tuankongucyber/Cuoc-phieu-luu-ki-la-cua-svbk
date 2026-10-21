import java.nio.file.*;
import java.util.*;

/** Replays checkpoints certified against the removed engine, using only Java. */
public final class Th06JavaReplayTest {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void near(float actual, String expected, String context) {
        float value = Float.parseFloat(expected);
        check(
                (Float.isNaN(actual) && Float.isNaN(value)) || Math.abs(actual - value) < .05f,
                context + ": " + actual + " != " + value);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("svbk.test.root", "."));
        List<String> lines = Files.readAllLines(root.resolve("tests/th06-replay-reference.tsv"));
        Set<Long> children =
                ProcessHandle.current()
                        .children()
                        .map(ProcessHandle::pid)
                        .collect(java.util.stream.Collectors.toSet());
        Th06Vm vm = null;
        String scenario = "";
        int frame = 0, checkpoints = 0, scenarios = 0;
        List<Integer> live = List.of();
        try {
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] v = line.split("\\s+");
                String key = v[0] + "/" + v[1] + "/" + v[2];
                if (!key.equals(scenario)) {
                    if (vm != null) vm.close();
                    Th06BossId id = Th06BossId.valueOf(v[0]);
                    Difficulty d = Difficulty.valueOf(v[1]);
                    vm =
                            new Th06Vm(
                                    Th06Data.prepare(id.stage),
                                    id.entry,
                                    id.stage == 7 ? 4 : d.ordinal(),
                                    20261021 & 65535,
                                    Integer.parseInt(v[2]));
                    frame = 0;
                    scenario = key;
                    live = List.of();
                    scenarios++;
                }
                Th06Runtime.Snapshot s = null;
                int target = Integer.parseInt(v[3]);
                while (frame < target) {
                    frame++;
                    Map<Integer, Double> damage = new LinkedHashMap<>();
                    if (frame % 480 == 0) for (int actor : live) damage.put(actor, 1e8);
                    vm.step(
                            192 + Th06Vm.sin(frame / 120f) * 120,
                            384 + Th06Vm.sin(frame / 200f) * 30,
                            frame == 900 || frame == 2100,
                            damage,
                            Set.of());
                    s = vm.snapshot();
                    live = s.actors().stream().map(Th06Runtime.ActorState::id).toList();
                }
                String at = scenario + " frame=" + frame;
                check(s != null, at + " duplicate checkpoint");
                check(
                        s.status() == Integer.parseInt(v[4])
                                && s.actors().size() == Integer.parseInt(v[5])
                                && s.bullets().size() == Integer.parseInt(v[6])
                                && s.beams().size() == Integer.parseInt(v[7]),
                        at + " pool/status mismatch");
                if (!s.actors().isEmpty()) {
                    var a = s.actors().get(0);
                    near(a.hp(), v[8], at + " HP");
                    near(a.x(), v[9], at + " actor x");
                    near(a.y(), v[10], at + " actor y");
                }
                if (!s.bullets().isEmpty()) {
                    var b = s.bullets().get(0);
                    check(
                            b.id() == Integer.parseInt(v[11])
                                    && b.sprite() == Integer.parseInt(v[15]),
                            at + " bullet identity");
                    near(b.x(), v[12], at + " bullet x");
                    near(b.y(), v[13], at + " bullet y");
                    near(b.angle(), v[14], at + " angle");
                }
                check(vm.world.rng.seed == Integer.parseInt(v[16]), at + " RNG draw order");
                checkpoints++;
            }
        } finally {
            if (vm != null) vm.close();
        }
        check(
                scenarios == 45 && checkpoints == 514,
                "all prior-engine scenarios/checkpoints are covered");
        check(
                ProcessHandle.current().children().allMatch(p -> children.contains(p.pid())),
                "ECL must not spawn a child process");
        System.out.println(
                "PASS Th06JavaReplayTest: 45 scenarios, 514 certified checkpoints, no child"
                    + " process");
    }
}
