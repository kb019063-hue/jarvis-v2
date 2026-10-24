# Headless verification harness

The 0.3.2 fixes were measured **outside** Robolectric, because the environment
where they were written had no Android SDK, no Gradle distribution and no
device. These programs compile the real game classes and drive them headlessly,
which is enough to prove physics and CPU-navigation behaviour but not touch
feel, frame pacing or rendering.

They are kept here as reproducible evidence, not as part of the app.

## Layout

| File | What it does |
|---|---|
| `Harness.java` | 15 broad gameplay assertions (match resolution, zero-G, stunlock, knockback, dash reach) |
| `Sweep.java` | Seeded sweep of full matches; prints stalls, self-deaths and jump counts |
| `Stall.java` | 12 seeds x 10 arenas stall sweep with per-arena breakdown (the pre-fix 31/120 figure) |
| `Measure.java` | Isolated knockback, dash reach and zero-G hop with both fighters idle |
| `TestMirror.java` | Headless mirror of the four tests added to `PlayabilityTest` |
| `Risky.java` | Headless mirror of the existing tests most at risk from the changes (35 assertions) |
| `Audit.java` | Weapon TTK spread, unlock economy, shield absorption, football, survival ramp |
| `Audit2.java` | Ammo exhaustion, silent weapon-pickup swap, shield break and regen |
| `Audit3.java` | Where weapon drops land, football stalemate risk |
| `Balance.java` | Per-weapon time-to-kill against an idle **and** a moving target, 9 seeds each |
| `WeaponHandling.java` | Magazine reload vs the old forced-fists downgrade, sustained fire rate, and the deliberate DOWN-to-swap drop rule |

## Toolchain note

`/tmp` is not preserved between sandbox restarts. Rebuild the toolchain into a
persistent directory (`/home/user/.toolchain`) with `/home/user/build.sh`, which
compiles the game and then everything in this folder. Compile the harness with
`-bootclasspath <stubs>:<android.jar>` so the stub `Context` wins over the real
abstract class, and put `reset()` before `giveWeapon()` in probes because
`Fighter.reset()` sets the weapon back to fists.

## How they were run

`Game` and `Fighter` only need a `Context` for `SharedPreferences` and audio, so
the harness links against a small set of hand-written `android.*` stubs. The
stubs must be on the **bootclasspath** (not the classpath), otherwise
`android.content.Context` resolves to the real abstract class from
`android.jar` and `new Context()` fails to compile.

```bash
# game classes
javac -source 8 -target 8 \
      -bootclasspath android-stubs:android-34.jar \
      -classpath android-stubs \
      -d out $(find app/src/main/java -name '*.java')

# harness
javac -source 8 -target 8 \
      -bootclasspath android-stubs:android-34.jar \
      -classpath out \
      -d harness-out tools/verify/*.java

java -cp android-stubs:out:harness-out Sweep
```

All keyboard/pointer input is simulated through `Game.pointerDown/Move/Up`, and
randomness is pinned with `Game.debugSeed(long)` so a failure is reproducible.
Round state is read through the `debug*` accessors in `Game`.
