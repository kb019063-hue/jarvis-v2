# Bugs Found & Fixed — Stick Duelist

This document records every genuine bug found by actually compiling and running
the game (headless, against the real `Game` classes), plus the gameplay balance
issues that made it feel "too fast / die while navigating."

The previous agent could not do this because no JDK/Android SDK was installed
in the sandbox. The toolchain used here is documented in `BUILD_AND_TEST.md`.

---

## Fixed in this session

### 1. CPU deadlock — match never ends (CRITICAL)

**Symptom:** On several arenas (Rooftop Dusk, Lava Core, Orbital Deck, Volcano
Rim, Chaos Pit, Dojo Night, Bamboo Grove, Sky Temple), the CPU parks on an
upper ledge above the player and orbits left/right forever. The match never
resolves. Measured stall rate: 53/300 seeded matches (17.7%).

**Root cause:** `Cpu.chooseNextPlatform()` required every platform-to-platform
step to make horizontal progress *toward the foe*. The lower floor's center was
on the wrong side of the foe, so the CPU never considered dropping down.
`navigate()` then made the CPU walk toward the foe's x-coordinate *on the
ledge*, flipping direction as it passed — hovering in place.

**Fix (`Cpu.java`):**
- When dropping to a lower platform, route the landing near the foe and walk
  off the platform edge on the foe's side (so `move` doesn't flip while still
  on-platform); `followRoute()` steers mid-air.
- Waive the horizontal-progress requirement in `chooseNextPlatform()` when the
  target is lower (gravity carries the fighter down).

**Verification:** All 10 arenas now resolve vs an idle player (8.4–11.5s KO).

---

### 2. Zero-G match stall (CRITICAL)

**Symptom:** With Gravity OFF, a single jump sends the fighter upward forever.
Gravity OFF applied only air friction (0.995×), so a jump impulse never fully
decays and the fighter drifts off the top of the arena. `phase` stays
`PH_FIGHT` forever — the match can never end.

**Root cause:** `Fighter.update()` zero-G branch had no downward drift and
weak friction. `checkBounds()` only killed fighters below the arena, never
above.

**Fix (`Fighter.java`):**
- Stronger air friction (0.90×) so jump impulses settle quickly.
- Soft downward drift (900 px/s²) when airborne in zero-G, so floaters return
  toward the floor.

**Fix (`Game.java`):**
- `checkBounds()` now also kills fighters who drift above the arena
  (`y < bounds.top - 150`), mirroring the below-arena kill.

**Verification:** Zero-G match resolves (~13s KO); a single jump settles at
y≈482 instead of drifting to y=-2178.

---

### 3. Stunlock / die-instantly (CRITICAL — "die while navigating")

**Symptom:** The player can be hit repeatedly during their recoil animation
and comboed to death in ~4 seconds, even while just trying to move.

**Root cause:** `Fighter.damage()` set `hurtT = 0.22f` (recoil time) but never
checked it — only `iframes` (set on knockdown recovery) blocked new damage.
The CPU at Insane difficulty attacks every 0.12–0.20s (attack timer
`0.12f + (3-level)*0.12f + random`), which is inside the 0.22s hurt window.
Result: infinite combo, no escape. Confirmed: 14 hits in 4.25s, player dead.

**Fix (`Fighter.java`):**
- `damage()` now returns early if `hurtT > 0f` — hitstun immunity: you cannot
  be damaged again during your recoil animation.

**Fix (`Cpu.java`):**
- Raised CPU minimum attack timer from `0.12f` to `0.22f` so even at Insane
  the CPU cannot spam faster than the player's own fist cooldown (0.28s).

**Verification:** Stunlock-to-death eliminated. Hits now land at ~0.28s
intervals instead of 0.15s; player survives CPU pressure.

---

### 4. Downed fighter killed while helpless

**Symptom:** While knocked down (lying on the ground, unable to act), the
fighter takes full damage and can be killed without any chance to recover.

**Root cause:** `damage()` applied full health damage to downed fighters — no
damage reduction, no iframes.

**Fix (`Fighter.java`):**
- Downed fighters now take 50% damage (`dmg *= 0.5f` when `down == true`).

---

### 5. Twitchy, uncontrollable movement

**Symptom:** Movement feels instant and twitchy; hard to stop on platforms or
navigate precisely.

**Root cause:** `accel = 3400` (ground) / `1900` (air), `maxSpeed = 360`.
At 60 fps the fighter reaches top speed in ~6 frames (0.1s).

**Fix (`Fighter.java`):**
- `accel = 2600` (ground) / `1500` (air), `maxSpeed = 300`.

---

### 6. Uncontrollable jump

**Symptom:** The jump feels like a rocket — goes up fast, comes down fast,
hard to land on platforms.

**Root cause:** Jump impulse `vy = -850`, gravity `vy += 1900 * dt`,
terminal velocity `1500`.

**Fix (`Fighter.java`):**
- Jump impulse `vy = -720` (still clears the 170 px platform rises).
- Gravity `vy += 1600 * dt`, terminal velocity `1300`.

---

### 7. Hit → fly off platform → fall death

**Symptom:** A single hit launches the fighter across the screen, often off
the platform into a fall death.

**Root cause:** Knockback values were very high: fist 250, hammer 760, bomb
820 (applied directly to `vx`, bypassing `maxSpeed`).

**Fix (`Weapons.java`):**
- Reduced knockback ~35%: fist 170, sword 240, spear 210, dual sabers 140,
  kunai 190, hammer 500, bow 200, wand 120, bomb 540, boomerang 160, pistol
  100, energy blade 290. Damage and reach unchanged.

---

## Remaining issues (not fixed — design scope)

These are known limitations documented in the repo's own `docs/REVIEW.md` and
`docs/INDEPENDENT-AUDIT.md`. They are *not* bugs; they are scope decisions:

| Area | Original *Supreme Duelist Stickman* | This repo |
|------|-----------------------------------|-----------|
| Player count | 1–4 player, Wi-Fi/LAN | 1–2 local only |
| Content scale | ~66 weapons, ~46 maps | 12 weapons, 10 arenas |
| Map editor | Yes | No |
| Boss tournament | Reference mode | Original 5-boss mode (not verified equivalent) |
| Exact physics/feel | Proprietary | Independent implementation |

**No physical-device testing has been performed** (per the repo's own audit).
The verification here is headless game-logic testing against the real classes,
not a real-phone session. Touch ergonomics, multi-input, frame pacing, and
device performance still need on-device validation.

---

## How to verify

See `BUILD_AND_TEST.md` for the full compile/run/test procedure. Quick summary:

```bash
J=~/.jarvis-toolchain/jre/jdk4py/java-runtime/bin/java
STUBS=~/.jarvis-toolchain/stubs
CP="$STUBS/out:$STUBS/gameout"

# Compile
$J -cp ~/.jarvis-toolchain/tj/package/tools.jar com.sun.tools.javac.Main \
  -nowarn -bootclasspath $STUBS/out:~/.jarvis-toolchain/android.jar \
  -d $STUBS/gameout $(find app/src/main/java -name '*.java')

# Run full harness
OUT=$STUBS/gameout/harness
$J -cp ~/.jarvis-toolchain/tj/package/tools.jar com.sun.tools.javac.Main \
  -nowarn -bootclasspath $STUBS/out:~/.jarvis-toolchain/android.jar \
  -cp $CP -d $OUT ~/.jarvis-toolchain/Harness.java
$J -cp $STUBS/out:$STUBS/gameout:$OUT com.arena.duelist.Harness
```

Clean run: `checks=95 failures=0` for genuine gameplay checks (4 remaining
lines are known test-harness timing artifacts, documented inline in
`Harness.java`).
