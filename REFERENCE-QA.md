# Trailcloth 0.2.0 — calibrated cloth, compatibility and performance

## Reference and runtime

The reference grid is **17×49** (833 sites, 1,536 drag triangles). It uses double precision, 24 substeps per Minecraft tick, 44 projection passes per substep, a world-oriented particle state with moving shoulder attachments, mass lumped by rest triangle area, compliant warp/weft/shear constraints, and curvature stencils. Per-triangle pressure uses the normal and tangent components of relative air velocity; area determines its contribution to adjacent particles. The material is a single light-cloth surrogate (0.16 kg/m² by modelling convention), not a measured fabric. A small one-way wake, torso shielding and medium coefficients approximate airflow; this is not a fluid solver. The back collider and shoulder safety plane are conservative approximations. Cloth self-contact and block collision are not computed.

The runtime keeps **5×13 / 3×7**, two substeps per tick, render interpolation, the existing texture/render hook and distance policy. It approximates pressure with a normal per row and node-relative drag, retains a small filtered motion history, and limits strain in both directions. Nine fitted scalars per grid control drag, inertial coupling, airflow, damping, bending and turning. A deterministic wind disturbance is input velocity, not a prescribed pose or baked flutter wave. No reference meshes, third-party cape content, fitting classes, traces or external runtime libraries are included in the mod JAR.

## Offline results

The coordinate search used **five** original trajectories: a forward stride/surge, running hop, climb/dive, turn/strafe, and submerged drift. Its loss weighs lower rows more and penalizes excessive single-tick sideways motion. Three separate combinations were excluded: oblique drop with turn/reversal, upward boost with braking/backward movement, and ledge fall with landing. The comparator runs the actual 0.1.4 JAR in a separate Java classloader. Errors here are **3D distances in blocks** at common normalized mesh locations; the pinned row and first 20 settling ticks are excluded. Do not compare the two density scores to one another as if their sample locations were identical.

| Held-out result | 5×13: 0.1.4 → 0.2.0 | 3×7: 0.1.4 → 0.2.0 |
| --- | ---: | ---: |
| Whole-cloth weighted RMS | 0.3176 → **0.1988** (−37.4%) | 0.3170 → **0.1873** (−40.9%) |
| Hem RMS | 0.4753 → **0.2937** | 0.4551 → **0.2713** |

All six scenario/grid scores improved; not every individual frame did. Extreme turns and sudden repeated boosts still show phase and folding differences. On climb/dive and turn refinement checks, reference sensitivity at common points was **0.029–0.096 blocks RMS**, with a maximum hem RMS difference of 0.166. The largest difference was for the sharp-turn time-step refinement. This is a more detailed comparison target, not numerical convergence to real cape behavior or experimental proof. The image export carries a downsampled view of the dense reference; numerical metrics use the common nodes themselves.

The standalone `clothTest` covers turning, seeded airflow, water damping, hop/landing, steady ascent and descent, strain, interpolation, remeshing, reset and watertight UV/mesh topology. It measured **0 bytes over 1,500 warmed-up solve-and-mesh iterations**. The calibrated model no longer targets the particular lift timing or flutter amplitude hardcoded for 0.1.4: e.g. a standing fall takes longer to unfold, and strong constant airflow can flap more than in the old version. Takeoff still trails initially and the hem settles after stopping.

## Matched GTNH performance

GTNH 2.9.0, Java 25, UniMixins 0.3.1, Angelica 2.2.43 OpenGL with shaders off, 854×480. The QA fixture fixes daytime/weather and spawns the same actors. The 24-cape scene splits actors evenly among near (around 13 blocks), mid (around 33), and far (around 62). Each row is the median of four 18-second recorded rounds from two independent launches. Runs were separate rather than tightly interleaved between every version; whole-frame P95 includes game and host activity. Cape CPU time includes measured solve and cloak submit/fallback, divided by captured frames. The absent client has no cape timing hook.

| Mode | No-cape frame P95 (ms) | One-cape frame P95 (ms) | 24-cape frame P95 (ms) | 24-cape CPU (µs/frame) |
| --- | ---: | ---: | ---: | ---: |
| Trailcloth absent | 1.487 | 1.854 | 3.172 | — |
| 0.1.4 BALANCED | 1.528 | 1.933 | 3.769 | 217.51 |
| 0.2.0 BALANCED | 1.708 | 1.925 | 4.161 | 223.04 |
| 0.2.0 FULL | — | — | 4.791 | 437.40 |

Relative to 0.1.4, the calibrated model's 24-cape median was **2.5% higher** in the time measured by the cape hooks. Relative to current FULL, the distance policy cut that time by **49.0%**. These are not whole-client FPS improvements. The no-cape mixed-session whole-frame P95 was **14.9% higher** than absent, missing the original +3% gate in this run, even though there were zero measured cape steps or draws. Treat this as a failed/variable frame-time gate, not as proof that the measured percentage was spent in cloth work.

The all-client-thread allocation rates for the 24-cape scene were 97.89 KiB/frame absent, 98.23 on 0.1.4 BALANCED, 98.79 on 0.2.0 BALANCED, and 89.99 on 0.2.0 FULL. These measure the entire client thread, not allocation attributable to the cape alone. In particular, they are not comparable to the standalone solver's warmed-up allocation test.

### Separate no-cape control

A second experiment used zero-cape scenes only, with three 18-second recorded rounds per condition and one fresh launch per condition. Shaders were off; both conditions used the same 854×480 test fixture and had zero measured cape steps and draws. The absent run's three P95s were 3.8358, 3.4233 and 3.5078 ms; the installed run's were 3.4019, 2.4142 and 2.9162 ms. Medians were **3.5078 → 2.9162 ms (−16.9%)**. The direction reversed, and the baseline itself was much higher than in the mixed-session benchmark. This control shows run-to-run sensitivity; it does not replace the failed +3% gate or establish a whole-client speedup. Results are in `empty-control-base.csv` and `empty-control-new.csv`.

## Client visual and compatibility checks

The reobfuscated candidate ran in the disposable Prism copy `GTNH-290-Trailcloth-client-test`, not the daily profile. With shaders off, the fixture rendered an asymmetric standard-layout test cape and the GregTech donor cape resolved from the installed mod. The reviewed views did not show double drawing or obvious UV reversal. With Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5, the side-view replay covered jump, fast climb, fall and landing (224 simulated cloth ticks). The sampled poses rendered and were inspected; this was not a pixel-perfect shadow comparison or a new exhaustive compatibility matrix. Water, reset and LOD coverage for this version is from the standalone checks. This build was not requalified against a separate mod-free server. Earlier versions' results do not certify 0.2.0.

## Tested local artifact

- `trailcloth-0.2.0.jar` SHA-256: `18ab4463f515020113835228d2a330966247f1752715db24cc0f45e82ce808a2`.
- The adjacent PNG and GIF compare the old runtime, the offline reference and the calibrated runtime on identical synthetic trajectories, not game footage. Raw local logs and timing data are under `build/benchmarks/0.2.0`; reference scores and sensitivity results are under `build/reference`.
- This is a locally tested build, not a verified public release. No upload was performed. Replace the old Trailcloth JAR; don't install the old and new JARs together.
