# 0.3.0 — Football and reliability pass

Date: 2026-10-23. This implements a first portion of the requested move toward Supreme Duelist Stickman, not full parity.

## Playable additions

- **Football vs CPU** and **Football · 2 Players** buttons on the title screen.
- An enclosed stadium with two goal mouths, nets, center markings and a camera that includes both goals.
- Substepped ball physics: gravity, floor bounce/rolling resistance, wall/ceiling bounce and fighter contacts.
- Automatic contact kicks and a directional release **power kick**, with proximity and recharge requirements. Same joystick controls as duels; both players can hold their sticks simultaneously.
- CPU approach behavior, aerial interception attempts and power kicks toward the opponent's goal.
- First to three goals, full-ball goal-line checks below the crossbar, a goal celebration, reset kickoff countdown and a final result/reward.
- Pause, restart, reset pitch, rematch and return-to-menu flows use existing match UI. System interruptions pause explicitly rather than immediately resuming play.
- Football deliberately does not apply weapon damage, ammo drops, duel hazards or duel gravity/shield house rules.

The football mode is independently designed. Its exact movement, abilities, rules and presentation have not been validated against the reference game's football mode.

## Reliability fixes

- **Projectile ordering:** consider all platform contact times and fighter contact on the original swept segment; resolve the nearest event. A wall after the opponent no longer suppresses an earlier hit, and platform insertion order no longer determines impact location.
- **Haptics:** declare Android's VIBRATE permission.
- **Interruptions:** clear held controls and pause gameplay on Activity pause or focus loss; resumption requires the pause-menu Resume action.
- **Surface loop:** request stop/interrupt, wait briefly for termination and retain an outstanding thread reference so a replacement cannot overlap it. Retry startup only while foregrounded and the surface is valid. Stop checks also guard simulation catch-up and drawing. A slow Android surface unlock is logged, not treated as permission to start another thread.
- **Diagnostics:** report expected surface availability exceptions instead of silently swallowing every rendering Throwable.
- **Metadata:** versionName 0.3.0, versionCode 4; corrected the stale title-screen version label.

## Verification

49 authored Robolectric tests: 39 SmokeTest methods and 10 FootballRegressionTest methods. New coverage includes:

- Both football menu entries and local two-pointer movement, with rendered screenshots.
- Complete-ball goal detection, each scoring direction and one-shot goal events.
- Above-crossbar wall bounce instead of a false goal.
- Power-kick proximity/direction requirements.
- Three-goal victory and defeat, kickoff reset and exactly-once final rewards.
- Countdown/paused ball freezing and cleared movement.
- No combat damage, projectiles or K.O. deaths during football.
- Ball simulation consistency at 30/60 update rates.
- Opponent-before-wall hits and wall-before-opponent blocking, including reversed platform insertion order.

First Android CI run passed assembly and all test steps:
https://github.com/kb019063-hue/jarvis-v2/actions/runs/39990821377

That run built the main implementation at 32991b1 and generated the committed football screenshots. A follow-up build validates the documentation, instruction text, additional loop stop checks and test-report publication changes. Java grammar parsing and git diff --check were also run locally; no Android SDK/JDK is installed in the sandbox.

CI now publishes XML test reports as an artifact and includes all passing test names in its build summary comment. Screenshots were visually inspected, but no physical-device test session was performed. In particular, phone touch ergonomics, CPU strength, haptics, interruption behavior under actual SurfaceHolder contention and sustained performance still need device validation.

## Remaining work before claiming reference parity

- Three/four-player local matches and generalized multi-fighter combat/HUD.
- Map editor and saved user maps.
- Additional modes and the reference-scale weapon/map roster.
- Network multiplayer if targeting a reference version that supports it.
- Reference-verified weapon abilities, controls, appearance, animation, physics and balancing.
- A defined target reference version and captured side-by-side device testing.

No proprietary code, artwork or audio was imported. This remains independently branded Stick Duelist, not the official Neron's Brother game.
