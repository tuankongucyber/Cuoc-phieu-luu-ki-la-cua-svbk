# Original Touhou 6 movesets — Java interpreter and source audit

The target is one intact moveset per original boss, including nonspells, Spell Cards, phase order and movement. Production now executes each selected encounter's own ECL sequence. The earlier mixed three-phase encounters remain only as explicit legacy fixtures; they were not relabeled as original bosses.

## Target roster

- Rumia — stage 1, including the midboss encounter.
- Daiyousei — stage 2 midboss.
- Cirno — stage 2 boss.
- Hong Meiling — stage 3 midboss and boss.
- Koakuma — stage 4 midboss.
- Patchouli Knowledge — stage 4 boss and Extra midboss; spell routes depend on player shot type.
- Sakuya Izayoi — stage 5 midboss/boss and stage 6 midboss.
- Remilia Scarlet — stage 6 boss.
- Flandre Scarlet — Extra boss.

Do not force all encounters into three phases or add another character's attack to fill a slot. Preserve each difficulty's own script branches. HP damage, the player's existing HP bar, Bomb and controls remain part of this game's combat system. Boss timeout removal remains the user's explicit gameplay modification; it must be separated from attack timing.

## Why the remake source was rejected

[daikoko/Re_EoSD](https://github.com/daikoko/Re_EoSD/tree/5cb6bc4927296793ed559cf7a2b3b3e10c605692) is a fan remake, not the original script set. Its `Game/Entities/_Text/BossText.json` and per-stage boss resources were inspected: Cirno has Glacial Draw and Nine Armed Snowflake, for example, and the main-boss Normal sequence is NonA1 → SpellA → NonA2 → SpellB → SpellC. This is not the original complete Cirno sequence. Porting it verbatim would still fail the request. None of those scripts were imported into gameplay.

## Initial data inspection tools

- `Th06Archive`: reads the original PBG3 archive directory and extracts entries in memory using LZSS; never modifies the source game or writes extracted data.
- `Th06Ecl`: validates bytecode boundaries and inventories subroutines, raw instruction parameters/difficulty masks and original CP932 Spell Card declarations.
- `Th06Inspect`: command-line inventory of an original stage archive, game directory or extracted `.ecl`; reports SHA-256, instruction counts and declarations. It explicitly reports that it is an inventory, not an ECL runtime.

Compile with the existing JDK, then run `java -cp <build-directory> Th06Inspect "<original-game-directory-or-stage-archive>"`. The original Japanese filename ending in `ST.DAT` and patched `th06*_ST.dat` are both accepted.

Format references inspected:

- [GensokyoClub/th06 EclManager.hpp](https://github.com/GensokyoClub/th06/blob/6a5acc02df4b5a6da9b9fc409facd7b655a27542/src/EclManager.hpp), including the 12-byte instruction header, 16-byte file header, opcodes and Spell Card declaration layout.
- [thpatch/thtk thdat06.c](https://github.com/thpatch/thtk/blob/master/thtk/thdat06.c) and [thlzss.c](https://github.com/thpatch/thtk/blob/master/thtk/thlzss.c), PBG3 bit-packed directory and 8 KiB LZSS dictionary. The upstream notice is retained in `licenses/thtk-BSD.txt`.

## Real-data verification

Downloaded the 0.13 trial from [ZUN's official TH06 page](https://www16.big.or.jp/~zun/html/th06.html), temporary location only. The trial contains only stages 1–3. Real PBG3 extraction and ECL inventory pass:

| File | Subroutines | Instructions | Spell declarations | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| ecldata1.ecl | 24 | 644 | 3 | 9d9a40e9f7e3ab9346d3874438134659cacf9d34f4aff57b96b4be4ea85b99d7 |
| ecldata2.ecl | 33 | 565 | 4 | aa89aaaf13264d909bbaaa24f2af263ae1a58145ad5cc33fcea5eb9cb920c43e |
| ecldata3.ecl | 35 | 838 | 7 | 44dfcaa737b7d4b3d1275655627e15a9d5b7710fc75a0885fb39d8c9485056d6 |

Cirno's original declarations include Icicle Fall, Hailstorm, Perfect Freeze and Diamond Blizzard. These are inventory results across difficulty branches, not a claim that all four occur on every difficulty.

## Full dataset acquisition — 2026-10-21

The user authorized downloading the missing data. Downloaded the public community port's [stage data pack](https://github.com/CNTianQi233/th06-sdl2/releases/tag/Archived-fix1.1.0), asset `th06-sdl2-game-data.zip`. This is a community redistribution, **not an official download**. The full archive is kept in `%LOCALAPPDATA%/SVBK/TH06Reference/` outside the Git workspace. No downloaded executable was run. The script/ANM metadata subset is now shipped as the small offline resource described below. No original textures or audio are included.

Downloaded ZIP SHA-256: `b65ce0913227f555978f9c453d2303782aa096c75109d251db598ed7b4718441`, matching GitHub's asset digest. Source URL and digest are saved in local `source.json`.

The stage archive contains `ecldata1.ecl` through `ecldata7.ecl`, with 64 Spell Card declarations across difficulty branches. It is Chinese-localized; use `Th06Inspect <archive> --charset GBK` to decode names. The explicit charset affects display only.

`Th06Verify` compares subroutine instructions while excluding only the 34-byte displayed Spell Card name. It retains instruction time, opcode, difficulty mask, parameter length and all other parameters, including jumps, spell IDs, movement and firing settings. It is not a full-file or timeline comparison. Against the official 0.13 trial, all three shared stage scripts match:

| Script | Gameplay SHA-256 |
| --- | --- |
| ecldata1.ecl | 0a2341c47392a1c6cb8b84c7dba93831cd6202103ce728a48eb515b8fb44cc54 |
| ecldata2.ecl | 0f393b9338683744b2df91b103ed1fff535de089c48dc95e5835ec384d1d63a9 |
| ecldata3.ecl | a40a0a6ea02cf8501ce7291d67d57d834895f86e46920a6cf86dcbfb88035b3b |

Stages 4–7 are available and inventoried; there is no independent official full-game comparison for them yet. The local `inventory.txt` records all seven scripts' raw and normalized hashes plus their declarations.

## Playable integration

The interpreter is now implemented entirely in Java, adapted from [TheScreamer74/Touhou6](https://github.com/TheScreamer74/Touhou6/tree/e9d9316f31127f9105aa41ae7cb5d0d7d9a7aee1), revision `e9d9316f31127f9105aa41ae7cb5d0d7d9a7aee1`. The MIT notice is retained in `licenses/Touhou6-engine-MIT.txt`. The previous Rust companion and its build script have been removed from the repository. No downloaded game executable runs.

`Th06Vm.java` implements ECL opcodes, contexts/call stacks, per-boss ex-instructions, the original 16-bit RNG, movement/interpolation, life/survival callbacks, enemy/bullet/laser pools, time-stop and projectile physics. `Th06Anm.java` reads ANM v0 metadata and executes control flow, sprite selection, interrupts and deferred random-sprite requests. Pure visual transforms and invisible particle positions have no gameplay consumers and are omitted; effect lifetimes and RNG draws are retained. `Th06Runtime.java` adapts typed Java snapshots to the existing player combat, rendering and statistics.

`java/resources/th06-boss-data.zip` ships all 14 encounters' required data: 21 ECL/ANM files, 295,116 bytes uncompressed and 53,413 bytes packaged. SHA-256: `14498320dbffe1f901d6cb5a81670b1df7b9a7fc646b48b750e39992f6b5561b`. `Th06BundledData` reads the classpath resource, verifies its hash and flat allowlist, and atomically prepares the writable local cache. `run.bat` and the test runner copy the ZIP into their build output; JAR builds place it at the JAR root. Plain project/IDE launches also search their adjacent resource directory. No network, full data pack, original executable, texture or audio is required to play.

`Th06Setup.java` prefers this bundled resource. The earlier verified-download/manual-import path remains only as recovery for incomplete custom distributions lacking the resource; `svbk.th06.offline=true` disables that recovery download. Start checks stages 2/6, practice checks its selected stage, and both include shared/secondary ANM metadata. Preparation runs in a SwingWorker, continues the requested start after success, and does not launch an abandoned request after navigation. The full 54 MiB archive is no longer downloaded during normal startup.

Compile all `java/*.java` with `javac --release 17 -encoding UTF-8`; run `Main`, or use `java/run.bat`. Only the standard JDK is required. No Rust compiler, native executable, JNI, pipe protocol, worker process or additional Java library is used. ECL/ANM files remain external input data. One `GameWorld` tick advances the interpreter one frame in the same JVM. Pause/Settings/Level Up stop the simulation; restart and return to Start release its state. ECL instruction loops and spawn recursion have explicit budgets.

`Th06BossId` lists 14 entry points: six main stages, Flandre, Daiyousei, Koakuma, Rumia/Meiling/Sakuya midboss variants and Extra Patchouli. The UI selects one of four original shot routes for Patchouli. Initial HP/coordinates come from the original timeline. ECL owns phase transitions; the old three-phase director does not. Survival runs use Cirno and Remilia. Missing data is reported before starting.

### Intentional adaptations

- Original 384×448 coordinates are uniformly scaled by 1.34.
- Existing 100 HP, focus, dash, auto weapons and shared immunity remain. Boss HP is measured per phase, down to its next ECL life callback, rather than against the preceding phase's total. Each boss phase has at least 6,000 effective HP before the existing difficulty multiplier (4,500 Easy / 6,000 Normal / 8,400 Hard). Larger source pools are retained. Java converts damage back into source HP units, preserving original thresholds, order and attack timing. Auxiliary enemies retain their source HP. Attack scripts use difficulty 0/1/2; Extra uses its original branch 4.
- HP-based phases cannot automatically time out. Original survival cards retain their scripted endpoint; otherwise Flandre's untargetable survival card would never finish.
- Dialogue is skipped only within intro-wait subroutines, after entry movement. Invulnerable spell windups are not repeatedly restarted.
- Rank is fixed at 16; the original RNG uses the seed's low 16 bits. Original dynamic rank and item/progress economies are not imported.
- Queued damage applies on the next simulation tick. HP thresholds clamp large damage so a strong build cannot skip an original spell.
- Geometric actor, bullet and knife rendering replaces sprites. ANM files provide timing/size metadata only; no bitmap or audio assets are rendered or committed.
- Original limits of 640 hostile bullets, 64 lasers and 256 enemy slots are enforced. Java field-combat limits remain separate.
- HUD shows boss identity and phase-local HP only. Player hits use the health bar and hit flash without a yellow damage notice; the defeat screen retains the final damage source. Clean original Spell Cards grant captures; nonspells do not.

### Verification boundary

All 14 encounters were stepped through all three selected difficulties using real ECL. Forced damage between observation windows exercises transitions: all 42 scenarios finish, Flandre emits all ten cards, Remilia Normal all five, and Cirno Normal all three. Four Normal Patchouli routes finish with their own card sequences. Clean capture counts match completed declarations. This is phase coverage, not evidence that a player has beaten every encounter.

During migration, a temporary comparison harness advanced the previous Rust executable and the Java VM with identical moving-player inputs, Bombs at frames 900/2100, and forced damage every 480 frames. Across 45 scenarios it compared 123,360 frames, 175,992 actor states and 12,904,251 bullet states, including phase/status, spell events, pool counts, identities, flags, HP, coordinates, angles and laser states. All discrete values matched. The largest finite numeric difference was 0.00097656 source pixels; matching nonfinite values inherited from the previous interpreter were treated as equivalent. This establishes migration parity for those traces, not correctness against the original executable.

`tests/th06-replay-reference.tsv` retains 514 certified checkpoints from those traces, including counts, HP, sampled coordinates and exact RNG state. `Th06JavaReplayTest` replays them using only Java and checks that no child process is created. Integration tests cover seeded trajectories, Bomb cancellation, harmless laser telegraphs, swept laser/dash collision, shared immunity, Pause, the 14-choice menu, replay, Enter retry, state disposal and normal-run Cirno selection. The earlier full-run timings predate the increased phase-health budget and are historical, not current balance measurements.

No frame-for-frame match against the original executable is claimed: the interpreter is independently reconstructed and the adaptations above remain. Only the small script/metadata subset is included in Git; full game archives, textures, audio and executables are excluded. Old companion executables in an existing developer cache are unused; they are not read, loaded or launched by the Java implementation.
