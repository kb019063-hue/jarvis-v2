# 0.3.1 — Physical-phone playability fixes

The player reported passive AI at close range, recurring fall/death visuals, camera zoom pumping and arenas split by large middle gaps. This pass prioritizes those issues rather than adding more modes.

## Diagnosed causes

1. **Impossible fist range:** the old CPU only entered its attack branch at <= 36 × 0.85 = 30.6 px. Fighter separation holds standing opponents >= 32 px apart. It therefore approached but never requested a normal punch. Ranged AI also only fired in a narrow ideal-distance band and retreated without firing at close range.
2. **Corpse recreation:** updateRagdolls cleared a body after it fell out of the world, then recreated it on the next tick because the fighter was still marked dead. That body started again at the fighter's original position. The new path only reconstructs a missing body for a living, knocked-down fighter; a completed corpse stays removed until an explicit new round/reset.
3. **Corpse-driven camera:** camera focus explicitly weighted toward ragdolled/dead bodies, including bodies far below the stage. Their movement and repeated recreation changed the zoom and pan. The camera now holds its last combat frame during K.O., break and result phases.
4. **Arena topology:** the default layout and other arenas had large holes or disconnected platforms between the spawns. Survival alone did not prove those layouts were enjoyable or navigable in a real fight.

## Changes

- CPU attack reach includes physical hand/blade reach and body contact allowance.
- Close combat takes precedence over routing; short-range opponents are not ignored while planning jumps.
- Basic attacks can fire when cooldown permits without waiting for a movement decision. Difficulty influences attack cadence.
- Gunners fire at nearby targets and while repositioning, with platform line-of-sight checks and edge-aware retreat.
- Dash skill use is gated by a floor-ahead check. This is a short look-ahead safety heuristic, not a full trajectory predictor.
- Removed completed-corpse respawning; living knockdown recovery still has a body.
- Rebuilt ten maps with continuous central floors and same-level grounded spawns. Optional side ledges remain reachable. Hazardous maps retain outer-edge hazards, not a central obstacle across the fighting route.
- Fit living fighter positions within the HUD/control-safe camera region; use zoom hysteresis and a delay before zooming back in. Do not track animation-joint jitter for standing fighters or chase corpses.
- Reset CPU routing and camera framing at each new duel round. Removed duplicate countdown camera updates.
- Metadata: versionCode 5, versionName 0.3.1.

## Tests and evidence

`PlayabilityTest` adds nine test methods. They exercise actual CPU damage, not a distance-only assertion:

- Fist attack requests at 40 px on all four difficulties.
- Close-range gun attack requests and actual bullet damage before ammo runs out.
- Basic contact damage for each melee weapon, with release skills disabled.
- Actual damage from a full countdown on every map, seeded runs, no opening CPU self-death or jump loop.
- An unbroken, hazard-free floor across each pair of spawns.
- A completed out-of-world corpse remaining absent for another 300 ragdoll updates.
- K.O. camera transform remaining unchanged while a corpse falls/disappears; one round point only.
- Both fighters staying inside the camera's safe region at four landscape aspect ratios.
- Two K.O. rounds progressing to a result instead of continuing to respawn indefinitely.

Total authored suite: 58 methods across SmokeTest, FootballRegressionTest and PlayabilityTest. The initial fix build passed all 58 and assembled the APK:
https://github.com/kb019063-hue/jarvis-v2/actions/runs/40009183868

That initial pass also regenerated screenshots, which were inspected for the new connected floors. Follow-up changes tighten the camera's bottom safe margin, add the actual close-range bullet check and vary CPU attack cadence; their final CI results are linked on the branch/commit checks. CI publishes XML reports and lists all passing methods in the commit summary.

The sandbox has no Android JDK/SDK or attached phone; Java grammar checks ran locally and compilation/Robolectric tests ran in GitHub Actions. These checks do not establish real-phone frame pacing, control feel, perfect navigation on every custom situation or bug-free gameplay. The player should retry the first arena, close-range fists/guns and a K.O. on the new APK.

## Install caveat

These are still debug-signed test APKs, not production releases. The CI signing identity is not persisted between clean runners. A phone may reject this as an update to an older build; uninstalling the old test app permits installation but deletes local progress. Production signing continuity remains separate work.
