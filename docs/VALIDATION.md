# Current validation — 2026-10-21 (100% Java runtime)

Run `./tests/run-tests.ps1 -Original -Benchmark` with JDK 17+. Boss data is shipped as a small classpath resource; an existing cache and internet access are not required. All game code, including the ECL interpreter, compiles with `javac --release 17` and uses only the standard JDK. The Rust sources/build script are removed; no native executable, JNI, library or child process is needed. See [TH06_PORT.md](TH06_PORT.md) for provenance and architecture.

The Java migration passes the core/menu/pattern suite, GameplayTest (325 assertions), Th06HealthTest, all 14 real-script integrations, all 45 full-moveset scenarios, and Th06BehaviorTest including pause/replay, missing-data handling, Bomb and laser collisions. Th06JavaReplayTest passes 514 reference checkpoints from 45 scenarios, including exact RNG states, and asserts no new child process appears. Its fixture is numeric replay output, not original game scripts or assets.

Bundled-data setup: Th06SetupIntegrationTest starts with an empty cache, no downloaded pack, and `svbk.th06.offline=true`. The shipped 53,413-byte resource prepares all 21 files; loading is asynchronous, duplicate starts are blocked, the original start request resumes with its seed, and a real Sakuya encounter fires correctly. Th06SetupTest retains local-server coverage for the optional recovery downloader: redirects, progress, checksum rejection, HTTP failures, size limits, partial cleanup and cache preservation. It also checks normal-run readiness against stages 2/6 and their metadata. Missing-data UI checks deliberately hide the bundled resource to exercise incomplete-installation errors.

Th06BundledDataTest verifies the classpath resource, all encounters from an empty offline cache, absence of the full downloaded archive, and rejection of a corrupted bundle before any cache file is written. A separate temporary JAR was launched from outside the checkout with a new empty cache and downloads disabled; all 45 replay scenarios / 514 checkpoints passed using its embedded resource. The cache contained exactly the 21 metadata/script files.

A real first-run check also succeeded with a separate empty cache: the Java downloader fetched the pinned GitHub pack, passed SHA-256 verification, prepared all seven stage scripts and their ANM metadata, and reported ready without a preinstalled host or external toolchain. Loading and missing-data menu renders were visually checked at 1000×750.

A temporary migration-only harness compared the old interpreter with Java for 123,360 frames, 175,992 actor states and 12,904,251 bullet states. Discrete fields matched; maximum finite numeric difference was 0.00097656 source pixels. Moving input, Bombs and forced-damage transitions were included. This preserves the previous implementation on these traces; it does not establish equivalence to the original Touhou executable. Full-run balance has not been remeasured after the phase-health increase. Original scripts/metadata remain external data.

The final Java build passes both software-rendering performance targets on this machine. At 640 hostile bullets, update P95 is 0.088 ms, render P95 1.583 ms, combined P95 1.668 ms. At 300 enemies and 2,000 projectiles, combined P95 is 15.937 ms (target <16.7 ms). Oriented geometric glyphs are painted with Java2D into a bounded 1,024-entry memory cache; round types use primitive circles. Cached artwork rounds facing to 128 directions and size to half-pixel steps without changing any simulation angles, hitboxes or graze radii. The ten-type contact sheet and a real Sakuya Misdirection frame were visually checked. These measurements are workload checks on this machine, not universal live-window FPS guarantees.

## Historical original integration measurements (before the Java migration and HP increase)

- Th06IntegrationTest: 14 real-script encounter starts, visible original fire, pause-clock checks and renders.
- Th06FullMovesetTest: all 42 encounter/difficulty combinations finish, plus the other three Normal Patchouli routes. This uses forced damage after observation windows to cover the whole phase graph; it is not an unaided-play claim. Flandre executes all ten cards, Remilia Normal all five and Cirno Normal all three. Clean capture counts match declared completed cards.
- Th06BehaviorTest: native seeded trajectories repeat; Bomb clears native bullets; warning lasers do no damage; active lasers hit swept dash paths and respect immunity; the 14-entry chooser, pause/replay/Enter, return-to-Start, process cleanup and normal-run Cirno selection work. Missing runtime gives a menu error, without silently loading legacy patterns.
- GameplayTest (321 assertions), BossReworkTest, GameMenuTest, BossPracticeTest, EosdPatternTest and Th06DataTest pass. Legacy-only fixtures explicitly set `svbk.legacyBosses=true`; production does not use the mixed movesets.
- Three Normal survival traces win: TARGET 13:00, 100 HP, 3 Bombs; AREA 13:07, 88 HP, 2 Bombs; CONTROL 14:43, 100 HP, 3 Bombs. These use real inputs/upgrades and no HP/stat cheats. Target/area use continuous native-arena steering; control uses a four-tick held-input policy to avoid direction chatter. They prove three traces, not universal human balance.
- Field load benchmark, 300 enemies + 2,000 projectiles, 600 samples after warmup: update P95 0.487 ms, render P95 6.129 ms, combined P95 6.594 ms, median 4.446 ms. This workload does not include the separate native bridge; all 14 native start probes stayed below 3 ms worst observed I/O in the final suite.
- Th06PerformanceTest includes the actual companion, Java collisions and drawing during original Misdirection: peak 640 hostile bullets, update P95 0.337 ms, render P95 5.678 ms, combined P95 5.971 ms. It observes the entry/nonspell and uses forced damage once to enter the dense phase, then measures 600 frames after 240 warmup frames. No conclusion about every possible hardware configuration follows from this one machine.
- Boss HUD/damage notices no longer expose move or Spell Card names. Original arena clipping, player HP bar, native bullets/knives and the new selector are rendered for visual inspection.

## Historical prototype validation — 2026-10-20 (Boss v2)

## Reproduce

~~~powershell
.\tests\run-tests.ps1 -Benchmark -Playtest
~~~

Requires JDK 17+ on PATH. No third-party test dependencies or assets are needed. The script prints the temporary output directory for reports and renders.

## Logic and UI

- GameplayTest: 321 assertions for input, swept collisions, hit immunity, Dash, Bomb, graze, pickups, EXP, build caps, progression, deterministic RNG and pause clocks.
- BossReworkTest: maxed items excluded across 100 sampled choice screens; explicit current/max metadata; evolved items remain separate choices; player-independent ring gaps; real curved trajectories; freeze/aim/relaunch behavior; locked dash telegraph; phases cannot clear even with a 20-minute phase clock.
- GameMenuTest: settings, difficulty lock, Esc repeat, focus loss, input routing, level-up preservation and six menu/result renders.
- BossPreviewTest: real schedules rendered for all six phases, plus a representative 3/5 → 4/5 weapon / 1/3 → 2/3 passive screen.
- Compilation with Java 17 compatibility and lint checks, plus whitespace inspection.

## Pattern challenge

Each of the 18 difficulty × encounter × phase combinations runs for 45 simulation seconds. Offense and combat upgrades from graze-earned level-ups are disabled only in this isolated observation fixture; HP, movement, damage, invulnerability and Bomb stock are not overridden. The steering bot uses no Bombs.

All 18 survive the observation window. This is not the old requirement of waiting for a phase timeout: boss phases remain alive and never advance until defeated.

| Difficulty | Maximum observed hit count | Maximum visible/simulated bullets |
| --- | --- | --- |
| Easy | 3 | 416 |
| Normal | 3 | 549 |
| Hard | 6 | 840 |

Standing still is tested separately without offense or defensive overrides. All six Normal phases and all six Hard phases kill the stationary character within the 45-second observation window. Five of six Easy phases also do so; the remaining phase leaves 8 HP.

The bot reads visible wall gaps and telegraphed dash direction. The final boss includes recovery after a complete dash chain to avoid indefinitely spawning dense volleys beside a player pinned near the bottom edge.

These tests establish substantially more pressure than the previous sparse patterns and show survivable no-Bomb windows. They do not prove that every trajectory, long fight or skill level is balanced.

## Full runs

Seed 20261020 on Normal, starting at level one. The bot uses real movement and legal upgrade/reroll/Bomb choices; no health, damage, stat or timer overrides.

| Build priority | Outcome | Simulation time | Ending HP | Level | Bombs used | Captures |
| --- | --- | --- | --- | --- | --- | --- |
| Target damage | Victory | 11:09 | 100 | 47 | 2 | 5/6 |
| Area damage | Victory | 11:37 | 100 | 47 | 2 | 5/6 |
| Zone control | Victory | 11:47 | 100 | 47 | 2 | 5/6 |

Unlike the previous prototype, no boss phase is skipped by waiting. These times are measured results, not enforced run durations. Human playtesting is still needed to judge feel and satisfying difficulty.

## Performance

Machine: Intel Core i9-13900HX; RTX 4080 Laptop / Intel UHD graphics available. Benchmark: 1000×750 BufferedImage software rendering, 300 live enemies + 2,000 projectiles, 180 warm-up frames and 600 samples.

| Measurement | Milliseconds |
| --- | --- |
| Update P95 | 1.152 |
| Render P95 | 13.538 |
| Combined P95 | 14.557 |
| Combined median | 8.018 |
| Target | <16.7 |

The benchmark includes gameplay update, broad-phase queries, collision and drawing, and excludes replenishing test entities between samples. It checks this machine's workload, not a universal live-window FPS guarantee.

Boss Test: BossPracticeTest passed for both encounters, selected seed/difficulty, level-4 starter build, uninterrupted practice, three HP-based phases, pause replay, Enter retry and return to normal runs. Start/chooser/pause/result renders checked at 1000 x 750.

## Original boss phase-health regression — 2026-10-21

The earlier prototype run times above predate original ECL integration and the new phase-health budget. GameplayTest now passes 325 assertions, including silent player damage, preserved notices and shared immunity. Th06HealthTest checks Rumia and Cirno on all three difficulties: each first spell starts with a full phase-local bar, an exact threshold hit advances correctly, and five seconds of scaled 600 DPS removes half of the minimum phase budget. With actual default practice weapons on Normal, the first Spell Card takes 27.2 seconds for Rumia and 25.1 seconds for Cirno. Those stationary offensive simulations use player invulnerability to isolate damage throughput; they are not survival playtests.

Th06IntegrationTest passes all 14 encounters. Th06FullMovesetTest passes 42 encounter/difficulty combinations and three additional Normal Patchouli routes, preserving complete spell sequences. Th06BehaviorTest passes native determinism, Bomb, laser collision, pause, replay and process cleanup. Full-run balance has not been remeasured after increasing phase HP.

Bullet silhouette update: inspected a ten-type geometry contact sheet and a real Sakuya midboss Misdirection frame; native kunai are visibly pointed and aligned with the authoritative angle. GameplayTest (325 assertions) and Th06BehaviorTest pass. Native dense-pattern benchmark at 640 hostile bullets: update P95 0.352 ms, render P95 8.086 ms, combined P95 8.420 ms on this machine. Round types use primitive-circle rendering to avoid unnecessary rotated paths: the 300-enemy/2,000-projectile field benchmark passes at combined P95 7.994 ms.

Boss v3: EosdPatternTest verifies shared knife release timing on all difficulties and proportional HP-bar width/color during blinking. GameplayTest (321 assertions), BossReworkTest, BossPracticeTest and BossPreviewTest pass. All 18 isolated 45-second scenarios survive without Bomb; all six Normal stationary fixtures die. Three Normal bot runs win in 11:10 / 11:37 / 11:47 using 0 / 1 / 2 Bombs. These are automated checks; human balance playtesting remains useful. Updated 300-enemy/2,000-projectile benchmark: update P95 1.036 ms, render P95 12.809 ms, combined P95 13.820 ms (600 samples after warmup).
