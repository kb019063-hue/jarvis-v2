# Pre-0.3 review: Stick Duelist vs. *Supreme Duelist Stickman*

**Review date:** 2026-10-23

**Reviewed application version:** 0.2.1 (`versionCode 3`)

> Historical review of 0.2.1. Version 0.3.0 adds independent CPU/local football,
> nearest-event projectile collision, haptic permission and interruption/loop fixes.
> The subsequent 0.3.1 playability pass fixes close-range AI, corpse replay and camera behavior, and rebuilds the arenas.
> See [the 0.3.1 notes](IMPLEMENTATION-0.3.1.md), [0.3.0 update](IMPLEMENTATION-0.3.md) and the current README.
> Remaining claims of absent football below describe the older reviewed revision.

## Verdict

This repository contains a real native Android game with original Java code, Canvas artwork, synthesized sound, active ragdoll bodies, four modes, twelve weapons and ten arenas. It is **not an exact recreation or a completed port** of Neron's Brother's *Supreme Duelist Stickman*.

The repair pass corrected confirmed match, persistence, input, audio, physics and combat defects and moved the controls closer to the original's one-stick design. The new boss tournament is an independent five-fight mode, not a reproduction of the reference mode; major content and mode gaps remain. The result must not be described as full parity.

Reference baseline: the [official Play Store listing](https://play.google.com/store/apps/details?id=com.Neurononfire.SupremeDuelist), the [community wiki](https://supreme-duelist-community-wiki.fandom.com/wiki/Supreme_Duelist_Stickman), and the control description on [Uptodown](https://supreme-duelist.en.uptodown.com/android). Original-game feature totals can change between releases; the comparison here is a reference snapshot, not a claim to have reproduced a particular proprietary build.

## What the repair pass changed

### Controls and combat

- Replaced the separate ATK/JMP/DEF clusters with one stick per player.
- Holding a direction now moves, aims and performs basic attacks; up jumps, down shields/crouches, and releasing a charged direction activates the weapon skill.
- Added distinct release-skill implementations for all twelve current weapons, with per-weapon cooldowns and visible charge rings/labels.
- Routed rendered weapon orientation and melee contact through the same angle and physical hand/tip geometry.
- Melee collision follows the rendered weapon segment and body joints, sampling six interpolated segment poses per frame so quick swings can connect. This is an approximation, not a full continuous swept solver or the original's exact physics.
- Added CPU use of release skills and updated the in-game instructions and arsenal text.

### Match and state correctness

- Newly purchased starter weapons are saved **after** the starter field changes, so they survive recreation of `Profile`.
- A double K.O. awards no round point and replays the round; a 1–1 draw can no longer turn into a contradictory tied defeat.
- Survival checks player death before enemy death, so a mutual K.O. cannot award a wave or spawn another enemy before defeat.
- Added a playable five-boss tournament: each custom boss has a themed name/color, signature weapon and increasing health; clean wins grant coins, restore some player health/shield between bouts, and the final K.O. awards the tournament victory. A mutual K.O. is a loss. This is original content, not verified parity with the reference game's boss mode.
- Countdown and post-round simulation reject gameplay input and clear stale movement/action state.
- Round transitions clear projectiles and held controls, preventing post-K.O. hits and accidental skill releases.
- Menu transitions clear stale hitboxes; Android Back now does the same before the next frame is drawn.
- The arena picker, pause-to-arena flow, purchase/reset confirmations and Survival scaling fixes from the earlier review remain in place.

### Character look and combat feel

- Matched the default combat palette to bright green/yellow stick figures, removed the face dot and shoe-like foot accents, and gave the bright default skins clean dark outlines. The live body and customization preview now use the same joint renderer; optional custom hair remains.
- Added a subtle counter-swinging gait/body bob, kept crouched feet on the floor while shortening the crouch hitbox, and replaced the constant melee arc with per-weapon wind-up, strike and follow-through poses.
- Basic melee strikes now take a small weapon-weighted step; fist hit reach is tied to the visible hand instead of an invisible extra-long hit line.
- Sampled the rendered weapon segment through each frame for quick melee sweeps, and attach the held weapon to the forearm during ragdoll falls.
- These are original procedural changes guided by public gameplay references, not a claim that the proprietary art or physics have been reproduced exactly.

### Timing, AI and presentation

- The render loop uses a fixed 60 Hz accumulator with a bounded catch-up count.
- Ragdoll integration preserves velocity across timestep changes, uses time-based damping and substeps large updates for stability.
- Projectile lifetime is measured in seconds rather than frames; particles use time-based damping.
- CPU hazard checks run independently of its slower decision timer. Actual danger is distinguished from lookahead padding, and a retreat without a usable route no longer requests a jump.
- **Hotfix 0.2.1:** a player report caught the 0.2 CPU repeatedly jumping and killing itself on the first map despite the earlier green suite. A real countdown simulation reproduced seven jumps in roughly three seconds followed by a water death. The CPU now commits to platform-to-platform routes and landings instead of panic-jumping, and the first-map test requires survival **and engagement** with the player.
- Thin ledges now behave as one-way platforms for fighters, without side/underside blocking, and the jump impulse can clear the maps' 170 px rises. This makes the intended upper route physically usable instead of merely assumed by AI logic.
- Voice-off gates both match and UI announcements. Sound remains independently configurable.
- The arsenal uses text for its locked state rather than an emoji that can fall back to a missing-glyph box.
- Application metadata moved from 0.1/1 to 0.2.1/3.

## Regression suite

`app/src/test/java/com/arena/duelist/SmokeTest.java` contains **38 authored Robolectric tests**. The suite covers:

1. Menu-to-match flow and rendered quick-match frames.
2. Local Duel and Survival flow.
3. Menu reachability, Boss Tournament access, two-tap purchase and starter persistence.
4. Android Back clearing stale menu hitboxes.
5. K.O. ragdoll creation, settling and held-weapon rendering.
6. Ragdoll joints remaining finite.
7. Gravity-off fighter and ragdoll behavior, including dead bodies in a match.
8. Signed PCM audio amplitude conversion.
9. Swept projectile collision with thin platforms.
10. Standing active-body pose tracking.
11. Light-hit flop and recovery with AI input isolated.
12. Heavy-hit knockdown, recovery and invulnerability.
13. Four-jab poise break and downed fighters ignoring input.
14. Crouch pose keeping the feet on the platform.
15. Ragdoll stability in every arena.
16. Equivalent ragdoll motion at 30 and 60 updates per second.
17. CPU lava retreat without a panic jump when no route exists.
18. Jumping through and landing on a reachable thin ledge.
19. First-map opening CPU survival, no jump loop, and actual engagement.
20. CPU opening survival in every hazard arena.
21. Second-based projectile lifetime at different update rates.
22. Double-K.O. replay without a point.
23. Survival mutual-K.O. defeat without a phantom wave.
24. Five-boss tournament order, break transitions, rewards, final victory and mutual-K.O. loss handling.
25. Countdown input lockout and stale-action clearing.
26. Voice-off suppression of UI and match speech.
27. Real pointer release activating a weapon skill.
28. Cancellation not activating a skill and final touch coordinates setting its direction.
29. A concrete effect for every one of the twelve weapon skills.
30. Weapon swing wind-up, strike and recovery poses.
31. Fast sword contact across the sampled swing path.
32. Upward melee contact following the weapon direction.
33. Rendering every arena and weapon combination.

The same suite writes actual rendered PNGs to `docs/screenshots` through CI. These images expose layout/rendering regressions, but they do not prove touch latency, device performance or gameplay feel.

## Verification boundary

- Java grammar parsing is used as a local static check because this sandbox has no JDK/Android SDK.
- The authoritative compile and test gate is GitHub Actions: `gradle assembleDebug testDebugUnitTest` on JDK 17, compileSdk 34.
- CI publishes a debug-signed APK and test-rendered screenshots after a successful non-PR build.
- **No physical Android device gameplay session has been completed in this environment.** Multi-touch ergonomics, OEM font/rendering differences, TTS behaviour, frame pacing and sustained performance still need on-device validation.
- Automated tests exercise engine state and Robolectric rendering; they are not proof that the game exactly matches the original.

## Remaining original-game gaps

| Area | Original reference | Current repository |
|---|---|---|
| Core 1v1 duel, weapon drops and hazards | Present | Present, independently implemented |
| One-stick movement/weapon control and directional release skills | Present | Present for the current 12-weapon roster |
| Same-device two-player duel | Present | Present |
| 3–4 player party matches | Advertised | Absent |
| Survival | Present | Present, with different balancing |
| Football and additional modes | Advertised | Football/other modes absent; an independently designed five-boss tournament is present, but reference-mode parity is unverified |
| Map editor | Advertised | Absent |
| Wi-Fi/LAN multiplayer | Advertised | Absent |
| Content scale | Much larger weapon/map roster | 12 weapons / 10 arenas |
| House rules | Gravity, instant K.O., energy shield | Independent implementations present |
| Physics/combat fidelity | Original proprietary behaviour | Similar category, not demonstrated to be identical |
| Online Internet service | Not required for the current build | Absent; current game works offline |

## Recommended next work

1. Test the APK on physical phones/tablets, especially simultaneous two-player touches, small screens, notches and lower-end GPUs.
2. Add 3–4 player matches only after refactoring the current two-fighter assumptions in combat, HUD and round logic.
3. Add football, other missing modes and the map editor as separate tested systems; do not treat the independent boss tournament as reference-equivalent without validation.
4. Expand the weapon/map roster and tune against captured original gameplay if exact fidelity is required.
5. Add LAN discovery/transport and deterministic/session reconciliation before claiming Wi-Fi multiplayer.
6. Add production signing, adaptive/store icons and a release-testing matrix before any store distribution.
