# 0.3.2 — playability and physics fixes

This pass follows an independent review of the 0.3.1 build. It fixes five
defects that made matches unplayable or that silently cancelled half of the
combat model. Every claim below was produced by running the real game classes
headless, not by reading the code.

## What was broken

### 1. A seeded CPU could park on a ledge and never fight (critical)

The CPU's platform router only accepted a candidate platform if stepping onto
it moved the CPU *horizontally toward* the opponent. A drop straight down to
the opponent therefore failed the test, while the drop branch in `navigate()`
only ran when a candidate had already been chosen. The result was a CPU that
walked to the edge of a side ledge, found no legal "progress", and stood still
with `move = 0` until the round timer or the player ended it.

Measured before the fix: **31 of 120 seeded matches (26 %) never produced a
K.O.** — Volcano Rim 6/12, Orbital Deck 5/12, Chaos Pit 5/12, Rooftop Dusk
4/12, Neon Alley 3/12, Lava Core 2/12, Sky Temple 2/12, Frozen Lake 2/12,
Bamboo Grove 1/12, Dojo Night 1/12.

Fix (`Cpu.chooseNextPlatform`, `Cpu.navigate`):

- The horizontal-progress filter is skipped for downward steps
  (`rise < -24 px`). Descending is progress; the range test still applies.
- A drop now plans a real exit: the CPU walks to `exitX` just outside the
  edge of the platform it is standing on, then lands on the lower platform
  aiming at the opponent's x instead of a fixed 80 px offset.

Measured after the fix: **0 stalls in 600 seeded matches** (60 seeds x 10
arenas), 0 CPU self-deaths, 0.98 jumps per match on average (max 6).

### 2. Hazard avoidance treated a floor as a hazard (high)

`hazardDanger` only asked whether lava overlapped the CPU's body in x, so a
lava pool *below a solid floor* looked lethal. The CPU then refused to walk
across the bridge above it and retreated indefinitely — the same "paces at
the edge" symptom, on the arenas with ledges over hazards.

Fix: `Cpu.hasFooting()` checks for a non-hazard platform under the feet; when
one exists, hazards whose top is below the feet are ignored.

### 3. Gravity OFF removed the ability to come down (critical)

With the house rule off, `Fighter.update` skipped gravity entirely, so a
single jump carried the fighter upward forever. Measured apex: **1363 px above
the top of the arena**, still rising at 4 s. The fighter never returned, the
opponent could not reach it, and a zero-G match could not end.

Fix (`Fighter`): gravity OFF is now a floaty low-gravity rule —
`ZERO_G_GRAVITY = 450 px/s^2` with 5 %/frame air damping, plus a sky clamp at
`bounds.top + 140`. A full jump now peaks 177 px and lands back at the floor
in about 0.9 s, and a zero-G match resolves in roughly 36 s.

### 4. Hitstun cancelled the walk controller's knockback (high)

`Fighter.damage()` applies knockback as `vx`, but the movement controller ran
on the next frame and rewrote `vx` toward the player's stick input at
360 px/s. Any shove weaker than the walk speed was erased within 2–3 frames.
A fist hit (250 px/s) displaced the victim **11 px**; a hammer (760 px/s)
managed 182 px; dash releases had almost no reach because the dash velocity
was overwritten the moment the input was still held.

Fix: an `impulseT` window (0.20 s after a hit, the full `attackDur` after a
dash release) during which the controller brakes toward zero at
`IMPULSE_BRAKE = 1200 px/s^2` instead of steering, so the impulse is
preserved and decays visibly.

Measured isolated knockback, both fighters idle: fists 24 px (was 11),
sword 47 px, energy blade 66 px, hammer 182 px, bomb 206 px. Dash reach:
fists 138 px (was 78), sword 238 (was 119), energy blade 280 (was 34),
spear 325 (was 154).

### 5. A knockdown was a death sentence (medium)

A downed fighter could not block, could not move, and still took full damage for
the whole recovery ragdoll (measured: a 20-damage hit removed exactly 20 HP).
While down, incoming damage is now halved.

### 6. Hitstun also stopped you steering (medium)

`canMove` was false for the whole hurt window, so being hit meant losing all
control for up to 0.5 s — the "stunlock" feel reported on the phone. Being hit
now *slows* movement (x0.45) instead of freezing it, while attacks are still
blocked and the shield still cannot be raised instantly.

## Verification

There is no Android SDK, Gradle distribution or device in the authoring
sandbox, so the checks were:

- **Static compile** of all 29 game classes (`javac` 1.8 against a 34 API
  stub set) — exit 0 after every change.
- **Headless simulation harness** (`tools/verify/`) driving the real
  `Game`/`Fighter`/`Cpu` classes: 600 seeded matches, isolated knockback
  measurement, zero-G jump trace, and a mirror of the existing
  playability assertions (35 checks, 0 failures).
- **Test suite** extended from 58 to 63 methods: gravity OFF is now asserted
  to be *gentle* rather than *absent*; a ledge-descent test, a zero-G
  containment test, a light-knockback test and a seeded end-to-end match test
  were added.

The Robolectric suite itself was executed by GitHub Actions, not locally. It
ran green, including all 62 tests and the screenshot renderer:

https://github.com/kb019063-hue/jarvis-v2/actions/runs/40100593158

## Still open (not fixed here)

- **Weapon balance.** Dual sabres take a full health bar to zero in under ten
  seconds; the energy blade's live knockback measured lower than the fist's
  in a moving match. Both are tuning, not defects.
- **Light weapons barely shove.** 24 px for a fist is now *correct* (it is a
  fast, low-commitment weapon) but it still feels inert compared with the
  original, where every hit sends the ragdoll sliding.
- **Content parity** is unchanged — see `docs/FIDELITY-AUDIT.md`. This build
  has 12 weapons and 10 arenas against roughly 66 and 46 in the original, and
  no 3–4 player play, map editor or online play.
- **No real-phone pass.** Frame pacing, touch feel and default difficulty are
  still unverified on hardware.
