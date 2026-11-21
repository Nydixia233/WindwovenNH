# Trailcloth 0.1.4 — vertical-motion QA

## Cause and change

0.1.3 added strong rearward spill and a small upward load at the hem when climbing fast. The cape tilted back and the loose end rose relative to the shoulders. The old scripted standing jump showed a small downward lag, not a reversed axis. Conversely, the old descent load was weak until high falling speeds and was concentrated near the free end.

0.1.4 uses the bounded change in vertical speed for the initial inertial lag and smoothly filtered speed for sustained airflow. Fast ascent no longer adds an upward hem load, and the cape can still trail behind horizontal motion. For descent, earlier rearward airflow and distributed lift raise the middle with the hem instead of curling only the tip. The filter and vertical speed history reset with the cloth. The mesh, strain limiter, torso contact, LOD and render hook are unchanged, and the flat-speed flutter and reversal regressions still pass. No new config is needed.

## Matched solver results

Distances are measured relative to the shoulders in blocks. For the takeoff row, a positive value means a **downward** lag compared with the pre-jump travelling pose. Other rows measure upward lift from rest. A standing cape is already close to fully extended, so downward lag is subtler when jumping from rest.

| Motion | 5 × 13: 0.1.3 → 0.1.4 | 3 × 7: 0.1.3 → 0.1.4 |
| --- | ---: | ---: |
| Running jump, hem down on tick 3 | 0.010 → **0.080** | 0.010 → **0.070** |
| Sustained climb (+1.4 blocks/tick), mean hem up | 0.175 → **−0.003** | 0.195 → **0.013** |
| Gravity-driven drop, hem up after 14 ticks | 0.047 → **0.463** | 0.036 → **0.493** |
| Sustained fast dive (−2.4 blocks/tick input), mean hem up | 0.361 → **0.571** | 0.375 → **0.569** |
| Same sustained dive, mean middle up | 0.093 → **0.245** | 0.093 → **0.242** |

The sustained dive has less bow despite greater lift. Both builds clip vertical airflow input at −2 blocks/tick. At −1.4 blocks/tick, the underwater-flag run lifts the hem by about 0.34 blocks, versus 0.57 in air. The jump, drop, and sustained descent regressions reject 0.1.3 at both grid densities. The isolated warmed-up solve-and-mesh loop again reported 0 bytes allocated over 1,500 iterations; this is not whole-client performance data.

## GTNH replay

In the isolated Prism GTNH 2.9.0 client (Java 25 and Angelica 2.2.43 OpenGL), a scripted actor wore standard iron chest armor and a generated cape swatch. Both builds replayed standing jump, running jump, fast vertical ascent, and falling to a landing along the same path with shaders off. The new build repeated the run under Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5. In sampled frames the climb was draped, the cape opened earlier during descent, and it went back to a resting drape after landing. No obvious chestplate intersection or duplicate cloak was visible. Each replay recorded 224 cloth ticks, with 81 captured frames in each shaders-off run and 80 in the shader run; the shader pass did not duplicate the solve.

The full-pack performance and network gates were not repeated, and this test does not certify specific third-party armor or jetpacks. No daily instance or saves were modified. The runtime JAR is independent and includes neither the fixture nor cape assets. Tested SHA-256: `0edc2d67e3f754e1855777ed076d1f6c61649e5b2f8896944c8adf21bf023e3f`.

## Reproduce locally

```powershell
.\gradlew.bat --offline build qaJar sourceBundle
.\tools\Invoke-TrailclothLab.ps1 -Mode vertical -ModVersion 0.1.3 -Run vertical-before -Screenshots
.\tools\Invoke-TrailclothLab.ps1 -Mode vertical -Run vertical-after -Screenshots
.\tools\Invoke-TrailclothLab.ps1 -Mode vertical -Run vertical-shader -Shader -Screenshots
```

Use only the disposable client with `-Dtrailcloth.metrics=true` as described in the README, and place the old JAR in `build/libs` for the comparison. Local evidence is in `build/measurements/0.1.4/`. Capture times include screenshot overhead and are not performance comparisons. The fixture and metrics flag were removed after QA, leaving only the regular 0.1.4 active in the isolated client.
