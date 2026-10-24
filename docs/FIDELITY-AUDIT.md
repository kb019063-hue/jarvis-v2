# Fidelity audit: is this an exact recreation of *Supreme Duelist Stickman*?

**Verdict: no.** This is a genuine, working, original stick-fighting game that
takes *Supreme Duelist Stickman* as its reference, but it is roughly a
one-fifth-scale reimplementation. It is not a port, not a clone of the
original's content, and not an "exact game" of it. The core loop (ragdoll
duel, one-stick control, release-for-skill) is recognisably the same idea; the
content, modes, progression and polish that make the original what it is are
mostly absent.

The repository's own documentation says the same thing ("independent,
incomplete recreation—not a port and not feature-parity"), so this is not a
case of an undisclosed gap. It is a case of a small demo being described by
its author in a way that could be read as closer than it is.

## What the original is

*Supreme Duelist Stickman*, by **Neron's Brother**
(`com.Neurononfire.SupremeDuelist`, 100M+ installs, ~4.5 stars):

- A ragdoll stickman fighting game with deliberately floppy, physics-driven
  bodies and a single on-screen joystick.
- Drag to move and aim the weapon; **release** the joystick to trigger that
  weapon's special skill; up to jump; down to crouch/shield.
- Roughly **66 weapons** and **46 arenas** according to the community wiki,
  unlocked with coins and XP.
- Local 1–4 player battles, a football minigame, survival waves, and a set of
  battle modifiers (mirror, random gravity, giant weapon, double trouble,
  radioactive, boots fight, super jump, no health bar).
- A map editor, skins and stickman customisation, character hair/accessories,
  daily rewards and achievements.

## Side by side

| Area | Original | This build | Difference |
|---|---|---|---|
| Core duel | Ragdoll health duel with environmental K.O. | Same | Faithful in spirit |
| Control scheme | One stick: move/aim, release = skill, up = jump, down = shield | Same | **Genuinely faithful** |
| Ragdoll | Full-body physics ragdoll | 11-point physics ragdoll | Simplified but real |
| Weapons | ~66, each with a distinct ability | 12, each with one ability | **~18 % of roster** |
| Arenas | ~46 + map editor | 10, hand-built, no editor | **~22 %, no editor** |
| Players | 1–4 local | 1–2 (2nd is CPU or same-device stick) | No party play |
| Modes | Duel, football, survival, modifiers | Duel (best of 3), football, survival, 5-boss tour | Own designs, not the original's |
| Modifiers | 8+ battle rules | 3 house rules (gravity, instant KO, shield) | Partial |
| Weapon clash/parry | Weapon-vs-weapon collisions matter | Not implemented | **Missing mechanic** |
| Progression | Coins, XP/levels, unlocks, dailies, achievements | Coins, one-off weapon purchases | Much thinner |
| Customisation | Skins, accessories, hair | Body colour + hairstyle | Thin |
| Online | None (local only) | None (`no INTERNET` permission) | Parity by absence |
| Art / audio | Hand-drawn stickmen, music, SFX | Procedural Canvas art, synthesised SFX + TTS announcer | Clearly placeholder-tier |

## What is genuinely there

- It installs, runs and plays: a full match loop, menus, countdown, rounds,
  K.O., results, pause, and persistence of coins and settings.
- The one-stick control model is implemented properly, including
  release-to-skill, and is the most faithful part of the project.
- Ragdoll physics, hit sparks, screen shake, damage numbers and a K.O. camera
  exist and work.
- 12 weapons are mechanically distinct: reach, damage, cadence, thrown arcs,
  explosives, a homing wand and a returning boomerang.
- 10 arenas with hazards (lava, spikes, void), instant-KO rules and a football
  mode with its own physics.
- 62 authored automated tests and real rendered screenshots in CI.

## What is missing relative to "an exact game"

The short version: **content volume, party play, the map editor, the modifier
set, weapon clashes, and progression.** Someone who has played the original
will recognise the concept within five seconds and then notice, within a
minute, that there are a dozen weapons, ten maps, two fighters and no
customisation beyond colour and hair.

Calling this "an exact game of Stickman Duelistic" is not supportable. Calling
it "a small original game inspired by Supreme Duelist Stickman" is accurate.

## Defects found in this review

Found by reading all 7,648 lines of source and by driving the compiled game
classes through a headless simulation harness (600 seeded matches plus
isolated physics probes). Full write-up: `IMPLEMENTATION-0.3.2.md`.

| # | Severity | Defect | Evidence | Status |
|---|---|---|---|---|
| 1 | Critical | CPU parks on a side ledge and never fights (round cannot end) | 31/120 seeded matches stalled; CPU `move = 0` while the player stood below | **Fixed** — 0/600 |
| 2 | Critical | Gravity OFF launches a jump out of the world and never returns | apex 1363 px above arena top, still rising at 4 s | **Fixed** — 177 px hop, lands |
| 3 | High | Hazard avoidance treats lava under a solid floor as lethal, freezing the CPU | CPU retreats forever above Lava Core/Orbital Deck bridges | **Fixed** |
| 4 | High | Movement controller cancels knockback within 2–3 frames | fist hit = 11 px, dash = 78 px reach | **Fixed** — 24 px / 138 px |
| 5 | Medium | Hitstun freezes all steering for up to 0.5 s | "stunlock" feel reported on device | **Fixed** — slowed to 45 %, not frozen |
| 6 | Medium | Weapon balance: dual sabres kill 100→0 HP in under 10 s | isolated 9-hit kill, player pinned at wall | Open (tuning) |
| 7 | Low | Light weapons feel inert; a fist shoves only ~24 px | measured | Open by design |
| 8 | Low | Two different version strings in the UI | title said v0.3.1, settings said v0.2.1 | **Fixed** in 0.3.2 |
| 9 | Low | No physical-device testing of frame pacing or touch feel | no hardware in the writing environment | Open |

## How to check this yourself

- Install `docs/screenshots/*.png` are rendered by the test suite each CI run,
  so they reflect the real code.
- Run `gradle testDebugUnitTest`; the 62 methods exercise combat, CPU
  navigation, camera, football, bosses and the input model.
- The pre-fix numbers in the table above came from `tools/verify/` — a
  headless harness that compiles the game classes and runs real matches with
  fixed random seeds.
