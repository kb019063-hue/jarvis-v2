# Review: "Stick Duelist" vs *Supreme Duelist Stickman*

Audit of the build in this repository: what it is, how close it is to the game it
copied, what was broken, and what is still missing.

**Short version.** It is a real, working, from-scratch Android game — not a mock-up.
It gets the *shape* of Supreme Duelist right (1v1 stick duel, weapon drops, hazard
arenas, gravity / instant-KO / energy-shield house rules, local 2P), but it is
**not an "exact game"**: the original's defining feature, full ragdoll physics on
every hit, is absent, and most of its content and party modes are missing.

---

## 1. What actually exists

| Item | Evidence |
|---|---|
| Native Android app (Java, no engine, no external assets) | `app/src/main/java/com/arena/duelist/*.java`, ~4.4k lines |
| Builds and tests green | GitHub Actions on this branch — `assembleDebug` + `testDebugUnitTest` success |
| Tests | 12 Robolectric tests incl. per-arena ragdoll stability, knockdown → recovery, poise, input-lock and the buy-confirm flow |
| Installable APK | Releases `build-19`–`build-21` → `stick-duelist-<n>.apk`, `minSdk 24` (Android 7.0+) |
| Screenshots are real frames | `SmokeTest` renders with Robolectric NATIVE graphics to PNG |
| 10 arenas, 12 weapons, 3 modes, coins/unlocks/settings | `Maps.java`, `Weapons.java`, `Game.java`, `Profile.java` |

Screens: title → arena picker → match, plus Arsenal, Customize, Settings, How to play,
Pause, Result. Match flow: intro countdown → best-of-3 rounds → K.O. → result → rematch.

## 2. Is it like *Supreme Duelist Stickman*?

Research baseline ([Play Store](https://play.google.com/store/apps/details?id=com.Neurononfire.SupremeDuelist),
[community wiki](https://supreme-duelist-community-wiki.fandom.com/wiki/Supreme_Duelist_Stickman),
[APKPure](https://apkpure.com/supreme-duelist-stickman-app/com.Neurononfire.SupremeDuelist)):

* 2D **ragdoll** stick fighting released 16 Jul 2018 by Neron's Brother, 100M+ installs.
* 1 / 2 / 3 / 4 players on one device + CPU, survival, football mini-game, boss
  tournament, zombie mode, map editor.
* House rules: gravity ON/OFF, instant KO, energy shield (with it off the fighter
  "can bend down").
* 46 maps, 66 weapons. Local only — no online play.

| Feature | Original | This build |
|---|---|---|
| 1v1 stick duel, best-of-3, hazards, weapon drops | ✅ | ✅ |
| Gravity / instant KO / energy shield toggles | ✅ | ✅ (crouch added — see fix #6) |
| Local 2P, same device | ✅ | ✅ (was broken — fix #1) |
| Skin/weapon unlocks with coins | ✅ | ✅ (8 colours, 4 hair styles, 12 weapons) |
| Ragdoll physics | ✅ on every hit | ✅ the living fighter is drawn from a simulated body, so every hit flops it |
| 3–4 player, football, boss, zombie, map editor | ✅ | ❌ |
| Content | 66 weapons / 46 maps | 12 / 10 |
| Online | ❌ | ❌ |

**Verdict:** recognisably the same genre and rule set, and the code quality is decent,
but calling it "an exact game of Stickman Duelist" would be wrong. It is a
small, clean tribute.

**Update — active ragdoll (this branch):** a fighter is no longer an animation with
a ragdoll bolted on for deaths. The body exists from the first frame and the drawing
is stroked straight from its joints, so *every* hit is a physics event: the blow adds
an impulse and a `limp` value, and a muscle term pulls the joints toward the animated
pose (quadratic in `limp`, so a jab is a 28 px flinch that is gone in 0.25 s and a
hammer is a 133 px flop that takes 0.42 s). Standing bodies use 0.4× gravity so a
flinch wobbles instead of collapsing, and at full muscle the pose is snapped exactly
— the constraint solver leaves a few px of drift in the over-constrained torso
triangle, and that drift was enough to make the fighter look deformed.

**Update — ragdoll-on-hit:** the living fighter now goes limp too.
Each hit builds poise (`damage × (1 + knockback/500)`); at 46 the fighter is knocked
down and a ragdoll drives the body — fists need 4 clean hits, a hammer one. A downed
fighter cannot move, attack or shield, the CPU punishes it, and it stands back up on
whatever surface it is lying on after ~0.9 s with 0.6 s of i-frames and reset poise.
Dying while down keeps the same body (`Ragdoll.reborn`) instead of teleporting.
The camera also gained dynamic zoom (1.55 → 1.02) and now leans toward any downed
body, because a fixed, tight zoom left separated fighters off-screen.

**Update — ragdoll pass (earlier in this branch):** the rigid "spin on death" has been replaced
by a real jointed ragdoll (`Ragdoll.java`): 11 points, 12 distance constraints
(10 bones + 2 braces to stop the spine folding flat), 5 relaxation passes, gravity,
damping, and platform collision with friction. The rest lengths are captured from the
pose the fighter died in, so the hand-off is seamless, and the corpse fades once it
settles.

## 3. Bugs found and fixed in this branch

Severity: **A** = blocks a feature, **B** = wrong behaviour, **C** = polish.

### A1. Local 2P was barely playable — joystick ate the buttons
`layoutControls()` puts the 2P joystick at `(92, h-92)` with a grab radius of
`74 + 70 = 144`, while ATK sits only **125 units** away (radius 48) and DEF
**128 units** away (radius 29). Because `pointerDown` tested the sticks *first*,
any touch inside the joystick's oversized zone became movement.

Numerically (circle–circle overlap of the grab zone with each button disc):

| Button | usable fraction of the visible button |
|---|---|
| ATK | **29 %** |
| DEF | **18 %** |
| JMP | 81 % |

So player 2 could not reliably attack or block. *Fix:* buttons are hit-tested before
the sticks, and the joystick grab radius is now `radius + 34`.

### A2. Boomerang dealt ~6 hits for one throw
`updateShots()` removes a projectile on hit *unless* it is a boomerang, but there was
no per-target cooldown, so it re-hit **every frame it overlapped**, outbound and
returning. Simulating the exact shipped formula (820 px/s, 9 px radius, 24 px target
padding) gives **6 hits = 78 damage** from a single 1-ammo throw, versus the 2 the
README promised. *Fix:* `hitCount` / `hitThisPass` on `Projectile` — at most one hit
per pass, two per throw, verified to land exactly 2 for any target in range.

### A3. The 10th arena had no button
The picker is a 3-column grid of 96-unit cards starting at y=118; only three rows fit
in 540 virtual units, and the loop `break`s when a card would overflow. 3 × 3 = 9
cards for 10 maps, so **Chaos Pit could never be selected** on any screen size.
*Fix:* four columns × 88-unit cards — all 10 now fit at every aspect ratio.

### A4. "Change arena" in the pause menu silently changed the game mode
It reopened the picker with `pendingMode`, which is only updated from the title
screen, and the picker then calls `resetMatch(pendingMode, …)`. Pausing a **2P duel**
and picking a new arena restarted it as a **1P Quick Fight**. *Fix:* the pause path
sets `pendingMode = mode`.

### B1. The countdown never said "Three"
`stepIntro` derives the number from `ceil(phaseT - 0.6)` with `phaseT = 2.6` (2.2 for
later rounds) → the sequence is always **2, 1, FIGHT**, even though the README and
the announcer advertise "Three… Two… One… Fight!". *Fix:* 3.4 s / 3.0 s.

### B2. DEF was dead while holding a ranged weapon
`shieldOn` required `Weapons.DEFS[weapon].type == MELEE`, so with the bow, wand,
pistol, kunai, bomb or boomerang equipped the shield button did nothing at all, with
no feedback. *Fix:* the shield works with every weapon.

### B3. Survival became unwinnable after ~4 waves
The player is capped at 100 max HP, heals 22 per wave, while enemy HP is
`70 + wave × 26` (wave 2 = 122, wave 5 = 200) and the player's best strike is 27.
Wave 1 also used a hard-coded 100 HP while wave 2 used the formula, so wave 2 was
weaker than wave 1. *Fix:* enemy HP `70 + wave × 18`, wave 1 uses the same formula,
player max HP grows to 140 and heals 30.

### B4. Irreversible actions on a single mis-tap
Tapping a locked weapon card instantly spent up to 1500 coins, and **RESET PROGRESS**
wiped coins, unlocks and records with one tap. *Fix:* both now require a second tap
("TAP AGAIN · 880" / "TAP AGAIN!"), and any other tap cancels the confirmation.

### B5. Stale menu hitboxes for one frame
`uiBtns` is rebuilt during `draw()`; `pause` and `finishMatch` switched screens
without clearing it, so a tap in the first frame after the switch could fire a button
from the *previous* screen (e.g. restart the match on a different arena). *Fix:*
clear the list when entering the pause overlay and the result screen.

### C1. Dead / misleading code
Removed: the Wi-Fi lock in `GameView` (there is no networking, yet it implied LAN
support), `Fighter.finalFlip`, `Fighter.deathVx`, `Game.introShake`,
`Game.pressedAction`, the empty `spawnLandDust()`. Implemented instead of removing:
`Fighter.crouch` (was never assigned, so the README's "crouch" was fiction — now DEF
crouches when the energy-shield rule is off, matching the original), and the win
streak/records, which were persisted but never updated or shown.

### B6. 1P joystick snapped to full speed on touch
In single player the whole left half of the screen grabs the stick, but `move()`
measured the offset from the stick's **fixed base**, not from where the finger
landed. Touching 300 units from the base therefore produced `clamped / max = 1.0`
— instant full-speed running toward that corner. *Fix:* the stick now floats to the
thumb on touch-down and returns to its drawn position on release.

### C2. Per-frame allocation on the Customize screen
`drawStyle()` constructed a `Fighter` on every frame just to draw the preview.
*Fix:* cached instance.

## 4. Known issues left as-is

| # | Issue | Why |
|---|---|---|
| 1 | ~~Light hits are still canned knockback~~ | **Fixed**: the standing fighter is driven by the physics body (see the active-ragdoll update above) — every hit flops the body, heavy ones knock it down |
| 1b | The 1P stick is only drawn at its resting spot; while held it moves under the thumb | Intentional (floating stick), but means nothing is drawn where the thumb is not |
| 2 | `Projectile.life` counts frames, not seconds | Lifetime drifts with frame rate; cosmetic |
| 2b | Boomerang turnaround is time-based, so its range is a fixed `0.45 s × 820 = 369 px` | Throws beyond 369 px snap back before reaching the target; would need a distance/hit-based return |
| 3 | `Math.random()` in `Fighter.kill()` | Non-deterministic death spin, hurts reproducible tests |
| 4 | The lock glyph "🔒" is an emoji in Canvas text | Falls back to a box on some OEM fonts |
| 5 | `Game.tick` adds `dt` to `time` before clamping it | After a long hitch, menu animations jump |
| 6 | `backPressed()` on the result screen has a pointless ternary (`S_TITLE : S_TITLE`) | Goes to the title instead of the arena picker |
| 7 | Debug-only APK, `versionCode 1`, no signing config, no adaptive icon | Fine for sideloading, not for a store listing |
| 8 | `Host.setBright` is implemented but never called | Harmless extension point |
| 9 | ~~`SmokeTest` taps missed their buttons~~ | **Fixed**: three taps were 60/6/6 units outside their targets, so the How-to-play and Arsenal screenshots were showing the title screen; the buy-confirm flow is now asserted too |

## 5. Recommendations, in order

1. ~~**Ragdoll pass.**~~ **Done** — `Ragdoll.java` drives every fighter, standing or
   not: the body is simulated from the first frame, the animated pose is a muscle
   target, and hits flop the body before it recovers. Deaths and knockdowns are the
   same solver with the muscle switched off.
2. **3–4 player local.** The input layer already supports N pointers; the fight loop
   assumes exactly two fighters, so this is a refactor of `fighters[]`, HUD and
   round logic.
3. **Content.** Maps and weapons are cheap to add (`Maps.build()`, `Weapons.DEFS`);
   a map editor and the football/boss/zombie modes are separate features.
4. **Ship-ability.** Signing config, `versionCode` bump, adaptive icon, and a
   `targetSdk` review before anything goes to a store.

---

### How this audit was done

Reviewed every source file, then checked the suspicious formulas by re-implementing
them numerically (2P control geometry, boomerang hit loop, survival curve, picker
grid capacity). The APK itself was **not** executed: this sandbox has no JDK or
Android SDK and cannot reach Google's Maven or the Gradle distribution, so device
behaviour is inferred from code plus the last green CI run and its rendered
screenshots. Java syntax of every edited file was verified with a Java parser.
