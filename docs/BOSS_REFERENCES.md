# Historical mixed-pattern prototype

These six mixed phases are retained as explicit legacy test fixtures, not the current playable boss roster. Production uses the original-script encounters described in [TH06_PORT.md](TH06_PORT.md). This account documents the earlier prototype.

## Sources actually inspected

- [TH06 reverse engineering, EnemyEclInstr.cpp](https://github.com/GensokyoClub/th06/blob/master/src/EnemyEclInstr.cpp): inspected `ExInsStage56Func4` (time-stop flag and bullet direction changes) and `ExInsStage5Func5` (dagger formations). Used to understand the mechanism; no C++ implementation copied. The new Java clock is a gameplay adaptation, not a frame-accurate reproduction of an original spell.

- [Touhou Tales of Danmaku](https://github.com/NyuxRGB/Touhou-Tales-of-Danmaku), pinned revision [166d57edb42e7bf1ec4a6b70bc62e51ae733ce9c](https://github.com/NyuxRGB/Touhou-Tales-of-Danmaku/tree/166d57edb42e7bf1ec4a6b70bc62e51ae733ce9c).
- [Cirno phase 4](https://github.com/NyuxRGB/Touhou-Tales-of-Danmaku/blob/166d57edb42e7bf1ec4a6b70bc62e51ae733ce9c/src/patterns/data/stage2/cirno/phase4.json): layered circles that freeze, change to aimed shots and accelerate again.
- [Meiling phase 3](https://github.com/NyuxRGB/Touhou-Tales-of-Danmaku/blob/166d57edb42e7bf1ec4a6b70bc62e51ae733ce9c/src/patterns/data/stage3/meilingboss/phase3.json): multiple colored circle-fan streams and staged speed changes.
- [Meiling phase 6](https://github.com/NyuxRGB/Touhou-Tales-of-Danmaku/blob/166d57edb42e7bf1ec4a6b70bc62e51ae733ce9c/src/patterns/data/stage3/meilingboss/phase6.json): layered fountain/thread sprays, circular secondary fire and edge pressure.
- [OrbitingBullet](https://github.com/NyuxRGB/Touhou-Tales-of-Danmaku/blob/166d57edb42e7bf1ec4a6b70bc62e51ae733ce9c/src/entities/bullets/OrbitingBullet.ts): polar coordinates and independent angular/radial motion.
- [Kenta Cho's BulletML reference](https://www.asahi-net.or.jp/~cs8k-cyu/bulletml/bulletml_ref_e.html): composed repeat/fire/wait actions, changing direction and speed.

The Java code is a new implementation of these pattern-design ideas adapted to this game's wider arena, auto-attacks, HP and dash. It is not a decompilation or extraction of an official Touhou executable. No external engine, art or audio was imported. The upstream MIT notice is retained under licenses/.

## Six movesets

| Boss / phase | New behavior |
| --- | --- |
| Mid 1 — Băng giao cắt | Icicle Fall-inspired crossing side fans with staggered speeds, followed by aimed central pressure and a gap ring; six-second schedule |
| Mid 2 — Vòng quỹ đạo giao nhau | Two displaced emitters, curved rings and gaps independent of player aim |
| Mid 3 — Đóng băng / Tái khởi động | Three staggered rings stop, visibly lock a snapshot direction, then accelerate out; aimed secondary fire |
| Final 1 — Scarlet / Chuỗi hồng đạn | Scarlet Shoot-inspired radial bead strings with five speeds and alternating curvature, then aimed volleys and recovery; six-second schedule |
| Final 2 — Đồng hồ / Dao ngưng đọng | Sakuya-inspired four clock layers placed around the arena, frozen together then launched simultaneously in crossing directions; knife-shaped visuals and follow-up aimed knives; seven-second schedule |
| Final 3 — Phong tỏa chuyển động | Descending walls with moving gaps, spiral layers and telegraphed dash chains |

Every phase loops until its HP reaches zero. Elapsed phase time is bookkeeping only, never a win/loss/phase-change trigger. Capture requires no damage and no Bomb in that phase, with no deadline. The HUD shows HP percentage in place of a countdown.

Dashes lock direction when the warning starts. After a complete dash chain the boss gets a one-second repositioning recovery without new fire, so it cannot remain at the lower edge and spawn dense volleys next to the player indefinitely. Existing bullets remain active during recovery.

The old rotating ring gave the player a free safe lane by aiming its gap directly at them. New gaps follow world-time schedules instead. Ring/flower/spiral density, bullet curvature and staggered speeds provide the challenge rather than simply multiplying HP.

The clock knives use four staged placements but a shared release beat. Their directions are visible while frozen; their collision radius stays at 4 px despite the elongated drawing. The side-fan and scarlet spells alternate attacks and recovery inside longer schedules, with orientation changes across repetitions. Player HP is also drawn as a proportional green/amber/red bar above the character and remains visible during damage blinking.

## Upgrade cards

Owned max-rank weapons and passives are excluded before selection. Valid upgrades carry explicit current/max metadata, shown as e.g. 3/5 → 4/5. A new item shows 0/5 → 1/5. An evolution is a different item replacing the base slot, so it remains eligible after the base weapon reaches its cap.
