# Trailcloth 0.1.1 — rapid vertical flight

The old solver applied upward or downward motion mainly as a uniform pull. Its vertical load saturated at 0.7 blocks/tick, and straight downward loading remained positive even during a fast pure dive. Version 0.1.1 filters signed vertical speed per cape and varies pressure down the fabric. A fast climb puts more rearward load toward the hem; in a dive the hem lifts while the shoulders stay fixed. Braking releases the pressure gradually. The change keeps the existing two substeps, mesh densities, torso contact and stretch limiter, with reduced vertical loads underwater.

## Solver regression

A rested, armoured cloth was driven at ±1.4 blocks/tick, with stops between phases. The **bend** is the distance in the Y/Z side plane from the center of the cloth to the straight shoulder-to-hem line at the 14th climbing tick and 24th diving tick. A flat sheet that only rotates stays near zero on this measure.

| Cloth grid | 0.1.0 climb | 0.1.1 climb | 0.1.0 dive | 0.1.1 dive |
| --- | ---: | ---: | ---: | ---: |
| 5 × 13 | 0.005 blocks | 0.070 blocks | 0.014 blocks | 0.197 blocks |
| 3 × 7 | 0.005 blocks | 0.068 blocks | 0.014 blocks | 0.193 blocks |

The regression fails with the old solver and passes with the new one. It also checks inertia after braking, settling at hover, reduced response underwater, contact and stretch under fast reversals with forward movement, and clearing the vertical filter when the cloth resets. Existing sideways motion, LOD, texture and mesh tests still pass. The standalone warmed-up cloth/mesh loop with vertical movement allocated 0 bytes over 1,500 iterations; this is not a measurement of total client allocation.

## In-game replay

The same scripted actor with an iron chestplate and a procedural normal-layout cape flew up and down at ±1.4 blocks/tick in the isolated Prism GTNH 2.9.0 client with Java 25 and Angelica 2.2.43 OpenGL. The rig followed the actor with the camera and captured hover, ascent, ascent braking, descent, and descent braking. This was a controlled replay, not a test of every jetpack or custom armor. With shaders off, both 0.1.0 and 0.1.1 recorded 155 high-density cloth steps in the 155-tick sample. Comparing the screenshots shows an arc during ascent and a raised, curved hem during descent and after braking in 0.1.1 rather than the old nearly straight silhouette.

The 0.1.1 run with Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5 showed the same climb/dive silhouette. It also recorded 155 cloth steps, with approximately two high-density mesh submissions per frame (consistent with the additional shadow pass) and no distance fallbacks. No duplicate cape or obvious iron-chestplate intersection was visible at the sampled poses.

The captures and counts are in `build/measurements/0.1.1/`. The previous full benchmark and network qualification in `QA-REPORT.md` are for 0.1.0 and were not repeated for this local preview. The new binary tested in the replay is `trailcloth-0.1.1.jar`, SHA-256 `4cef7691e8951e5ce49c0ed7c2ca0026137b7b6e3ba0a7d7e287830e66400974`. Neither test assets nor shaders are bundled. No upload or installation to a daily profile was performed.
