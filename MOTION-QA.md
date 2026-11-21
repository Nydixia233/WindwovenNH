# Trailcloth 0.1.3 — motion QA

## What changed

The review recording had two distinct issues: the upper cloth and hem moved to the other side nearly together around 22.2–22.7 seconds, and the hem repeatedly curved into a hook in the sustained dive around 18.4–20.0 seconds. The fixture recreates those kinds of movement with synthetic inputs, not with the recording or extracted motion data.

The horizontal air load follows the new movement at 38% per tick in the upper cloth and 24% in the lower cloth; the last rows receive another 20% follow-through to prevent the whole sheet crossing the center at once. On yaw changes, the solver rebases the prior airflow into the new body frame and retains a bounded fraction of the previous orientation in both Verlet histories, increasing toward the hem. The previous direct yaw forces are gone; strafing and wind can still twist the two edges. The vertical filter is evaluated once per tick. Sustained diving uses a lower curl load of 9 instead of 15, with a brief acceleration contribution up to 13. The two quiet travelling waves begin below 35% of the cloth length, with seeded gust targets spaced 8–15 ticks apart and interpolated smoothly. No new grids, constraint passes, render-side displacement, or config values were added.

## Matched solver results

Both runs used the same trajectories and chestplate clearance. Lateral reversal changes X input from +0.70 to −0.70 blocks/tick, holding forward speed at 0.40. Lag is the smallest difference in the first centerline crossings of the one-third row and hem, across four seeds. Biggest lateral step includes **every free node** in the first 25 ticks of each reversal. Dive bow is the median absolute distance of the center node from the straight shoulder-to-hem line in the Y/Z plane over 240 samples after 100 ticks at −2.40 blocks/tick. The flutter range is the peak-to-peak hem height at a constant forward speed of 0.90 blocks/tick.

| Metric | 5 × 13, 0.1.2 → 0.1.3 | 3 × 7, 0.1.2 → 0.1.3 |
| --- | ---: | ---: |
| Minimum delay between upper cloth and hem switching sides | 0 → **2 ticks** | 1 → **2 ticks** |
| Largest single-tick lateral step in a reversal | 0.318 → **0.148 blocks** | 0.218 → **0.094 blocks** |
| Median sustained-dive bow | 0.188 → **0.125 blocks (−33.5%)** | 0.195 → **0.126 blocks (−35.8%)** |
| Hem height range at constant high speed | 0.350 → **0.132 blocks** | 0.330 → **0.123 blocks** |

The sustained dive still lifts the hem by an average of 0.362 and 0.375 blocks versus the respective resting poses. The wet-flag run reduces steady-speed flutter to 0.024 and 0.022 blocks, and the 0.18-block/tick walk settles without ongoing flutter. Both corners still lead at different times. The reversal, dive and restrained-flutter checks reject the 0.1.2 implementation on both grids. Existing constraints, reset, LOD, mesh and allocation checks pass. The standalone warmed-up loop, including active flutter and yaw, measured 0 bytes allocated over 1,500 solve-and-mesh iterations; this is not whole-client allocation or frame timing.

## Scripted Prism replay

The isolated GTNH 2.9.0 client used Java 25 and Angelica 2.2.43 OpenGL. The separate QA fixture gives an actor a standard iron chestplate and a generated asymmetric test cape, then repeats lateral reverse, a moving 90° turn, rapid climb, sustained descent and braking with rear and side cameras. The 0.1.2 and 0.1.3 runs were matched with shaders off; 0.1.3 was also visually checked with Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5. In the 0.1.3 sequence the shoulder stays fixed while the hem follows, the descending cape retains an arc with fewer deep hooks, and it relaxes after braking. At the inspected frames there was no visible double cape, texture flip, or obvious iron-chestplate intersection. The shader run maintained the same 292 counted cloth steps per angle as the non-shader runs and submitted geometry to both render passes.

The full-pack performance and network gates were **not** rerun. The capture reports include screenshot overhead and are not performance comparisons. The replay does not certify the recording's particular armor or jetpack. The original video was used only for analysis and is not bundled. Local measurements and captures are under `build/measurements/0.1.3/`. Tested artifact SHA-256: `92be03197d47aed8b67bfd02c28b72667e26ba4c7db246f6379fdf7f63d46446`.

## Reproduce

```powershell
.\gradlew.bat --offline build qaJar sourceBundle
.\tools\Invoke-TrailclothLab.ps1 -Mode review -Run motion-old -ModVersion 0.1.2 -Screenshots
.\tools\Invoke-TrailclothLab.ps1 -Mode review -Run motion-new -Screenshots
.\tools\Invoke-TrailclothLab.ps1 -Mode review -Run motion-euphoria -Shader -Screenshots
```

The old JAR must be available in `build/libs`. Run only against the disposable Prism client with `-Dtrailcloth.metrics=true` as described in the README. The fixture and metrics option were removed after the replay; only the regular 0.1.3 JAR remains in that client. No daily profile, pre-existing save outside QA, or remote release was modified.
