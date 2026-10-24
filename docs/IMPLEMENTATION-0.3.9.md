# 0.3.9 — bigger arenas, and a camera that frames them

> "Improve the maps. The maps are not well built, too small. Horrible design
> etc. So you are focusing on maps this round. Also make sure you make the
> camera work smartly too… if the characters are far apart, either the camera
> zooms out so as to see both characters."

Two separate defects sit behind that complaint, and the second one made the
first look worse than it was.

**The arenas were small.** Every map was 1,300–1,500 px wide and 820 tall, with
exactly three surfaces: one floor and two 180–240 px ledges at a single height.
Both spawn points sat on that floor 440 px apart, so a fight always happened in
the middle third of a room barely one and a half screens wide.

**The camera was almost always fully zoomed out.** On a 1,400 px map, the
`fit` that keeps both fighters on screen was larger than the map itself, so the
camera sat at zoom 0.71–0.81 for the whole match and the entire arena was on
screen at all times. Nothing was ever off-camera, so nothing felt big. A larger
map on its own made this *worse*, not better: the frame filled with more empty
world beyond the arena.

## The arenas

Ten rebuilt arenas, 1,900–2,400 px wide and 940–1,080 tall, **51 platforms**
(41 of them above the floor), **three tiers** everywhere, and a floor that runs
under the whole fighting area. Eight hazards across the four hazardous maps,
all on the outer flanks.

| Arena | Size | Platforms | Tiers | Hazards | Floor span |
|---|---|---|---|---|---|
| Rooftop Dusk | 2200 × 980 | 5 | 3 | — | 0..2200 |
| Neon Alley | 2100 × 950 | 5 | 3 | — | 0..2100 |
| Lava Core | 2400 × 1050 | 5 | 3 | 2 lava | 100..2300 |
| Sky Temple | 2200 × 1000 | 5 | 3 | — | 0..2200 |
| Frozen Lake | 2300 × 1000 | 5 | 3 | — | 0..2300 |
| Bamboo Grove | 2000 × 980 | 6 | 3 | — | 0..2000 |
| Orbital Deck | 2400 × 1060 | 5 | 3 | 2 spikes | 200..2200 |
| Volcano Rim | 2400 × 1080 | 5 | 3 | 2 lava | 200..2200 |
| Dojo Night | 1900 × 940 | 5 | 3 | — | 0..1900 |
| Chaos Pit | 2400 × 1060 | 5 | 3 | 2 lava | 150..2250 |

Design rules the geometry is built on, and checked by `MapAudit`:

- **Every tier is one flight of stairs apart.** `jump()` has `vy = -850`, a
  170 px apex; every rise between tiers is 120–140 px, so no tier needs a
  precision jump. The top ledge always has a mid-ledge beneath it.
- **The spawn lane is solid and hazard-free** on every map: no hole and no
  hazard between `spawnAx - 40` and `spawnBx + 40` at floor height (0/20 checks
  failed). This is also a Robolectric assertion.
- **Every platform is reachable from the floor**, verified by a real physics
  probe that jumps and steers, not by a distance formula: 41/41 above-floor
  platforms, 0 unreachable.
- **Ledges are wider than the fighter's spawn check.** `safeSpawnX` requires a
  platform wider than 92 px; the narrowest spawnable ledge is 160 px, the
  typical ledge 300–420 px.

**Correction, flagged.** Earlier in this round I reported "all 46 platforms
reachable". The arenas contain 51 platforms, 41 of them above the floor; 46 was
a stale count from an intermediate version of the tool. The reachable figure is
41/41.

## The camera

Three changes in `Game.updateCamera`:

| Change | Was | Now |
|---|---|---|
| Framing margin around the pair | 100 px each side | 70 px |
| Zoom floor | `ZOOM_MIN` (0.40) | `max(ZOOM_MIN, whole-arena fit × 0.92)` — never zooms out past "the map fills the frame" |
| Horizontal pan | unbounded | clamped to the arena bounds, and centred when the view is wider than the map |
| Vertical pan | unbounded | at most 150 px of sky above the map, at most 250 px of world below its floor |
| Backdrop | hills/bamboo/skyline built to a fixed ~1,500 px span | repeated to the arena's own width |

The zoom floor is what makes the zoom-out *useful* instead of endless: the
camera stops widening at the point where the whole arena is on screen, so when
the fighters separate it zooms out exactly as far as the map allows and no
further.

Measured at 1600 × 720 with the two fighters pinned to opposite arena edges
(`MapAudit`):

| | 0.3.8, old maps | old maps, today's camera | 0.3.9 |
|---|---|---|---|
| Zoom when split | 0.71–0.81 | 0.74–0.85 | **0.46–0.58** |
| Visible world width | 1,478–1,696 px | 1,413–1,630 px | **2,065–2,609 px** |
| Both fighters on screen | 10/10 | 10/10 | 10/10 |
| Dead framing (view outside the arena) | — | 8 % | **5–8 %** |

Visible width grew 43–60 % against the old geometry with the *same* camera, and
28–54 % against what 0.3.8 actually shipped — while the dead-framing figure
stayed at the old level, because the zoom floor and the pan clamp both stop the
camera before it walks off the map.

**Correction, flagged.** Earlier in this round I measured "empty area 11–15 %".
That number came from a vertical clamp that pushed the floor down behind the
on-screen controls: at 960 px width the fighters' feet ended up at 433/540 of
the screen instead of the required ≤ 360, which would have broken
`cameraKeepsSeparatedFightersClearOfHudAndThumbAreas`. That clamp has been
replaced, the HUD framing now passes at all four widths (0 failures), and the
honest dead-framing number is 5–8 % above.

## CPU navigation, and why `Cpu.java` was touched

The maps round was scoped as "no `Cpu.java` behaviour changes". That had to be
partly given up, and here is the exact size of it.

Bigger maps with three tiers exposed a routing bug that the old three-platform
maps hid. `chooseNextPlatform` picked the next platform by a greedy score. The
moment the CPU crossed the opponent's x, the score's winner flipped, so the CPU
walked out to one ledge, changed its mind, walked back, and repeated. Frozen in
place, it never reached the player:

```
t=15s P(196,770) C(201,630) route=1->2 (180-440 top 660) -> (560-780 top 540)
t=16s P(196,770) C(201,630) route:gap   move=1.0 jump=false
t=17s …identical…
```

It is now a breadth-first search over the platform graph (`canStep`: rise ≤
175 px, gap ≤ 260 px). The answer depends only on the two platforms, so it is
stable while the CPU stands on one. **Nothing else in `Cpu.java` changed** — no
aggression, reaction, accuracy or weapon constants, and the CPU still never
raises its shield (still reserved for the difficulty pass).

| Metric | 0.3.8 | 0.3.9 |
|---|---|---|
| `Stall`: 300 seeded 90 s matches | 4 stalls | **0** |
| `Sweep`: 300 matches | 4 stalls, jumps max 45, avg 2.72 | **0 stalls, jumps max 0, avg 0.00** |
| `CpuClimb`: CPU reaches a player parked on each upper platform | not measured | **41/41**, 4.0–7.4 s |
| `Countdown`: hit landed inside 15 s on every map × 3 seeds | not measured | **30/30**, 4.3–4.6 s, 0 jumps |

`Sweep` jumping zero times is not a regression: with an idle floor-bound
opponent the shortest route is along the floor, and the old 45 jumps a match
were the dithering loop. `CpuClimb` proves the vertical navigation still works
by parking the player on every upper platform in turn.

## Verification

Everything below was run from `tools/verify` against the compiled game classes.

| Probe | Result |
|---|---|
| `MapAudit` — geometry, spawn lane, 41-platform jump reachability | 0 unreachable, 0 holes, 0 hazards in spawn lanes |
| `MapAudit` — camera at the arena edges | zoom 0.46–0.58, both on screen 10/10, dead framing 5–8 % |
| `MapAudit` — HUD-safe framing at 960/1280/1600/2160 px | 0 failures (8 checks) |
| `CpuClimb` | 41/41, 4.0–7.4 s |
| `Countdown` | 48/48 (30 spawn-to-hit + 18 melee-weapon contacts) |
| `Stall` | 0/300 |
| `Sweep` | 0 stalls, 0 self-deaths |
| `Zoom` — real matches, 5 seeds per arena | zoom 1.45 (in close combat), 0 clipped fighter-frames of ~69,000 |
| `Risky` / `Harness` / `TestMirror` | 35 / 15 / 21 checks, 0 failures |
| `WeaponHandling` / `ShieldTests` / `ClockTests` / `Draw` | 13 / 4 / 7 / 2 checks, 0 failures |
| `Balance` | fair-TTK spread 4.8×, unchanged (Tier-2 freeze respected) |
| `Clock` | duel ends at 572 s, football at 124 s |

One test-tooling repair was needed: `WeaponHandling` placed both fighters at a
hard-coded `y = 590` written for the old 640-top floor and a drop at
`floorTop - 12`. On an 800-top floor that dangled the fighters in mid-air and
left the pickup 168 px below them, which showed up as five failures in the
drop/swap checks. The probe now derives its y from the map's floor; the game
code was not at fault.

## What this does not do

The arenas are still rectangles on a grid: no slopes, ladders, moving
platforms, breakable floors or per-map props, and still no map editor. Hazards
are still lava, spikes and water rather than the original's variety. There are
still ten arenas, against ~46 in the original.

Version 0.3.9, versionCode 13.
