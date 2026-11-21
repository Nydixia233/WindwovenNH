# Trailcloth 0.2.1 — shoulder lift QA

## What changed

0.2.0 clamped all free nodes to at most 0.025 blocks above the shoulder seam. 0.2.1 retains the pinned seam and rear torso/chestplate support, but tests contact against a head-sized region. Contact selects an accessible face (rear, lower/upper edge, or the side the parent is already on) and stays within the adjacent warp link's reach. This avoids a short link oscillating between an unreachable contact face and the strain guard. The seam itself owns the narrow neck attachment band.

The independent 17×49, 24-substep offline reference loads each face with drag along the relative airflow. Its vertical wake attenuation now recovers gradually as fabric moves away from the rear of the torso. The runtime approximates it by row with a filtered open-air velocity and the same two substeps; no vertical pose, above-shoulder speed trigger, or offline trace is shipped. The coarse-grid coefficients have additional damping/wake adjustments to retain the strafe-reversal and sustained-dive checks. Underwater uses the sheltered, damped response instead of the new open-air contribution.

## Standalone regression checks

- `gradlew.bat --offline build qaJar` passed. The test checks the blocked volume and unobstructed positions behind, beside and above it. It checks interpolated poses as well as tick poses, and samples both triangles of the rendered quads against the upright helmet solid during loft. It excludes the narrow attachment band.
- In the separate oblique fall regression, the highest hem node reached **0.637 blocks above the seam** with 5×13 and **0.566** with 3×7. It exceeded 0.10 blocks during 40 and 41 falling ticks. The two edges were not in lockstep, and the fabric settled when motion stopped.
- The standing-hop checks never put the hem above the seam; at launch it still trails downward relative to its resting pose. Turning, water, stretch, mesh topology, LOD and reset checks passed. Sustained fast falls need not produce the same silhouette at the two resolutions. The warmed standalone tick+mesh run allocated **0 bytes over 1,500 iterations**; this is not a measurement of the rest of the client.

## Comparison against the updated reference

Verification loaded the reobfuscated 0.2.1 binary and the actual 0.2.0 JAR (the latter in an isolated classloader), not a reconstruction. Six trajectories were set aside from the coordinate search. In matched samples, held-out weighted 3D RMS decreased from **0.2751 to 0.1888 blocks (−31.4%)** for 5×13 and **0.2668 to 0.1792 (−32.8%)** for 3×7. Hem RMS was **0.4262 → 0.2883** and **0.3937 → 0.2577** respectively. All twelve held-out scenario/grid scores improved; individual poses and flutter phases can still disagree.

In the held-out turning fall, the tallest hem point was no more than 0.025 blocks above the seam on 0.2.0; it reached **0.643 / 0.699 blocks** on 0.2.1. The reference sampled at the same locations reached **0.593**. The stationary hop never crossed the shoulder in either model. The moving-hop trace can show a short lift when it starts with a billowed cape; this is different from a standing jump and is not a prescribed flip.

Banked-fall reference sensitivity was **0.021–0.025 blocks RMS**, comparing finer time steps and a finer spatial grid separately. Climb/dive sensitivity was **0.126–0.145 blocks RMS**; transition timing remains sensitive. These are not CFD, experimental evidence, or a universal claim that one simulated silhouette is physically correct. Per-case scores and lift data are in `build/reference/0.2.1/scores.csv` and `shoulder-lift.csv`.

## GTNH visual replay

Replayed the standing hop, steep turning descent and brake/settle sequence at 854×480 in the disposable Prism copy `GTNH-290-Trailcloth-client-test` (GTNH 2.9.0, Java 25, Angelica 2.2.43 OpenGL). Both shaders-off and Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5 completed with an unarmoured rear view and a standard iron helmet/chestplate side view. The selected side shots at ticks 80, 154 and 210 show ordinary drape, an above-seam hem with space behind the helmet, and drape restored after braking. There was no obvious helmet intersection, reversed atlas or double draw in the reviewed views. The separate six-actor scene with shaders off resolved and displayed the GregTech donor cape from the installed mod, alongside the standard-layout test swatch, hidden/invisible cases and armor. This is not a shadow pixel-diff test or a full compatibility matrix.

`shoulder-replay.png` shows those in-game views. The `reference-comparison` PNG/GIF instead show synthetic cloth at identical inputs, comparing 0.2.0, the updated offline reference and 0.2.1. No game content from the screenshots is shipped in the JAR or source ZIP.

## Client timing: 24 capes at mixed distances

Shaders off, 854×480, and the same near/mid/far (8/8/8) fixture. Two rounds of 300 recorded ticks (about 15 seconds each) in one launch per condition. Cape-hook time covers tick and draw/fallback CPU time divided by the measured frame count; tick time per solved cape also includes the enclosing tick hook. The motion-only shader runs are visual evidence and not timing inputs.

| Installed configuration | Cape-hook µs/frame | Tick-hook µs/solved cape tick | Whole-client P95 frame (ms) |
| --- | ---: | ---: | ---: |
| 0.2.0 BALANCED | 233.90 | 24.44 | 4.900 |
| 0.2.1 BALANCED | 193.49 | 24.99 | 2.713 |
| 0.2.1 FULL | 477.06 | 41.16 | 6.109 |

In these rounds, `BALANCED` took **59.4% less cape-hook CPU time per frame** than this version's `FULL`. Counters confirmed the high, low and far-original paths in `BALANCED`, and only the high path in `FULL`. The tested mix had slightly higher tick cost per solved cape on 0.2.1 than on 0.2.0. The observed lower per-frame time on 0.2.1 is **not** evidence of a general speedup: launches were not interleaved, and both host frame throughput and the number of cape draws per second varied. The separate no-cape rounds of those two installed builds had **zero** cape steps and draws, but their P95s also varied (3.321 vs 1.628 ms). This is not a new against-absent +3% gate test.

## Tested artifact

- `trailcloth-0.2.1.jar` SHA-256: `c14a83abe66acffc97c8328646b97154d83dd291aada87967be00a9fd782efc8`.
- Raw captures and timing data: `build/benchmarks/0.2.1`. Local output: `build/preview/0.2.1`. Only the disposable Prism copy was updated, with one production JAR at `BALANCED`. Fixture, marker and instrumentation flags were removed, and shader settings restored. No daily profile or save was used; no files were uploaded.

## Scope

This is a simulation comparison and a scoped local playtest, not measured textile or fluid behavior, a guarantee for arbitrary clothes, or a public-release sign-off. Head/armor contact uses standard biped proxies, not the exact geometry of every helmet, view angle or custom armor. There is no cloth self-contact or block collision. No new remote-server qualification is implied by the QA fixture. No WaveyCapes code or assets were used. The preview does not bundle the reference, benchmark fixture, game code or textures.
