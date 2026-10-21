# TH06 ECL companion

Java/Swing owns the game. This isolated, frame-stepped CPU process executes original ECL, boss-specific ex-instructions, ANM timing metadata, projectile motion and laser state. It has no graphics/audio backend and no crate dependencies.

Upstream: [TheScreamer74/Touhou6](https://github.com/TheScreamer74/Touhou6), revision in `UPSTREAM`. `src/ecl.rs`, `anm0.rs`, `anm_vm.rs` and most of `ecl_vm.rs` are vendored from that revision. `physics.rs` adapts its `Stage::update_bullets`. The MIT notice is retained at `../../licenses/Touhou6-engine-MIT.txt`.

SVBK additions: stable bridge IDs, source timeline entry parameters, original enemy/bullet/laser pool limits, fractional damage transfer and HP-threshold protection, dialogue-wait handling, disabling HP-based timeouts while retaining survival endpoints, process protocol and cleanup. The companion fixes native rank at 16; it is not a complete original-game emulator or a verified frame-perfect oracle.

Protocol 3 exports phase-local current/max HP: the segment ends at the next life callback, with a fresh starting pool after that callback. Java applies a minimum boss-phase budget and difficulty scaling, converting dealt damage back to source units. An exact threshold hit triggers the callback, including when the displayed segment reaches zero.

Build on Windows with Rust stable GNU and the GNU/MinGW linker:

```powershell
.\native\th06-host\build.ps1
```

The script writes a content-addressed executable and `host-path.txt` under `%LOCALAPPDATA%/SVBK/TH06Reference/`; rebuilding does not overwrite a running encounter's executable. A Rust toolchain installed in `%LOCALAPPDATA%/SVBK/BuildTools/` is also recognized without modifying global PATH. No binary or original game data belongs in the repository.

Protocol version 3, big endian: startup magic/version, then one request/reply per 60 Hz Java tick. Requests contain player position, Bomb, accumulated per-actor damage and consumed projectile IDs. Replies contain completion, phase serial, spell IDs/end events for statistics, actor state, projectile state and beams. Spell names are never sent to the UI. EOF terminates the companion. `Th06Runtime` enforces handshake/frame timeouts and closes processes on encounter replacement or JVM exit.

Data provenance, roster, intentional combat adaptations and verification: [TH06_PORT.md](../../docs/TH06_PORT.md).
