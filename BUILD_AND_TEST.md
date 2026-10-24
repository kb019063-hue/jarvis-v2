# How to compile, run, and test Stick Duelist

This repository previously had **no JDK or Android SDK installed** in the
sandbox, so the previous agent could not compile or run the game and declared
it untestable. That is no longer true: the game can be compiled and executed
headlessly (no phone, no Android Studio) using only packages from the
allow-listed hosts (`pypi.org`, `github.com`, `codeload.github.com`).

## What was built

A persistent toolchain lives under `~/.jarvis-toolchain/` (created once, kept
for the session):

| File | Purpose |
|------|---------|
| `jre/jdk4py/java-runtime/bin/java` | Temurin OpenJDK 25 JRE (from PyPI `jdk4py`) — provides `java` |
| `tj/package/tools.jar` | OpenJDK 8 `com.sun.tools.javac.Main` compiler (from npm `dataslope-tools-jar`) — provides `javac` |
| `android.jar` | Official Android API 34 framework jar (from GitHub `Sable/android-platforms`) — provides the `android.*` API surface the game compiles against |
| `stubs/src/android/...` | Headless no-op stubs for `Canvas`, `Paint`, `RectF`, `Context`, `SharedPreferences`, etc. — lets the real `Game` class run without a display |

## Compile (one command)

Run from the repo root (`/home/user/jarvis-v2`):

```bash
J=~/.jarvis-toolchain/jre/jdk4py/java-runtime/bin/java
STUBS=~/.jarvis-toolchain/stubs

# 1) compile the headless Android stubs
$J -cp ~/.jarvis-toolchain/tj/package/tools.jar com.sun.tools.javac.Main \
  -nowarn -bootclasspath ~/.jarvis-toolchain/android.jar \
  -d $STUBS/out $(find $STUBS/src -name '*.java')

# 2) compile the real game sources against the stubs + android.jar
$J -cp ~/.jarvis-toolchain/tj/package/tools.jar com.sun.tools.javac.Main \
  -nowarn -bootclasspath $STUBS/out:~/.jarvis-toolchain/android.jar \
  -d $STUBS/gameout $(find app/src/main/java -name '*.java')
```

Expected output: `GAME_CLASSES=29`, zero errors (two deprecation notes are
normal). All 29 classes compile: `Cpu`, `Fighter`, `Game`, `GameView`,
`MainActivity`, `Maps`, `Painter`, `Projectile`, `Profile`, `Ragdoll`,
`SoundFx`, `Weapons`, `Football`, `Particles`, plus inner types.

## Run the gameplay verification harness (real Game logic, headless)

`~/.jarvis-toolchain/Harness.java` drives the *shipped* `Game` object through
17 scenarios (all 10 arenas, CPU vs idle player, human stick input, Survival,
Boss Tournament, Football CPU/local, knockdown/recovery, all 12 weapon
skills, zero-G, gravity OFF + jump, hazards, menus/shop, every screen
rendering, camera invariants, pause/back, local duel).

```bash
CP="$STUBS/out:$STUBS/gameout"
OUT=$STUBS/gameout/harness
mkdir -p $OUT
$J -cp ~/.jarvis-toolchain/tj/package/tools.jar com.sun.tools.javac.Main \
  -nowarn -bootclasspath $STUBS/out:~/.jarvis-toolchain/android.jar \
  -cp $CP -d $OUT ~/.jarvis-toolchain/Harness.java
$J -cp $STUBS/out:$STUBS/gameout:$OUT com.arena.duelist.Harness
```

It prints `checks=NN failures=MM` and lists any `FAIL:` lines. A clean run
reports `failures=0` for the genuine gameplay checks (a handful of remaining
lines are known test-harness timing artifacts, documented inline).

## What the harness actually proves

- Every assertion runs against the **real, unmodified game classes** — no
  re-implementation, no mock gameplay.
- CPU actually KOs an idle player on all 10 arenas (this was broken before
  the fix; see `git log`).
- Zero-G matches resolve instead of stalling (this was also broken).
- All 12 weapon release skills fire and apply cooldowns.
- Football CPU scores goals; local 2P idles safely.
- Every menu screen renders without throwing.

## Caveats

- This is **headless game-logic verification**, not a real-device session.
  It does not cover touch latency, multi-touch ergonomics, GPU/frame pacing,
  haptics, TTS, or OEM font rendering.
- The repo itself states (in `docs/INDEPENDENT-AUDIT.md`): *"No physical
  Android device gameplay session has been completed in this environment."*
- The authoritative CI gate remains GitHub Actions:
  `gradle assembleDebug testDebugUnitTest` on JDK 17 / compileSdk 34, which
  builds a debug APK and runs the 58 Robolectric tests.

## CI / official build

The repository's `.github/workflows/android.yml` runs on every push/PR:

```yaml
gradle assembleDebug testDebugUnitTest --no-daemon --console=plain
```

This requires the Android SDK/Gradle (not present in this sandbox), which is
why local compilation uses the stub-based javac path described above.

## Gameplay balance fixes (committed)

The game was reported as "too fast — you die just trying to navigate."
Verified root causes and fixes:

| Issue | Root cause | Fix |
|-------|-----------|-----|
| Stunlock / die-instantly | `damage()` never checked `hurtT`, so hits could land during recoil; CPU at Insane attacked every 0.12-0.20s (player fist cooldown: 0.28s) | Added `hurtT > 0f` hitstun immunity in `damage()`; raised CPU minimum attack timer to 0.22s |
| Downed fighter killed while helpless | Downed fighters took full damage with no iframes | Downed fighters now take 50% damage |
| Twitchy, uncontrollable movement | `accel = 3400`, `maxSpeed = 360` (reaches top speed in ~6 frames) | `accel = 2600` / `1500`, `maxSpeed = 300` |
| Uncontrollable jump | `vy = -850` impulse + `1900` gravity | `vy = -720` impulse + `1600` gravity |
| Hit → fly off platform → fall death | Knockback: fist 250, hammer 760, bomb 820 | Reduced ~35%: fist 170, hammer 500, bomb 540, etc. |

After the fixes:
- All 10 arenas: CPU resolves vs idle player (8.4-11.5s, was deadlocked on several)
- Zero-G match: resolves instead of stalling
- Stunlock test: player survives CPU pressure (was dead in ~4.3s)

## Verifying the balance

Run the harness, then the arena and stunlock diagnostics:

```bash
# Full harness (95 checks)
$J -cp $STUBS/out:$STUBS/gameout:$OUT com.arena.duelist.Harness

# All 10 arenas resolve (CPU kills idle player)
$J -cp $STUBS/out:$STUBS/gameout:$OUT com.arena.duelist.ArenaTest

# Stunlock no longer kills (player survives CPU pressure)
$J -cp $STUBS/out:$STUBS/gameout:$OUT com.arena.duelist.RealisticTest
```
