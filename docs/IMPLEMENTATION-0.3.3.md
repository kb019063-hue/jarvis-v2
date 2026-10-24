# 0.3.3 — "everything is too fast": what was actually wrong

The complaint was that the whole game runs fast: the AI, your own attacks, the
movement. This pass measures the tempo instead of guessing at it.

## First: it is not a clock bug

The simulation cannot run faster than real time.

- `GameView.GameThread` accumulates *measured* elapsed time and converts it to
  fixed 1/60 s steps, capped at 5 steps per frame, and drops the backlog after
  a long hitch rather than fast-forwarding.
- `Game.tick` clamps `dt` to 50 ms and is the only caller of the fighter/
  camera/projectile update; there is exactly one `tick` call site in the app.
- So 600 ticks are 10.00 s of game time, always.

Measured ground speed and attack cadence matched the constants exactly, which
confirms the clock: 360 px/s of walking, 3.50 attacks/s for fists. Nothing is
being double-stepped.

## What was genuinely too fast

Holding the one stick auto-attacks (`applyStick` requests an attack every frame
while the stick is past 0.32), so **the weapon cooldown is the game's tempo**,
not the animation. The 0.3.1 table fired the light weapons 3.5–4.6 times a
second, and the hit window opened three frames after the input.

| Measurement | 0.3.2 | 0.3.3 |
|---|---|---|
| Pistol attacks/s | 4.6 | 2.7 |
| Dual Sabers attacks/s | 4.3 | 2.5 |
| Fists attacks/s | 3.5 | 2.2 |
| Sword attacks/s | 2.3 | 1.7 |
| Wind-up before a hit can land | 50 ms | 100 ms |
| Walk speed | 360 px/s | 320 px/s |
| Ground acceleration | 3400 px/s² | 2700 px/s² |
| Jump apex / time to a 170 px ledge | 183 px / 0.55 s | **unchanged** |
| Idle player vs Insane CPU, 8 s, fists | 12 hits, 2 HP left | 7 hits, 38 HP left |
| Idle player vs Insane CPU, dual sabers | K.O. at 6.6 s | K.O. at 7.0 s |

Every weapon keeps its damage, reach and knockback; only the cadence and the
wind-up changed. Attacks still start instantly on input; what got longer is the
time before the blade is dangerous, which is what makes a swing readable and
punishable instead of an instant tap.

## Why the previous attempt did not fix it

The unmerged branch cut `maxSpeed` 360 → 300 and the jump to `-720` with 1600
gravity. That changes how far you *travel*, not how fast you *swing*: measured
survivability against the same Insane CPU moved from 7.97 s to 7.42 s, and the
lower jump cannot reach the 170 px Rooftop ledge, which is why its CI failed.

The jump is deliberately untouched here. Platform reach depends on the impulse
(`850² / (2·1900) = 190 px` of apex), so a slower jump means lowering the
impulse *and* the gravity together, and that is a separate change with its own
risk. The tempo problem is solved where it actually lives.

## What did not change

- **CPU aggression.** Difficulty is being redesigned separately; the CPU still
  picks attacks on its old timers, it simply cannot swing faster than the
  weapon allows.
- Damage, reach and knockback values.
- Hit-stop (30–50 ms) and screen shake.
- Jump impulse, gravity, platform geometry.

## Verification

- Tempo probes against the real `Fighter.update`: attack rate per weapon,
  wind-up in frames, ground speed, jump apex.
- 600 seeded matches: **0 stalls**, 0 CPU self-deaths. Average navigation jumps
  rose 1.0 → 2.3 per match because walking is slower, still under the suite's
  limit and no self-deaths.
- Two new tests lock the tempo in: `attackTempoStaysHuman` (no weapon above
  3 swings/s, none below 0.6/s) and `everyMeleeSwingHasReadableWindUp`
  (at least 4 frames before a melee hit is live).
- Suite 63 → 65 methods; CI green including the APK.

## Still open

- This is a tuning pass verified headlessly. Whether 2.2–2.7 swings/s *feels*
  right on a phone cannot be checked without a device.
- Weapon balance (dual sabers still kill fastest), and the CPU's decision
  cadence, which now belongs with the difficulty work.
