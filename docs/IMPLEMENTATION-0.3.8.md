# 0.3.8 — nothing allocated on the draw path

Road-map item 7: *"per-frame `Path`/`RectF` allocation in draw."*

Every frame of play built a fresh blade path, a fresh hair path, a fresh arc
`RectF` and a fresh `Paint` for each fighter, a whole new `Painter` (with three
`Paint`s) for the canvas, a `Btn` plus `RectF` for every menu button, and a
`Path` for every background hill or spike. On a phone that is garbage on every
frame — around 900 objects a second in a duel and nearly 3,000 a second on a
spiky map — and the collections that come with it land in the middle of a
fight, which reads as a stutter.

## How it is measured

`tools/verify/Draw.java` renders real frames headlessly. The stub graphics
classes in the harness toolchain count every `Path`, `RectF` and `Paint`
constructor, so the probe reports objects **created per drawn frame**, per
scenario, with no profiler and no device.

The same probe, run against the commit before this pass and against this pass:

| Scenario | before | after |
|---|---|---|
| Title screen | 13.00 objects/frame | **0.00** |
| Local duel, Dojo Night | 15.00 | **0.00** |
| Local duel, spike arena | 47.00 | **0.00** |

At 60 fps those are 780 / 900 / 2,820 objects per second before, and none after.
The probe fails its own assertion if the count ever climbs again, so this is a
regression test and not just a measurement.

## What changed

| Site | Was | Now |
|---|---|---|
| `Fighter.drawBlade` | `new Path()` per blade per frame | one `scratchPath`, `reset()` |
| `Fighter.drawHairAt` | `new Path()` per head with spiky hair | same `scratchPath` |
| `Fighter` bow / boomerang arms | `new RectF()` + `new Paint()` per frame | `scratchRect` + `scratchArc` |
| `Game.draw` | `new Painter(canvas)` — three `Paint`s — every frame | one `Painter`, `beginFrame()` restores its state |
| `Game` hills / spikes | `new Path()` per hill (7) and per spike (20) per frame | one `drawPath`, rebuilt in place |
| `Game` thumbstick ring, projectile triangle/diamond | `new RectF()` / `new Path()` | reused `drawRect` / `drawPath` |
| `Game` menu buttons | `new Btn()` + `new RectF()` per button per frame | pooled `Btn` list with a live count |
| `Painter.oval` | `new RectF()` per oval | one `ovalRect` |
| `Ragdoll` hair | `new Path()` per corpse per frame | one `hairPath` |

Reusing the `Painter` is only safe because `beginFrame` restores exactly the
state a fresh `Painter` had — `p.reset()`, `line.reset()`, `text.reset()` and
the constructor's configuration. `Painter.reset()` is a different method that
only clears alpha and shaders and is called *inside* frames; it was not
changed.

The menu-button pool needed care: `handleMenuTap` used to walk
`uiBtns.size()`, which was the live count because the list was cleared at the
start of every draw. It now walks a separate `uiBtnCount`, and the "Back"
guard that stops a tap landing on a stale hitbox still works because every
screen change zeroes that count.

## Verification

**81 tests** (was 80). The new one draws the title forty times and then taps
through to a match, which is exactly the failure mode pooling could introduce.
It was mirrored in `Draw.java` and passed against the shipped code first.

| Suite | Result |
|---|---|
| `Draw` | 2 checks, 0 failures (0.00 objects/frame on all three scenarios) |
| `TestMirror` | 21 checks, 0 failures |
| `Risky` | 35 checks, 0 failures |
| `WeaponHandling` | 13 checks, 0 failures |
| `Harness` | 15 checks, 0 failures |
| `ShieldTests` | 4 checks, 0 failures |
| `ClockTests` | 7 checks, 0 failures |
| `Sweep` (300 matches) | 0 stalls, 0 CPU self-deaths |
| `Stall` (300 matches) | 0 stalls |
| `Clock` | all four scenarios end |
| `Balance` | fair-TTK spread unchanged at 9.7× |

One harness problem surfaced while running this battery: the "sword dash
covers ground" check read anywhere from 38 px to 311 px across runs, because it
measured the dash from wherever the random spawn left the fighter and the
opponent sometimes wandered into the dash. It failed roughly once in ten runs
and would have made the battery look red at random. The opponent is now parked
and frozen and the dash starts from the middle of the arena: **251 px, ten runs
out of ten**. That was a flaw in the measuring tool, not in the game.

The headless `Game` is now compiled against the stub graphics classes so it can
render; `build.sh` still compiles the full app against the real `android.jar`
first and prints `ANDROID_API_CHECK`, so a stub signature drifting away from
the real API fails the build rather than the phone.

## What this does not prove

The harness draws into a no-op `Canvas`, so this measures the game's own
allocation and CPU, not GPU rasterisation or frame pacing on real hardware.
Device-level smoothness remains untested here (audit row 9).

## Metadata

Version 0.3.8, versionCode 12.
