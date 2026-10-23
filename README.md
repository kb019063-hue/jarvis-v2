# Stick Duelist

A native Android 2D stick-fighting game inspired by Neron's Brother's *Supreme Duelist Stickman*, built with original Java code and Canvas artwork.

**Scope:** this is an independent, incomplete recreation—not a port and not yet feature-parity with the original. The current build has 12 weapons, 10 arenas and three modes. The larger content roster, 3–4 player party play, extra minigames, map editor and Wi-Fi multiplayer are still absent. See [the current review](docs/REVIEW.md) for the exact boundary.

**Install v0.2.1:** download the APK from the [latest release](https://github.com/kb019063-hue/jarvis-v2/releases/latest) and side-load it on Android 7.0 or newer (allow unknown sources). Landscape, touch-only and offline. Releases are debug-signed test builds, not store-ready production packages.

<p align="center"><img src="assets/icon_512.png" width="180" alt="Stick Duelist icon"></p>

## Screenshots

The Robolectric suite renders real game frames to PNG on every CI push; these are not mock-ups.

| | |
|---|---|
| ![Title](docs/screenshots/01-title.png) | ![Local duel](docs/screenshots/05-local-duel.png) |
| ![Countdown](docs/screenshots/02-quick-fight.png) | ![K.O.](docs/screenshots/03-hit-and-hud.png) |
| ![Arena picker](docs/screenshots/10-arena-picker.png) | ![Arsenal](docs/screenshots/11-arsenal.png) |
| ![Lava Core](docs/screenshots/08-lava-core.png) | ![How to play](docs/screenshots/15-how-to-play.png) |
| ![Knockdown](docs/screenshots/18-knockdown.png) | ![Recovered](docs/screenshots/19-recovered.png) |

More frames, including every arena and ragdoll states, are in [`docs/screenshots`](docs/screenshots).

## Controls

There are **no separate attack, jump or defend buttons**. Each fighter uses one stick:

| Input | Action |
|---|---|
| Hold a direction | Move, aim the visible weapon and basic-attack automatically on cooldown |
| Push up | Jump once per upward push; return toward neutral before jumping again |
| Hold down | Energy shield, or crouch when the energy-shield rule is off |
| Release a held direction while the outer ring is gold | Use the equipped weapon's skill in that direction |
| Pause icon | Pause, resume, restart, change arena or quit |

- **1P:** touch anywhere on the left half; the stick floats to the thumb.
- **Local 2P:** player 1 uses the lower-left stick and player 2 the lower-right stick, with independent pointer IDs.
- The outer ring refills after a skill. A release with insufficient stick deflection does not spend the skill.

## What's in it

### Combat and physics

- Active 11-joint Verlet bodies for standing fighters, hit reactions, knockdowns and K.O.s—not only death animations.
- Poise breaks, limp knockdowns, automatic recovery and brief recovery invulnerability.
- Melee contact follows the rendered weapon segment and tested body joints, including upward attacks; it is not a full continuous/swept physics simulation.
- Directional melee knockback, projectile combat, shields, hit-stop, shake, sparks, dust and damage text.
- Fixed 60 Hz game simulation, timestep-aware ragdoll integration and second-based projectile lifetimes.
- CPU spacing, punishing, committed platform-to-platform routes, non-jumping hazard retreats and occasional weapon-skill use across four difficulties.
- Thin ledges are one-way platforms; the jump impulse can clear the arenas' 170 px rises.

### Weapons and release skills (12)

| Weapon | Release skill |
|---|---|
| Fists | Rushing power jab |
| Sword | Directional dash slash |
| Spear | Long lunge |
| Dual Sabers | All-around spin/shockwave |
| Kunai | Three-blade fan |
| Hammer | Heavy shockwave |
| Bow | Triple-arrow volley |
| Wand | Five seeking bolts |
| Bomb | Cluster throw |
| Boomerang | Twin returning blades |
| Pistol | Three-bullet spread |
| Energy Blade | Dash plus plasma wave |

Basic ranged attacks consume their displayed ammo. Skills use their own cooldown. Weapons also drop during non-Survival matches and can be collected by walking over them.

### Arenas (10)

Rooftop Dusk, Neon Alley, Lava Core, Sky Temple, Frozen Lake, Bamboo Grove, Orbital Deck, Volcano Rim, Dojo Night and Chaos Pit. Hazards include water, lava, spikes and pits, with floating platforms and themed backdrops.

### Modes

- **Quick Fight:** first to two rounds against the CPU; a double K.O. replays without awarding a point.
- **Local Duel:** two players on one device with the same one-stick control scheme.
- **Survival:** successive enemies, wave rewards, healing and increasing difficulty. A mutual K.O. is a player defeat, not a free wave.

### Meta and accessibility

- Coins, two-tap weapon purchases, persistent starter selection, body colours and hair styles.
- Gravity, instant-KO hazard and energy-shield house rules.
- Sound effects and a voice announcer with independent switches; disabling Voice suppresses match and UI speech.
- Persistent records and settings through `SharedPreferences`.

## Validation

`SmokeTest` currently contains **26 authored Robolectric tests**. Coverage includes real touch release, every weapon skill, directional melee contact, double-K.O. and Survival outcomes, purchase persistence, countdown lockout, voice gating, projectile timing, ragdoll timing across frame rates, thin-ledge traversal, first-map CPU survival/engagement, CPU opening survival in every hazard arena, menu transitions and rendering every arena/weapon combination.

Android CI runs both `assembleDebug` and `testDebugUnitTest`, publishes the rendered screenshots and uploads an installable APK. A green build is not a substitute for physical-device gameplay testing; touch feel, performance and full original-game parity remain separate validation tasks.

## Build

Requires JDK 17 and the Android SDK (compileSdk 34):

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

No Gradle wrapper is committed. CI installs Gradle 8.7 and the SDK, then publishes `stick-duelist-<run>.apk`. The application metadata is currently `versionCode 3`, `versionName 0.2.1`.

## Structure

```text
app/src/main/java/com/arena/duelist/
  MainActivity.java   activity, immersive fullscreen, TTS announcer, haptics
  GameView.java       SurfaceView, fixed-step loop, multi-touch routing
  Game.java           screens, match flow, combat, HUD, menus, one-stick input
  Fighter.java        movement, attacks, weapon pose, shield and fighter state
  Ragdoll.java        11-joint Verlet body, constraints and platform collision
  Weapons.java        12 weapon definitions and skill descriptions
  Projectile.java     thrown/projectile state and second-based lifetime
  Maps.java           10 arenas, platforms, hazards and themes
  Cpu.java            spacing, punishes, weapon use and hazard avoidance
  Particles.java      combat and movement effects
  Painter.java        Canvas drawing helpers
  SoundFx.java        on-device synthesized sound effects
  Profile.java        coins, unlocks, settings and records
app/src/test/java/com/arena/duelist/SmokeTest.java
```

## Remaining parity work

- [ ] Original-scale weapon and arena roster
- [ ] 3–4 player local party matches
- [ ] Football, boss tournament and other original modes
- [ ] Map editor
- [ ] Wi-Fi/LAN multiplayer
- [ ] Physical-device gameplay/performance validation
- [ ] Production signing, store assets and release hardening

---

Not affiliated with Neron's Brother. No assets from the original game are used.
