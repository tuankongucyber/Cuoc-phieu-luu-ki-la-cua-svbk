# Gameplay contract: Danmaku Survivors

One character, one world, two bosses per survival run, plus 14 original TH06 practice encounters. Java/Swing, 1000×750, with a synchronous CPU companion for original ECL scripts. Geometric placeholders only; audio and art production remain outside this milestone.

## Deterministic simulation

GameWorld is the authoritative 60 Hz simulation. GamePanel routes input, menus and rendering, while SimulationClock converts elapsed wall time into fixed ticks. Catch-up is capped at five ticks per callback; paused and upgrade screens accumulate no time debt.

RunDirector owns independent field, warning and phase clocks. Rendering does not mutate simulation state or consume RNG. A seed reproduces a run when the sequence of inputs and upgrade choices also matches.

A tick performs input/movement, Bomb, director transitions, enemy movement/attacks, weapon updates, relative swept collisions, deaths/pickups, then one level-up transition. Mutations of projectile/enemy lists occur outside their traversal. SpatialGrid supplies broad-phase candidates and deduplicates objects spanning several cells. Ninja checks nearby friendly projectiles once per enemy, never once per projectile.

## Player

| Action / property | Rule |
| --- | --- |
| WASD / arrows | Normalized eight-direction movement, 3.5 px/tick |
| Hold Shift | 45% movement speed; reveal the unchanged hitbox |
| X | 120 px dash over 9 ticks; 120-tick cooldown; current or last direction |
| Dash collision | No invulnerability. Relative swept collision checks the entire movement segment |
| Space | Bomb: clear existing hostile bullets, 450 damage to enemies within 900 px, at least 60 ticks of invulnerability |
| Bomb stock | Start 2, cap 3; mid-boss completion restores 1 |
| Bullet hitbox | Circle radius 4 px |
| Contact hitbox | Circle radius 12 px, plus each enemy's body radius |
| HP | 100; damage grants 45 ticks of invulnerability, independently of hit-flash visuals |
| Graze | Bullet enters radius 18 plus its own radius without hitting; once per bullet |
| Graze reward | 10 points; 1 EXP per 10 grazes, maximum 2 graze EXP per simulation second |
| Graze exclusions | Invulnerability, dash, dead player, Bomb-cleared bullets |

All player damage passes through Player.hit. Every hostile projectile keeps a source label, and the result screen shows the last damaging source. Bullets expire and are culled at distance, while offscreen rendering is skipped.

## Run and encounters

- Field clock 00:00–05:00: progressive spawn intervals, first melee enemies, then ranged attackers.
- A 120-tick warning freezes the field clock, but combat continues until arena entry.
- Mid-boss: Cirno's complete difficulty-specific script sequence, without HP-phase timeouts.
- Field resumes at 05:00 and continues to 09:00.
- Another 120-tick warning, then Remilia's complete script sequence, without HP-phase timeouts.
- Total length depends on how quickly the player defeats each phase. Field combat lasts nine minutes; boss combat has no deadline. Menu time is excluded.

Arena entry clears enemies/bullets and banks uncollected field EXP. Build, health, score, Bomb stock and cooldowns persist. The original 384×448 arena is scaled uniformly by 1.34; player movement is clamped to it. ECL owns phase transitions, movement, nonspell/spell order, auxiliary entities, bullets, time-stop and lasers. RunDirector owns transitions to/from field combat.

HP-based phases cannot end by waiting. Original survival cards retain their scripted endpoint, avoiding an untargetable Flandre softlock. Intro dialogue is skipped after entry movement. A completed clean Spell Card grants capture and 2,500 points; nonspells do not count as captures. Completing the boss grants 1,000 points. Mid-boss completion heals 25 HP and grants one Bomb (cap 3) plus 80 EXP. Completing Remilia wins; each practice ends after its own selected encounter.

### Boss patterns

Each original encounter executes its own complete ECL sequence. HP and damage retain the game's difficulty multipliers. Bullet radii derive from original type metadata; beams have a narrower collision core and swept player-path tests, including dash. Warning-only beams are harmless. Bomb clears native and Java hostile state. Ordinary weapons do not erase bullets. Neither HUD nor damage notices reveal move/Spell Card names.

The boss health bar resets for each phase and measures HP down to its next scripted transition. Small inherited Spell Card pools are expanded to a minimum of 6,000 effective HP before difficulty scaling (Easy 4,500 / Normal 6,000 / Hard 8,400); larger original pools remain. Damage is converted back to ECL units so the original phase order and patterns stay intact. Taking damage does not display a yellow notification; player HP and hit flash provide feedback, and the result screen keeps the final damage source.

Hostile bullet silhouettes distinguish pellets, hollow rings, rice, orbs, kunai, shards, fireballs and knives. Native bullets select their silhouette from the original etama3 type and keep their scripted angle even while stopped. Outlines follow the silhouette rather than adding a round background. Field aimed volleys use kunai, fans use rice, gap rings use hollow rings and spirals use shards. These geometric visuals do not change collision/graze radii, trajectory or damage.

Source, full roster, protocol/build instructions, adaptations and validation boundaries: [TH06_PORT.md](TH06_PORT.md). Mixed patterns in [BOSS_REFERENCES.md](BOSS_REFERENCES.md) remain explicit legacy test fixtures, not a production fallback.

## Build economy

- Start with Magic Wand. Four weapon slots and four passive slots.
- Five base weapons, each capped at level 5: Wand, Orbit, Aura, Piercing, Chain.
- Three valid, distinct choices per level-up; two rerolls per run. Already-maxed upgrades are omitted, and rank metadata is displayed as current/MAX → next/MAX. Eligible evolutions are guaranteed on the next choice screen, including rerolls.
- EXP cost is 10 + 5 × (current level − 1); surplus is retained. Multiple earned levels open one screen at a time.
- Full builds fall back to healing 25 HP when injured and distinct score rewards when no useful upgrades remain.
- Statistics count effective damage, excluding overkill; evolved and replaced weapons both remain in the result breakdown.

| Passive (cap 3) | Bonus per rank, added from base |
| --- | --- |
| DAMAGE | +10% damage |
| COOLDOWN | −8% cooldown |
| AREA | +10% range / area |
| AMOUNT | +1 projectile, orbit blade or chain target, where applicable |
| SPEED | +5% movement speed |
| MAGNET | +20% pickup radius |

| Evolution | Requirement | Result |
| --- | --- | --- |
| PRISM | Wand 5 + Cooldown 3 | Four-ray penetrating fan, four targets per ray |
| DUAL_ORBIT | Orbit 5 + Area 3 | Six blades across 70 / 120 px orbits |

Evolutions replace their base weapon in the same slot, have a single completed rank, and prevent reacquiring that base. Both aimed projectile weapons retain a central ray even at even projectile counts.

## Difficulty

Difficulty is selected before the run. Field enemies retain the multipliers below. Original bosses select native Easy/Normal/Hard branches plus existing HP/damage multipliers; the old generic boss warning/dash/count/speed adjustments are not layered onto those scripts. Extra uses its fixed original Extra branch. Player hitboxes and controls stay fixed. The generic bullet/warning/dash rows below apply only to field/prototype fixtures, not original ECL bosses.

| Rule | Easy | Normal | Hard |
| --- | --- | --- | --- |
| Enemy HP | ×0.75 | ×1 | ×1.4 |
| Enemy damage | ×0.7 | ×1 | ×1.35 |
| Enemy speed | ×0.8 | ×1 | ×1.2 |
| Spawn interval, initial → minimum (ticks) | 85 → 22 | 65 → 15 | 45 → 10 |
| Bullet count adjustment | −2, minimum 1 | Base | +2 |
| Bullet speed | ×0.85 | ×1 | ×1.12 |
| Ninja dodges / recovery ticks | 1 / 40 | 2 / 25 | 3 / 16 |
| Boss warning ticks | 45 | 30 | 22 |
| Boss dash cooldown ticks | 300 | 220 | 150 |
| Consecutive boss dashes | 1 | 1 | 2 |

Safety limits: 300 field enemies, 6,000 total projectiles, 2,000 ground drops, 24 damage labels and 64 lightning effects. At the drop cap, new EXP merges into an existing gem rather than disappearing. These are protection limits, not desired encounter densities.
