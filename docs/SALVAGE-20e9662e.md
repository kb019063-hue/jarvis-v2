# Salvage review of `arena/20e9662e-jarvis-v2` (commits `a3717cc`, `b740630`)

The branch was presented as a session that had "found and fixed" seven gameplay
bugs, with a `BUGS_AND_FIXES.md` to match. It was never merged and **both of its
commits failed CI**:

- `a3717cc` — [run 40045270916](https://github.com/kb019063-hue/jarvis-v2/actions/runs/40045270916): `gravityOffStopsFighterAcceleration` failed.
- `b740630` — [run 40046111197](https://github.com/kb019063-hue/jarvis-v2/actions/runs/40046111197): additionally `poiseBreaksAfterFourJabs` and `fighterCanJumpThroughAndLandOnThinPlatform` failed.

Every claim below was re-measured by compiling three builds from source — the
original main (`5a046d1`), that branch (`b740630`), and this branch — and running
the same headless probes against each.

## Verdict table

| # | Claim | Re-measured result | Verdict |
|---|---|---|---|
| 1 | CPU deadlock: CPU parks on a ledge, match never ends | main: 31/120 seeded matches stall; branch: 0/300; this branch: 0/600 | **Valid** — already fixed here |
| 2 | Zero-G match stall: a jump floats away forever | main: apex 1745 px above the arena, never returns; branch: returns to ground; this branch: returns | **Valid** — already fixed here |
| 3 | Stunlock: hits during recoil chain into an inescapable combo | longest continuous hitstun on main: 0.28 s (fists), 0.55 s (dual sabers, and that is the knockdown ragdoll); 68–88 % of frames were outside hitstun; their fix changed death time from 7.97 s to 7.42 s | **Not reproducible** — rejected |
| 4 | Downed fighter takes full damage and can be killed helpless | main: a 20-damage hit on a downed fighter removed exactly 20 HP | **Valid** — adopted here (50 %) |
| 5 | Movement is twitchy: `maxSpeed` 360 → 300, accel 3400 → 2600 | No defect measurable; pure feel change that shortens every weapon's relative reach | **Taste, not a bug** — rejected |
| 6 | Jump is a rocket: `-720` impulse, gravity 1600, terminal 1300 | Measured apex 156 px, but Rooftop Dusk's ledge is 170 px above the floor — the fighter can no longer land on it. Their own CI failed this exact test | **Wrong** — rejected, it is a regression |
| 7 | Hits launch players off platforms: knockback reduced ~35 % | Measured fist knockback on main: **11 px**. There was no launch to reduce; the branch's own build moves the victim 15 px, and lowering `knock` further would make hits weightless again | **Wrong premise** — rejected |

## Why each rejection, in detail

**#3 stunlock.** The report describes a passive player standing next to an
Insane CPU and dying in about four seconds. That does happen — an idle fighter
in melee range of the hardest AI dies. It is not a stunlock: `hurtT` never
stayed above zero for longer than one recoil (0.22 s) plus the knockdown body
(0.55 s), and the player had most frames free.

The proposed fix — an early return in `damage()` whenever `hurtT > 0` — also
swallows legitimate simultaneous hits (two fighters trading blows, a projectile
arriving during recoil) and breaks the poise rule that the suite guards:
`poiseBreaksAfterFourJabs` failed on their branch. Measured survivability barely
moved (7.97 s → 7.42 s against fists), so the fix does not achieve its stated
goal either.

This branch already reduces the underlying complaint without side effects:
being hit slows movement to 45 % instead of freezing it, and the surviving
knockback impulse pushes the victim out of the CPU's reach (measured hits in a
fixed 6 s window: 6 → 3).

**#5 movement tuning.** "Feels twitchy" is a legitimate opinion but not a defect,
and the constants interact with arena geometry: slower movement changes how far
every dash and knockback carries relative to a walk.

**#6 jump tuning.** This is the clearest regression. The claim "`-720` still
clears the game's 170 px platform rises" is arithmetically wrong: the apex of a
`-720` impulse under 1600 px/s² is `720² / (2·1600) = 162 px`, below 170. The
measured apex was 156 px. The existing test `fighterCanJumpThroughAndLandOnThinPlatform`
failed on their branch for exactly this reason.

**#7 knockback.** The branch's premise was that hits were launching players off
platforms. On main, a fist hit moved an idle victim 11 px and a hammer 182 px —
the opposite problem: light weapons did nothing. Cutting the table by 35 % would
have made that worse. The real cause was the walk controller overwriting the
impulse every frame; that is fixed here instead.

## What was actually salvaged

- **Downed fighters take half damage** (`Fighter.damage`), with a new test
  `downedFighterTakesHalfDamage` covering both the reduced hit and the unchanged
  damage on a standing fighter.
- Nothing else. Claims 1 and 2 were independently found and fixed on this
  branch before this review, with equivalent or safer mechanics (a sky clamp
  instead of killing a fighter that drifts above the arena).
- The two documentation files on that branch are superseded by this note and by
  `IMPLEMENTATION-0.3.2.md`.

The branch itself is left unmerged: its fixes are either already present here or
were regressions.
