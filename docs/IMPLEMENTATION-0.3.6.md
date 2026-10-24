# 0.3.6 — matches that always end, and a streak bonus that pays

This pass started as **item 4 of the road map: "the economy is too slow."**
It is not. The economy claim is retracted in §1. The investigation did uncover
a real defect — **some matches could never end** — and that is what 0.3.6 fixes.

## 1. Retraction: the economy was never the problem

The complaint came from counting *wins*: 109 victories to own the 7,580-coin
roster reads like a grind. Counting wins is the wrong unit — what matters is
**time**.

`tools/verify/Economy.java` plays 24 full matches against the Normal CPU with
human-like cadence (approaches, strikes at weapon range, backs off under 35 HP)
and measures wall-clock length and coins earned.

| Measure | Result |
|---|---|
| Win rate | 24/24 (100 %) |
| Match length | 27.2 s average (20.8 – 31.1 s) |
| Coins per match | 70 |
| Coins per hour | **9,257** |
| Full roster, cheapest first | 105 matches ≈ **0.8 hours** |

Unlock order from a 250-coin start: Kunai 2 matches, Spear 7, Bow 13, Dual
Sabers 21, Hammer 31, Wand 42, Bomb 54, Boomerang 68, Pistol 84, Energy Blade
105. Nothing here needs tuning. **The 0.3.6 pass changes no reward value.**

## 2. Some matches could never end

`tools/verify/Clock.java` drives a real `Game` frame by frame with no input and
asks one question: does the match reach the result screen?

| Scenario | Before |
|---|---|
| Football, local, both players idle | **still running at 300 s** (0–0) |
| Football, local, both players retreating | **still running at 180 s** |
| Local duel, both players idle | **still running at 300 s** |

Football had no match clock at all — a goal was the only exit, and the ball
only moves when a stickman kicks it, so two passive players froze the game.
Duel rounds had no time limit either, and since a round can only end in a K.O.
two evenly matched idle players looped forever.

The CPU hid this: it chases and scores, so the CPU football match finished
normally in 25 s (0–3). It only goes wrong with two human players, or one.

### The fix

- **Football: 120 s match clock.** At 0:00 the score decides; a tie is a draw.
- **Duel: 90 s round clock.** At 0:00 the fighter with the larger *remaining
  health percentage* takes the round. Within 2 % of each other the round is a
  draw and replays — as a double K.O. already did.
- **`MAX_ROUNDS = 6`.** A drawn round awards nothing and replays by design, so
  90 s alone would still let two perfectly matched players draw forever. Six
  rounds is also the football length, and the football rule (first to three) is
  itself a natural cap.
- **A drawn match pays 25 coins** and does not touch wins, losses or streak.

The clock is drawn in the HUD during play and turns red under 10 s, so the
limit is visible rather than a surprise.

| Scenario | After |
|---|---|
| Football, local, both idle | **ends at 124 s**, 0–0 draw |
| Football, CPU | ends at 25 s (0–3) — unchanged |
| Local duel, both idle | **ends at 572 s** as a drawn match |
| Football, both retreating | **ends at 124 s** |

572 s is the honest worst case: two people who never land a hit play six full
90-second rounds. That is a long match, but it is bounded, and it is a match
nobody was trying to win.

## 3. The win streak was displayed but never paid

`profile.streak` was incremented on a win, saved, and drawn on the menu —
"Streak 7" — and paid **nothing**. It now pays **+10 coins per consecutive win
after the first, capped at +50**, and the result screen shows it as
`· streak +30`.

A draw leaves the streak alone (it neither continues nor breaks it); a loss
still resets it.

## Verification

Two new Robolectric tests cover the clock contract and two more cover the
bonus, for **76 tests** total (was 70). Every one of them was mirrored
headlessly in `tools/verify/ClockTests.java` and run against the shipped code
before being trusted — 7 checks, 0 failures, including a full 568 s all-draw
match simulated in 162 ms.

| Suite | Result |
|---|---|
| `TestMirror` | 21 checks, 0 failures |
| `Risky` | 35 checks, 0 failures |
| `WeaponHandling` | 13 checks, 0 failures |
| `Harness` | 15 checks, 0 failures |
| `Sweep` (300 matches) | 0 stalls, 0 CPU self-deaths |
| `Stall` (300 matches) | 0 stalls |
| `Clock` | all four scenarios end |
| `Balance` | fair-TTK spread unchanged at 9.7× |

Weapon balance is untouched: the dodging time-to-kill table is identical to
0.3.5 to the tenth of a second. CPU aggression was not touched.

## Metadata

Version 0.3.6, versionCode 10.
