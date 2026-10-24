# 0.3.5 — weapon balance

The previous pass named the wand as the balance outlier at 3.0 s. **That
conclusion was wrong**, and this pass corrects it before changing anything.

## Why the old number was wrong

Time-to-kill was measured against an **idle** opponent. A stationary target is
the best possible case for a homing weapon (its bolts cannot miss) and the
worst possible case for melee (nothing walks into range). So the metric
manufactured a wand outlier that does not exist in play.

This pass measures the same thing against an **engaged-but-evasive opponent**:
it holds ~180 px, strafes, closes when further than 300 px, and hops. Nine
seeds per weapon, median reported.

| Weapon | dmg | cd | idle TTK | dodging TTK |
|---|---|---|---|---|
| Dual Sabers | 11 | 0.40 | 5.7 s | **6.2 s** |
| Energy Blade (0.3.4) | 21 | 0.52 | 3.7 s | **6.1 s** |
| Bomb | 34 | 1.25 | 13.0 s | 7.0 s |
| Sword | 17 | 0.58 | 5.9 s | 7.9 s |
| Hammer | 27 | 1.05 | 6.8 s | 7.9 s |
| Spear | 14 | 0.70 | 7.5 s | 9.5 s |
| Wand | 10 | 0.50 | 6.4 s | **11.1 s** |
| Pistol | 9 | 0.36 | 7.5 s | 11.3 s |
| Boomerang | 13 | 1.15 | 8.1 s | 13.0 s |
| Kunai | 15 | 0.54 | 8.8 s | 13.8 s |
| Bow | 19 | 0.78 | 6.8 s | 14.0 s |
| Fists | 8 | 0.45 | 10.4 s | **never (60 s cap)** |

The wand is not the outlier — it is the second *slowest* ranged weapon. The
real one is the **Energy Blade**.

## 1. Energy Blade was 38 % ahead of every other melee weapon

21 damage every 0.52 s is **40.4 dps** — the next best melee weapon is the
sword at 29.3 — and it also carries the second-longest reach (66 px) and the
highest melee knockback (440). Best damage, near-best reach, best shove, for
1,500 coins.

**Fix:** 21 damage / 0.52 s → **18 damage / 0.60 s (30.0 dps)**. It is still
the strongest melee weapon, just no longer by a third.

Measured: **6.1 s → 7.1 s** against the dodging target (sword: 7.9 s).

A new test, `noMeleeWeaponOutclassesTheRoster`, now enforces a 31 dps ceiling
on every melee weapon and requires the flagship to stay within 1.1× the sword,
so this cannot silently regress.

## 2. The CPU started unarmed in 44 % of matches

`rndWeapon()` returned bare fists with probability 0.45. Measured over 300
unseeded matches: **131/300 (44 %)** of CPU opponents began the fight with no
weapon at all.

That matters because an unarmed fighter re-arms only by happening to walk over
a drop:

- started unarmed and picked up a weapon: **23/40 (58 %)**, median 10.2 s
- **never** picked one up within 45 s: **17/40 (42 %)**

So roughly one match in five was fought against an opponent that owned no
weapon for its whole duration.

**Fix:** the unarmed chance drops from 45 % to 10 % (measured 14 % over 300
unseeded matches, 10 % over 200 seeds).

### What this does and does not do

It is a **variety and consistency** fix, not a difficulty fix. Measured on a
hazard-free arena against a moving player, 60 matches each:

| | 0.3.4 | 0.3.5 |
|---|---|---|
| CPU unarmed at start | 45 % | 12 % |
| Killed the moving player | 90 % | 93 % |
| Median time-to-kill | 9.5 s | 10.9 s |

Time-to-kill got *slightly longer*, because fists have the shortest cooldown in
the game (0.45 s) and the weapons it now draws are slower: 0.58–1.25 s. The
opponent is better armed but swings less often. Difficulty work is deliberately
untouched and still waiting on the difficulty pass.

## Honest limits of these numbers

- The dodging target is a scripted strafer, not a human. It never attacks, so
  these are "how fast can this weapon kill you", not duel win rates.
- Melee results cluster tightly (sword min = max = 7.9 s) because the CPU walks
  into range predictably; variance is much higher for ranged weapons.
- Fists reading "never" is in the *no-pickups* condition. In a real match drops
  spawn every 5.5–8.5 s, which is why the table above still shows kills.
- Nothing here has been played on a phone.

## Verification

- `tools/verify/Balance.java` (new): per-weapon TTK against a moving target,
  9 seeds each, plus the idle number for comparison.
- `tools/verify/Sweep.java`: 300 seeded matches, 0 stalls, 0 CPU self-deaths
  — the extra armed CPU did not reintroduce the ledge/stall class of bug.
- `tools/verify/Stall.java`: 0/300 stalls.
- Regression mirrors: 35 (Risky), 23 → 21 (TestMirror), 13 (WeaponHandling),
  15 (Harness) — all green.
- Suite 68 → 70 methods. Version 0.3.5, versionCode 9.
