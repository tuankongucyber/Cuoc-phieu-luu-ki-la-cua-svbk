# Bundled Boss data

`th06-boss-data.zip` is shipped with the game and copied into the Java classpath by `java/run.bat`. JAR builds should place it at the JAR root. Plain project/IDE launches also look in `java/resources/` and `resources/`.

The archive contains only 21 files (295,116 bytes uncompressed): seven ECL scripts, two shared ANM metadata files and twelve stage-enemy ANM metadata files. No textures, music, game executable or native library is included. Runtime code is entirely Java. Archive size: 53,413 bytes; SHA-256: `14498320dbffe1f901d6cb5a81670b1df7b9a7fc646b48b750e39992f6b5561b`.

Original TH06 data: Team Shanghai Alice / ZUN. Extracted from the previously verified `CNTianQi233/th06-sdl2` community data pack, release `Archived-fix1.1.0`. Data provenance and source-pack SHA-256: [TH06_PORT.md](../../docs/TH06_PORT.md). The engine's MIT license applies to the adapted interpreter code, not to the original game data.
