# 0.3.4 — weapon handling: reloading magazines and deliberate pickups

Half the roster had a handling defect rather than a balance one: ranged and
thrown weapons deleted themselves. This pass fixes that and the silent
weapon swap.

## 1. Ranged weapons were one-shot-per-round

Firing the last round called `f.weapon = Weapons.FIST` and set `ammo = -1`.
That is not "out of ammo", it is a **permanent downgrade for the rest of the
round**, with no reload and no way back except finding another drop.

Measured on 0.3.3, holding fire continuously:

| Weapon | Cost | Magazine | Time before forced to fists |
|---|---|---|---|
| Boomerang | 960 | 1 | **0.2 s** |
| Kunai | 260 | 3 | 1.3 s |
| Bomb | 880 | 2 | 1.4 s |
| Bow | 460 | 4 | 2.5 s |
| Wand | 760 | 6 | 2.7 s |
| Pistol | 1,100 | 8 | 2.7 s |

A round lasts 20–30 seconds, so a 960-coin boomerang bought about **one
throw** per round. Survival never refreshed the weapon at all.

**Fix:** a magazine now reloads.

- `Weapons.reloadTime(int)` gives each ranged weapon a reload (1.6 s boomerang
  → 3.0 s bomb). Melee weapons return 0 and never reload.
- Firing the last round starts the reload and prints `RELOADING`; the weapon is
  **never** taken away.
- While the magazine is empty the fighter cannot swing at all
  (`Fighter.outOfAmmo()` gates `attackQueued`, `fireIfNeeded` is defensive),
  so there is no dry-fire animation with no projectile.
- When the timer finishes the magazine refills to `Def.ammo`.
- The HUD shows `reload` instead of the round count while it refills.

Sustained fire over 20 s of held attack, after the change:

| Weapon | Shots in 20 s | Per second | Damage per second |
|---|---|---|---|
| Pistol | 28 | 1.40 | 12.6 |
| Bow | 24 | 1.20 | 22.8 |
| Wand | 22 | 1.10 | 11.0 |
| Kunai | 21 | 1.05 | 15.8 |
| Boomerang | 13 | 0.65 | 8.5 (×2 hits on return) |
| Bomb | 10 | 0.50 | 17.0 + splash |

That is deliberately below a melee weapon's ceiling (sword: 17 damage every
0.58 s), so safety at range is still paid for in damage. Time-to-kill against
an idle opponent barely moved — it did **not** turn ranged weapons into the
best weapons:

| Weapon | TTK 0.3.3 | TTK 0.3.4 |
|---|---|---|
| Bow | 8.4 s | 7.6 s |
| Boomerang | 8.9 s | 7.6 s |
| Pistol | 8.3 s | 7.7 s |
| Kunai | 7.8 s | 8.6 s |
| Bomb | 9.6 s | 10.4 s |

## 2. Walking over a drop replaced your weapon with no input

`updatePickups` swapped any weapon on contact (38 × 60 px). Measured: holding a
Sword, walking onto a Pistol drop with no button pressed, ended up holding the
Pistol. Combined with defect 1, a bought Energy Blade could become fists
because you walked past a drop with an empty magazine.

**Fix:** a human must **press DOWN** while standing on a drop.

- `updatePickups` tracks each fighter's previous DOWN input and only accepts a
  fresh press (`shieldHeld && !downPrev[f]`), so holding DOWN to shield does
  not swap repeatedly and a swap cannot ping-pong.
- What you were carrying is **left on the floor** as a new drop (unless it was
  bare fists or the same weapon), so a swap is reversible and never a loss.
- Standing in range draws `DOWN TO SWAP` under the drop, and the How-to-Play
  line changed from "walk over one to grab it" to "stand on one and push DOWN".
- The CPU keeps the original walk-over grab, so its behaviour is unchanged.
  This does mean the AI can claim a drop the player is standing on but has not
  asked for; that is a known asymmetry, left for the difficulty work.

> The reference game auto-equips on contact, so auto-swap was faithful — but it
> was faithful to a game where weapons are disposable pickups, not to one where
> a weapon costs 1,100 coins. The gesture keeps the pickup flow and removes the
> trap.

## Verification

- 13 new assertions in the headless harness (`tools/verify/WeaponHandling.java`):
  reload keeps the weapon, empty magazines cannot swing, reload timing matches
  the table, drops need a press, holding DOWN does not ping-pong, a fresh press
  swaps back, and the CPU still grabs by walking.
- 4 new Robolectric tests (`everyRangedMagazineReloadsInsteadOfBecomingFists`,
  `emptyMagazineCannotStartASwing`, `weaponDropNeedsADeliberateDownPress`).
  Suite 65 → 68.
- Regression battery unchanged and green: 35 mirrored risk checks, 23 combat
  coverage checks, 21 new-test mirrors, 300 seeded matches with 0 stalls and 0
  CPU self-deaths, 0/300 stall sweep.
- Version 0.3.4, versionCode 8.

## Still open

- **The wand is the balance outlier**: 3.0 s to kill an idle opponent against a
  6–8 s median, because its five-bolt skill (32.5 damage) has no counterplay.
  Unaffected by this pass.
- The AI can claim drops the player has not asked for.
- Nothing here has been checked on a real phone.
