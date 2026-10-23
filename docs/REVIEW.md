# Current review: Stick Duelist vs. *Supreme Duelist Stickman*

**Review date:** 2026-10-23

**Current application version:** 0.2 (`versionCode 2`)

## Verdict

This repository contains a real native Android game with original Java code, Canvas artwork, synthesized sound, active ragdoll bodies, three modes, twelve weapons and ten arenas. It is **not an exact recreation or a completed port** of Neron's Brother's *Supreme Duelist Stickman*.

The repair pass corrected confirmed match, persistence, input, audio, physics and combat defects and moved the controls closer to the original's one-stick design. Those changes do not fill the original's content and mode gaps. The result must not be described as full parity.

Reference baseline: the [official Play Store listing](https://play.google.com/store/apps/details?id=com.Neurononfire.SupremeDuelist), the [community wiki](https://supreme-duelist-community-wiki.fandom.com/wiki/Supreme_Duelist_Stickman), and the control description on [Uptodown](https://supreme-duelist.en.uptodown.com/android). Original-game feature totals can change between releases; the comparison here is a reference snapshot, not a claim to have reproduced a particular proprietary build.

## What the repair pass changed

### Controls and combat

- Replaced the separate ATK/JMP/DEF clusters with one stick per player.
- Holding a direction now moves, aims and performs basic attacks; up jumps, down shields/crouches, and releasing a charged direction activates the weapon skill.
- Added distinct release-skill implementations for all twelve current weapons, with per-weapon cooldowns and visible charge rings/labels.
- Routed rendered weapon orientation and melee contact through the same angle and physical hand/tip geometry.
- Replaced broad melee rectangles with current weapon-segment-to-body-joint contact tests. This is an improvement in directional contact, not a claim of continuous swept collision or the original's exact physics.
- Added CPU use of release skills and updated the in-game instructions and arsenal text.

### Match and state correctness

- Newly purchased starter weapons are saved **after** the starter field changes, so they survive recreation of `Profile`.
- A double K.O. awards no round point and replays the round; a 1–1 draw can no longer turn into a contradictory tied defeat.
- Survival checks player death before enemy death, so a mutual K.O. cannot award a wave or spawn another enemy before defeat.
- Countdown and post-round simulation reject gameplay input and clear stale movement/action state.
- Round transitions clear projectiles and held controls, preventing post-K.O. hits and accidental skill releases.
- Menu transitions clear stale hitboxes; Android Back now does the same before the next frame is drawn.
- The arena picker, pause-to-arena flow, purchase/reset confirmations and Survival scaling fixes from the earlier review remain in place.

### Timing, AI and presentation

- The render loop uses a fixed 60 Hz accumulator with a bounded catch-up count.
- Ragdoll integration preserves velocity across timestep changes, uses time-based damping and substeps large updates for stability.
- Projectile lifetime is measured in seconds rather than frames; particles use time-based damping.
- CPU hazard checks run independently of its slower decision timer. The follow-up edge-case fix distinguishes actual danger from lookahead padding and leaves a hazard by its nearest edge instead of blindly moving toward arena centre.
- Voice-off gates both match and UI announcements. Sound remains independently configurable.
- The arsenal uses text for its locked state rather than an emoji that can fall back to a missing-glyph box.
- Application metadata moved from 0.1/1 to 0.2/2.

## Regression suite

`app/src/test/java/com/arena/duelist/SmokeTest.java` contains **23 authored Robolectric tests**, not merely 23 assertions. The suite covers:

1. Menu-to-match flow and rendered quick-match frames.
2. Local Duel and Survival flow.
3. Menu reachability, two-tap purchase and starter persistence.
4. Android Back clearing stale menu hitboxes.
5. K.O. ragdoll creation.
6. Ragdoll settling and finite joints.
7. Standing active-body pose tracking.
8. Light-hit flop and recovery.
9. Heavy-hit knockdown, recovery and invulnerability.
10. Four-jab poise break.
11. Downed fighters ignoring input.
12. Ragdoll stability in every arena.
13. Equivalent ragdoll motion at 30 and 60 updates per second.
14. CPU retreat from lava even between AI decisions.
15. Second-based projectile lifetime at different update rates.
16. Double-K.O. replay without a point.
17. Survival mutual-K.O. defeat without a phantom wave.
18. Countdown input lockout and stale-action clearing.
19. Voice-off suppression of UI and match speech.
20. Real pointer release activating a weapon skill.
21. A concrete effect for every one of the twelve weapon skills.
22. Upward melee contact following the weapon direction.
23. Rendering every arena and weapon combination.

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
| Football, boss tournament and additional modes | Advertised | Absent |
| Map editor | Advertised | Absent |
| Wi-Fi/LAN multiplayer | Advertised | Absent |
| Content scale | Much larger weapon/map roster | 12 weapons / 10 arenas |
| House rules | Gravity, instant K.O., energy shield | Independent implementations present |
| Physics/combat fidelity | Original proprietary behaviour | Similar category, not demonstrated to be identical |
| Online Internet service | Not required for the current build | Absent; current game works offline |

## Recommended next work

1. Test the APK on physical phones/tablets, especially simultaneous two-player touches, small screens, notches and lower-end GPUs.
2. Add 3–4 player matches only after refactoring the current two-fighter assumptions in combat, HUD and round logic.
3. Add the missing modes and map editor as separate, tested systems rather than menu-only placeholders.
4. Expand the weapon/map roster and tune against captured original gameplay if exact fidelity is required.
5. Add LAN discovery/transport and deterministic/session reconciliation before claiming Wi-Fi multiplayer.
6. Add production signing, adaptive/store icons and a release-testing matrix before any store distribution.
