# Trailcloth 0.2.2 — stationary torso turns

## Diagnosis and fix

`PlayerCloths` samples the wearer's `renderYawOffset` and its wrapped tick change. It does not sample the observer camera, mouse, or the wearer's head direction. In 0.2.1 the angular transport was around each strip's own shoulder pin, which made an almost vertical resting strip effectively invariant under a torso turn. In 0.2.2 the slow-travel branch instead uses the biped's vertical yaw axis, 0.125 blocks in front of the original cloak-call origin. It transports the current and previous free poses together, increasingly toward the free end. The two edges can twist differently because the existing constraints and rear contact still apply. The shoulder row remains pinned.

The pivot and coupling blend smoothly toward the previous moving values by four blocks per second of travel. The low-travel angular response is bounded and reduced underwater. No yaw means no angular transport; no airborne animation, additional solver step, renderer hook or configuration change was added. The same two distance tiers stay in place.

## Standalone checks

`gradlew.bat --offline build qaJar` passed. Variants include both spin directions, a full 360° turn with a wrapped heading, and no-body-turn idle controls. The regressions check initial lag sign, sustained twist, interpolation and head/back contact, post-stop carryover and convergence to rest. With a 15°/tick stationary turn, minimum (across the two signs) peak rear-depth difference between hem edges was **0.334 blocks at 5×13** and **0.247 at 3×7**. Minimum peak lateral center lag was **0.135** and **0.039 blocks** respectively. The original moving-turn, jump/descent, local collision, water, LOD, mesh and reset checks also passed. The warmed standalone tick+mesh test allocated **0 bytes over 1,500 iterations**. That check does not measure allocations or frame time for the rest of the game.

## Reference and scope

The 17×49 offline simulator now uses the same original cloak offset when moving pins around the body axis; drag and projection otherwise operate in world space. It is an approximate physical model, not experimental cloth behavior. The comparison uses the actual reobfuscated 0.2.1 and 0.2.2 JARs. Two trajectories were compared: a full stationary 360° turn, and a slower turn with a pause and reversal. In the former, 0.2.1's two hem corners differed by at most **0.001 blocks** in depth on either grid; 0.2.2 reached **0.334 / 0.247** versus **0.419** in the dense reference. Relative to that updated model, weighted 3D RMS over the two clips fell **0.1621 → 0.1236** blocks (5×13) and **0.1526 → 0.1150** (3×7). Shape and timing still differ, notably the dense model's larger vertical skew and the coarse model's smaller lateral displacement. No full reference recalibration or comparison of flight scenes was done in this version. Raw results are in `build/reference/0.2.2/turn-signal.csv` and `scores.csv`.

Nothing from the offline simulator or the QA fixture is shipped in the production JAR. No WaveyCapes code or assets are used. This is a targeted local preview, not a requalification of multiplayer or all earlier rendering/performance gates.

## GTNH A/B replay

Both JARs completed the same head-only, stationary torso turn, stop and reverse-turn fixture at 854×480 in `GTNH-290-Trailcloth-client-test` (GTNH 2.9.0 / Java 25 / Angelica 2.2.43, Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5). On both runs the solver recorded **159** high-detail cloth ticks. The reviewed frames show both capes hanging during the head-only segment, the old cape staying straight through the torso rotation, and the new hem trailing/twisting and returning after the stop. `turn-replay.gif` and `turn-comparison.png` are matched in-game screenshots cropped to the actor, not renders of the offline reference. The replay is visual evidence, not a frame-time benchmark or a new shader compatibility matrix.

`trailcloth-0.2.2.jar` SHA-256: `0d26003c976a64ddf79f5c3f7b65dd80eacb783da31b206189de28f7228c8ecd`. Only the disposable instance was updated; temporary fixture and marker were removed and the previous settings restored. No daily instance or saves were used, and no files were uploaded.
