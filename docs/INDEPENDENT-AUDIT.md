# Independent repository audit

Date: 2026-10-23. Reviewed checkout: `46763c08969e1d2dd6428cd567c43ac31f117efa`.

> This audit describes the pre-change checkout above. The subsequent 0.3.0 pass
> addresses projectile ordering, vibration permission and lifecycle handling, and
> adds independent football. See [the implementation update](IMPLEMENTATION-0.3.md).

## Verdict

This is an independently implemented, incomplete game inspired by **Supreme Duelist Stickman**, by **Neron's Brother**. It is not an exact recreation. The README and previous REVIEW already acknowledge that limitation. There is substantial game code, not just a visual mock-up, but implementation and automated coverage do not establish equivalent gameplay feel.

## Research baseline

- Official Google Play listing: https://play.google.com/store/apps/details?id=com.Neurononfire.SupremeDuelist&hl=en-GB
- Developer's Game Jolt description: https://gamejolt.com/games/supremeduelist/684028
- Community gameplay description (secondary, mutable source): https://supreme-duelist-community-wiki.fandom.com/wiki/Supreme_Duelist_Stickman

The official listing advertises ragdoll physics, CPU and 1–4 player play, Survival, football, maps/map editor, skins, and gravity/instant-KO/energy-shield options. The community description explains the directional single-joystick/release-ability model. Exact roster counts and multiplayer claims vary by version/source; no exact current content total or Internet multiplayer capability is assumed here.

## Comparison with source implementation

| Area | Checkout |
|---|---|
| One-stick input and release skill | Implemented in Game.pointerUp/applyStick/useAbility |
| CPU duel and same-device two-player | Implemented; Game has exactly two fighters |
| Ragdoll, knockdown/recovery, hazards | Implemented with original procedural code |
| Survival | Implemented |
| Boss mode | Original five-boss implementation; not demonstrated equivalent to the reference |
| Weapons / arenas | 12 / 10 original implementations |
| Gravity, instant KO, energy shield | Implemented independently |
| 3–4 player matches | Absent |
| Football / additional minigames | Absent |
| Map editor | Absent |
| Network play | Absent |
| Exact original art, weapon behavior, balance, physics and UI | Not established; own Canvas art, cooldown attacks, sampled melee contacts and custom physics |

Viewed the committed local-duel screenshot. It shows the current rendered interface, not evidence of real-device performance or exact visual fidelity. No side-by-side physical gameplay session was performed.

## Findings

### 1. Projectile collision order is incorrect — medium, source-confirmed logic defect

`Game.java:1109–1135` resolves platforms first, selects the first platform in list order rather than the smallest contact parameter, marks the projectile dead, and only then considers the opponent if it is still alive. If one frame crosses an opponent and then a platform, the later platform can suppress the earlier fighter hit. Multiple crossed platforms can similarly produce a wrong impact location. Bomb explosions inherit the incorrect impact location.

Fix: compute candidate times against all relevant platforms and fighter geometry on the original movement segment, then resolve the earliest valid contact. Add integration tests for fighter-before-wall, wall-before-fighter and reversed platform insertion order. Existing thin-platform tests exercise geometric intersection, not full event ordering.

### 2. Vibration has no manifest permission — medium, source-confirmed configuration defect

`MainActivity.buzz` calls Android's Vibrator APIs but `AndroidManifest.xml` declares no `android.permission.VIBRATE`. This normal manifest permission is needed; no runtime permission dialog is necessary. Security failures are swallowed by `catch (Throwable ignored)`, making haptics silently fail. Add the permission and check on a physical device.

### 3. Loop shutdown does not await thread exit — medium, lifecycle race risk

`GameView.java:71–77` requests shutdown but does not join/await the old loop. The run body checks `alive` only at its outer boundary (`132`), so a stopped thread can still finish ticks/drawing after shutdown returns. A quick reacquire can start a replacement while that old iteration remains in flight. Synchronized Game methods protect individual game operations, but do not guarantee one loop owner or safe SurfaceHolder lifetime. Surface destruction also returns without confirming the rendering thread has stopped touching the surface.

Fix with a lifecycle-safe stop/termination protocol and tests for rapid background/resume and surface recreation. Do not hold locks needed by the renderer while awaiting it. This is a code-identified race, not a reproduced device failure.

### 4. Backgrounding is not an explicit gameplay pause/reset — medium, validation risk

`MainActivity.java:140–159` stops/restarts the loop but does not enter the game's paused state or explicitly cancel controls. Existing pause/control clearing lives separately in Game. Gameplay resumes immediately after returning to the app; held-input behavior depends on delivery of touch cancellation. Explicitly pause and clear input on lifecycle/focus interruption, and verify Home, screen lock, notification shade and interruptions while dragging. Stuck input is a scenario to test, not a confirmed reproduction.

### 5. Rendering errors are silently hidden — low/medium, diagnostic flaw

`GameView.java:148–162` catches all Throwables from lock/draw/post without logging. A recurring render failure can appear as a frozen screen with no useful diagnostic. Catch expected surface lifecycle failures narrowly and report unexpected errors. Simulation ticks are outside this handler and can still terminate the thread.

### 6. Device/integration coverage is missing — significant release-validation gap

There are 38 authored Robolectric tests. They instantiate Game directly, use a FakeHost for audio/haptics and directly tick/render it. They do not exercise actual GameView thread ownership, Activity lifecycle, hardware vibration, real simultaneous touches, device GPU/frame pacing or OEM TTS. Saved screenshots are outputs, not pixel-comparison assertions against the original game. These tests are useful regressions, not proof of bug-free or exact gameplay.

## Verification performed and limits

- Inspected Java gameplay/input/rendering/lifecycle code, manifest, build configuration, tests, existing review and screenshots.
- Local Java and Gradle executables are absent; no Android emulator/device was available. Did not independently compile, run the suite, install the APK or reproduce gameplay bugs on a phone.
- GitHub API reports successful compile/test steps in run https://github.com/kb019063-hue/jarvis-v2/actions/runs/39989277904 for `a757de03eb9a0b9088d6bc8d0388bfefe60df908`. This is **not the exact checkout SHA**. GitHub compare reported no file differences between that revision and this checkout. No check-run result was returned for the checkout itself.
- Full CI logs could not be downloaded because the log-storage endpoint was inaccessible. Therefore this audit confirms the reported successful step, not an independently examined 38-test execution report.
- No gameplay source changes were made during this audit.

## Recommended order

1. Fix projectile event ordering and missing vibration permission, with focused regression coverage.
2. Make loop shutdown/resume and interruption behavior explicit and testable.
3. Perform physical-device tests on multiple aspect ratios with two simultaneous players, all weapons/maps and sustained CPU matches.
4. Agree a specific reference-game version and capture side-by-side gameplay for controls, physics, weapon abilities, balance and visuals.
5. Implement absent modes/content as separately scoped work. Do not label the current build an exact recreation or a production-ready release.
