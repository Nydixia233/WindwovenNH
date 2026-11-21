# Trailcloth 0.1.0 — QA and release evidence

> This is the full release report for 0.1.0. See [FLIGHT-QA.md](FLIGHT-QA.md) for the separate 0.1.1 flight preview.

## Environment

GT New Horizons 2.9.0 in two isolated Prism instances, Minecraft 1.7.10, Forge 10.13.4.1614, UniMixins 0.3.1, Java 25.0.1 with LWJGL3ify 3.0.42, and Angelica 2.2.43 using its OpenGL backend. Test machine: Intel Core i9-13900H and NVIDIA GeForce RTX 4070 Laptop GPU. A disposable flat world was used; no daily instance or save was modified.

## Unit and mesh checks

`gradlew.bat --offline build qaJar` passed. The independently authored motion tests cover pinned shoulders, acceleration/stop, asymmetric curvature, underwater damping, standard chest armor clearance, interpolation history across LOD changes, resetting the solver, and closed mesh topology, winding and atlas limits. The scripted turn produced a 0.1612-block difference in edge depth and a 0.0089-block difference in the sampled second difference of the two edges. An isolated high-detail solver/mesh test on the Gradle test JVM measured **0 bytes** allocated across 1,500 warmed-up iterations. This excludes the Minecraft renderer, event bus, and driver.

## In-game method

The separate QA fixture measures 0, 1, and 24 players with capes in the same flat-world scene sequence. The 24-cape scene places 8 actors near 13 blocks, 8 near 33 blocks, and 8 near 62 blocks. Each recorded round follows 240–300 warm-up ticks and captures 500 ticks (~25 seconds), at 854×480 with shaders off. The no-mod and BALANCED rounds use matched warm-up durations within each benchmark block. Main-thread frame intervals come from Forge render-end callbacks. Cape cost is the wall-clock time spent in client cloth ticks and cloak draw/submit calls, normalized by captured frame count; it includes distant vanilla fallback calls, not an isolated GPU timer. Allocations reported per frame are for the game client thread as a whole. The baseline physically removes the production jar; the fixture and scene setup stay the same. `BALANCED` and `FULL` use the same rendering/simulation code.

On Windows, after building, use the instrumented disposable Prism instance described in `README.md`. Example:

```powershell
.\tools\Invoke-TrailclothLab.ps1 -Quality balanced -Run balanced-a -Warmup 300 -Sample 500 -Passes 2
```

Run independent sessions in an interleaved order with `baseline`, `balanced`, and `full`. Use run labels beginning `base-`, `balanced-`, and `full-` respectively so the analyzer recognizes the runs. Execute `tools/Measure-TrailclothLab.ps1` on the CSV directory. Test Complementary Reimagined + Euphoria Patches separately with `-Shader -Scenes 1,6 -Screenshots`. The six-actor scene contains a GregTech donor cape, no-cape controls (hidden and invisible), and a scripted turn, crouch, underwater flag, and discontinuous displacement. Texture and UV are verified with an asymmetric procedural swatch and the real 512×256 GregTech donor texture loaded at runtime. No Mojang account's earned/purchased cape is required for the fixture.

## Performance results

November 21, 2026. Each cell is the median of four captured rounds (two independent launches per mode). All performance runs used the same 854×480 scene layout with shaders off. `FULL` is Trailcloth's high-detail mode, not another cape mod. The baseline has no Trailcloth timing hooks; `—` means not measured.

| Capes | Mode | Whole-client frame P95 (ms) | Cape CPU time (µs/frame) | Client-thread allocation (KiB/frame) |
| ---: | --- | ---: | ---: | ---: |
| 0 | Uninstalled | 2.608 | — | 27.4 |
| 0 | BALANCED | 2.625 | 0.0 | 26.2 |
| 0 | FULL | 2.615 | 0.0 | 26.8 |
| 1 | Uninstalled | 3.048 | — | 38.4 |
| 1 | BALANCED | 3.006 | 23.9 | 37.7 |
| 1 | FULL | 3.080 | 24.5 | 37.6 |
| 24 | Uninstalled | 4.362 | — | 103.6 |
| 24 | BALANCED | 5.158 | 227.5 | 103.2 |
| 24 | FULL | 6.144 | 463.2 | 117.0 |

- **Pass: 0-cape regression gate.** BALANCED P95 was **+0.66%** relative to the uninstalled baseline (limit **+3%**). No cloth steps or cloak submissions were recorded in the empty scenes.
- **Pass: mixed-distance CPU gate.** In the 24-cape scene, BALANCED reduced cape CPU time per frame by **50.89%** relative to FULL (required **≥20%**). The counters confirmed both cloth densities and distance fallback in BALANCED, and only high detail in FULL.
- **Not an overall FPS gain over an uninstalled client.** In the 24-cape fixture the uninstalled client still had the best whole-frame P95: 4.362 ms, versus 5.158 ms in BALANCED. The allocation column reports the entire client thread; it is not an estimate of bytes allocated by Trailcloth alone. Results are machine- and scene-specific and do not establish a shader-mode performance result. Time of day, weather, and ordinary world updates were not frozen between launches.

Local raw evidence: `build/measurements/{base-a,base-b,balanced-a,balanced-b,full-a,full-b}.csv` and `build/measurements/benchmark-summary.json`.

## In-game visual checks

The disposable client completed both the close-up (one caped actor wearing an iron chestplate) and the multi-actor compatibility scene with each graphics setting: Angelica shaders disabled, and **Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5** using Angelica's OpenGL backend. The Angelica log confirmed the Overworld shader pipeline loaded. Each mode showed the 64×32 asymmetric test atlas with its expected orientation and the installed GregTech 512×256 donor cape. The hidden and invisible actors submitted no cape geometry. The fixture also exercised a scripted sprint/turn, crouch, underwater-state branch and 12-block position discontinuities. The four caped actors in the six-actor scene recorded 960 cloth steps over 240 sampled ticks with either shader setting. The shader run recorded approximately two cloak mesh submissions per caped actor per frame, versus one with shaders off, consistent with the extra shadow pass rather than two visible capes.

The captures showed no visible duplicated cape, reversed test pattern, obvious chestplate clipping or detached shadow at the sampled poses. Earlier daylight smoke captures gave a clearer view of the ground shadows; the final captures occurred in rain. This is a fixture-based visual check, not a complete animation or armor survey. The test swatch exercised the normal cape interface without a Mojang account cape download, the water flag tested damping without a swim-through, and a scripted displacement tested the reset threshold rather than a live dimension change. Block collision, cloth self-collision, unusual armor models, dedicated/public servers, OptiFine, experimental graphics backends and other pack versions were not certified.

Local evidence: `build/measurements/visual-off-scene1.png`, `visual-off-scene6.png`, `visual-final-euphoria-scene1.png`, `visual-final-euphoria-scene6.png`, and the corresponding capture CSVs. The captures are not included in the release archives.

## Client-only compatibility

**Pass (local TCP).** A disposable GTNH 2.9.0 integrated host, without the Trailcloth production mod, accepted an installed client over `127.0.0.1`. Both instances used the same pack and QA fixture. The host observed the guest on its player list and the guest stayed in the remote world for 60 client ticks. The test used synthetic local identities, did not contact a public server, and does not certify a dedicated-server deployment. Both `PASS:` records are saved in `build/measurements/loopback.txt`.

## Release artifact

The tested, reobfuscated `trailcloth-0.1.0.jar` has SHA-256 `4cc397062e193b85b6334a34a590d56e138e36b64bc75e4e27e700281f01b7d1`. The archive contains only Trailcloth classes, its mod/mixin metadata, and the MIT license: no QA classes, game or pack classes, textures, or bundled Mixin. The separate source ZIP includes the build scripts, pinned Gradle Wrapper and notices, the original sources and tests, and the QA scripts. Raw captures, logs, worlds, local configuration, and dependencies are not distributed. A fresh extraction outside the repository rebuilt with `gradlew.bat --offline build qaJar` using the existing dependency caches and reproduced the tested mod binary byte for byte. Staging is local only; the release script does not publish to GitHub.
