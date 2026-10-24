# 0.3.7 — the shield

Road-map item 5: *"the shield breaks after about four hits and never
regenerates."* Both halves were true and both are fixed.

## What was measured

`tools/verify/Shield.java` drives real `Fighter`s and a real `Game`.

| Weapon | damage | hits to break a full shield (before) |
|---|---|---|
| Pistol | 9 | 7 |
| Wand | 10 | 6 |
| Dual Sabers | 11 | 6 |
| Boomerang | 13 | 5 |
| Spear | 14 | 4 |
| Kunai | 15 | 4 |
| **Sword** | 17 | **4** |
| Energy Blade | 18 | 4 |
| Bow | 19 | 3 |
| Hammer | 27 | 3 |
| Bomb | 34 | **2** |

Shield points cost **1.8× the incoming damage**, and a blocked hit still costs
15 % of the damage as chip. Breaking a shield also applied a 0.7 s stun — or
rather, it *tried* to: `damage()` called `stun(0.7f)` and then assigned
`hurtT = 0.22f` three lines later, so the guard break was a normal hit-stun
with extra steps. That bug is fixed too (`Math.max`).

None of that would matter if the shield came back. It did not:
`tools/verify/Audit2.java` measured **10 → 10 points over 5 s**, and
`Shield.java` confirmed **0 → 0 over 15 s**. A shield spent in the opening
exchange was gone for the entire round.

Against a difficulty-3 CPU with a sword, and the human stick held DOWN through
the real pointer path (8 seeds):

| Player | died | average round |
|---|---|---|
| stick centred, no shield | 8/8 | 5.8 s |
| stick held DOWN, shield up | 8/8 | **7.4 s** |

So the shield was worth +28 % survival, once, for four hits.

## The fix

- **1.8 → 1.0 shield points per point of damage.** A sword now takes **6**
  hits, a bomb 3, a pistol 12. The shield still shrinks per hit — that part is
  faithful to the original, where it "gets smaller with each hit until
  disappearing" — it just lasts long enough to be a decision rather than a
  reflex.
- **Recharge.** After **2.5 s** without a blocked hit the shield regenerates
  **22 points per second** (4.5 s from empty). A broken shield becomes
  raisable again at **35 %** — measured at **4.1 s** from the break — so a
  break costs a real window instead of the rest of the round.
- **Guard-break stagger 0.45 s**, and it now actually applies. It is a
  punish for over-blocking, not a death sentence: the 0.7 s the code asked
  for was long enough to eat a free follow-up.
- **Blocked hits still deal 15 % chip.** The community guide describes the
  original shield as blocking *all* damage. Chip damage is kept deliberately:
  it is what stops a shield from being an infinite stalemate, and 0.3.6 just
  went to some trouble to make every match terminate.

| | before | after |
|---|---|---|
| Hits to break (sword) | 4 | **6** |
| Hits to break (bomb) | 2 | **3** |
| Recharge | never | 22/s after 2.5 s |
| Time until a broken shield can be raised | never | **4.1 s** |
| Guard-break stun | 0.22 s (bug) | **0.45 s** |
| Survival vs sword CPU, holding the stick down | 7.4 s | **9.0 s** |

The 0.3.6 clocks use remaining health to award a timed-out round; the chip
damage keeps that comparison meaningful while a player turtles.

## What this does not do

**The CPU never raises its shield.** `Cpu.java` sets `shield = false` on every
path and has no branch that ever sets it true — measured: **0 shield frames in
30 s**. That is a fidelity gap in its own right, but it is difficulty tuning,
and the standing instruction is to leave CPU aggression alone until the
difficulty pass. It is recorded here and in the audit table so it is not lost.

## Verification

Four new Robolectric tests (6 sword hits, a broken shield returning mid-round,
recharge gated behind the delay, guard break longer than a hit-stun) for
**80 tests** total. All four were mirrored headlessly in
`tools/verify/ShieldTests.java` and passed against the shipped code before
being trusted.

| Suite | Result |
|---|---|
| `ShieldTests` | 4 checks, 0 failures |
| `TestMirror` | 21 checks, 0 failures |
| `Risky` | 35 checks, 0 failures |
| `WeaponHandling` | 13 checks, 0 failures |
| `Harness` | 15 checks, 0 failures |
| `Clock` | all four scenarios still end |
| `Sweep` (300 matches) | 0 stalls, 0 CPU self-deaths |
| `Stall` (300 matches) | 0 stalls |
| `Balance` | fair-TTK spread unchanged at 9.7× |

Balance is unchanged because the CPU does not block, so no time-to-kill in the
table moves.

## Metadata

Version 0.3.7, versionCode 11.
