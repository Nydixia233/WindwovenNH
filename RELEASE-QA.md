# WindwovenNH release validation

## WindwovenNH 0.1.7 — public preview

Tested JAR SHA-256: `6ebd7527744451a9b1c844c49454195df6dea78c7209254e235a8231c52568b0`. In a controlled armored descent with wind strength 1.5 and a 35-degree torso tilt, the peak near-grid bend measured 26.8 degrees, down from 46.3 degrees in 0.1.6. Both grids passed the tests for lift and settling, anchored shoulders, warp stretch and standard helmet clearance. The standalone allocation check still measured zero bytes over 1,500 warmed tick-and-mesh iterations.

The Java 25 / Forge 1.7.10 / UniMixins 0.3.2 replay with EvenMoBends `ad96976-snapshot` confirmed animated cape draws. This is not a test of an entire custom pack; full GTNH, shaders, multiplayer and release performance were not revalidated on this JAR.

## WindwovenNH 0.1.6 — smoother collar contact (local preview)

Tested JAR SHA-256: `4259742e25b360009d57d3a327b5282f3d8a33008a7d12298be8020637a3c1ef`. In the armored, wind-strength 1.5 comparison, the near grid went from 145 repeated flat collar-link detections to zero; the largest measured bend between neighboring collar links fell from 22.9° to 10.2°. Both grid tiers passed the loft, helmet-triangle clearance, and strain checks. The farther grid still has a visibly coarser bend; this test does not establish an equivalent curvature improvement there. Warmed solver and mesh generation continued to allocate zero bytes in the standalone check.

An isolated Forge 1.7.10 replay with Java 25, UniMixins 0.3.2, and EvenMoBends `ad96976-snapshot` compared 0.1.5 and 0.1.6 at the same wind setting, including a standard helmet and chestplate. Front-of-neck pin position and the late compatibility patch remained intact; the side-view capture showed the cape curling upward without the same flat shoulder segment. No complete GTNH or shader-pack regression was run.

## WindwovenNH 0.1.5 — flight wake and wind strength (local preview)

Tested JAR SHA-256: `b37b51197056b375cea6df2deac6dbeb537c8141da63fd97217b3d77c2ccc50e`. The release JAR kept all 974,724 sampled non-flight coordinates bit-identical to 0.1.4 at default wind strength. Both mesh densities passed takeoff, descent, turn, stop, contact, and strain tests, including the maximum wind setting. The solver-and-mesh allocation check measured zero bytes across 1,500 warmed-up iterations, including flight transitions.

An isolated Forge 1.7.10 replay exercised walking, sprinting, hover, slow and fast flight, climb, descent, turning and stopping in a test world. On Java 25 with UniMixins 0.3.2 and EvenMoBends `ad96976-snapshot`, the slow-flight hem opened about 0.56 blocks behind the shoulder seam at default wind (effectively zero on 0.1.4). At `1.5`, the sprint hem averaged 0.75 blocks back, compared with 0.58 at `1.0`. These are scripted torso-local distances, not guarantees for all poses. The Java 25 / UniMixins 0.3.1 and Java 8 / UniMixins 0.3.2 clients also passed. The Java 8 check without EvenMoBends used the regular cape hook. No replay recorded a head or torso proxy violation.

A paired standalone input/solver/interpolation CPU test on Java 25 measured a median change of -0.6% to +4.2% across the two grids and tested motion profiles. Shorter timing runs were noisy; this is not a whole-client frame-time result. Full GTNH and shader packs were not retested.

## WindwovenNH 0.1.4 — EvenMoBends loading fix (unreleased)

Tested JAR SHA-256: `09f00636f1ce90a7e354a4fa6f2c4d911d78665214f7a61531e8bb1bbc331e39`. In an isolated Forge 1.7.10 instance, the published 0.1.3 JAR crashed during EvenMoBends startup on Java 8 and drew no cloth on Java 25. With 0.1.4, both clients started and drew animated cloth over the same synthetic cape. Every mod JAR was installed normally in `mods`, without classpath changes.

- With EvenMoBends `ad96976-snapshot` and Salamander `0.0.2`, the late patch applied and drew cloth on Java 8u482 / UniMixins `0.3.2`, and on Java 25.0.2 with UniMixins `0.3.2` and `0.3.1`.
- On Java 8, the default configurations of FalseTweaks `4.4.5`, FalsePatternLib `1.12.2`, Beddium `1.2.5-j8`, NotFine `0.4.0`, and SwanSong `1.3.7` worked together with shaders off. Without EvenMoBends, the regular cape hook still drew cloth. With `enabled=false`, the cape remained visible and all cloth counts stayed at zero. The empty scene recorded zero cloth steps and draws in all six runs.
- The standalone cloth/config tests passed. Both detail levels matched 0.1.3 bit-for-bit over 974,724 sampled coordinates. The solver, render hooks and adapter classes in the two JARs are byte-for-byte identical; only when the optional adapter loads changed.

This checks the reported loading regression, not the full reporter pack, the complete GTNH instance, shaders, or performance.

## WindwovenNH 0.1.3 — posed torso preview

Tested JAR SHA-256: `e2bfbbb88a35bf8392df7d724f41564942da5289c1bea0d9dd8420d161aa4ba7`. The solver projects gravity, air and acceleration into the animated torso's frame; the shoulder seam and collision proxy remain attached to the body. The normal cape path gives 974,724 bit-identical solver coordinates against 0.1.1. The offline tests also cover a 70-degree tilted drape and rapid pitch/yaw/roll transitions in both grids, including pinned shoulders, chest clearance and finite interpolated links.

In the full GTNH 2.9.0 / Java 25 / Angelica 2.2.43 preview copy, with EvenMoBends `0.0.3-gtnh-preview.1` and shaders off, a real local player reached a 70-degree forward flight pose. The compatibility hook recorded 862 local cape draws over 45 degrees of torso tilt. Equipping and removing Galacticraft oxygen gear still changed the renderer as expected. This check does not requalify shaders, multiplayer, or overall frame-time performance for 0.1.3.

### Earlier adapter checks (development build 0.1.2)

Earlier development JAR: `windwovennh-0.1.2.jar`, SHA-256 `5c1b74224f1284431dfc061f6609424039ccfc768d8da4683c6598a6108c7a53`. Offline build, artifact checks, and cloth/config regressions passed. Against the public 0.1.1 JAR, all 974,724 solver coordinates matched bit-for-bit across both grids, including half-tick samples. The compatibility changes only affect the cape draw path.

The game tests used a disposable GTNH 2.9.0 / Java 25 / Angelica 2.2.43 instance. With the default GTNH renderer registration, Galacticraft handled the fixture's player class and WindwovenNH still drew through its original hook. To actually exercise EvenMoBends, the separate QA fixture routed only the synthetic actors to the installed EvenMoBends renderer. The production mod does not change renderer selection.

- EvenMoBends 0.0.3 with Salamander `727fc6c-snapshot`, shaders off: 2,276 and 7,420 cloth draws through the EvenMoBends adapter in the one- and six-actor captures, respectively. The empty scene drew no cloth. The captures showed the asymmetric test cape, GregTech cape and a chestplate; hidden or invisible actors did not acquire a visible cape.

- EvenMoBends `ad96976-snapshot` with Salamander `5dc2a21-snapshot`, Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5: 2,290 and 6,160 cloth draws through the adapter in the same two scenes. The empty scene drew no cloth. In the reviewed captures, both cape textures were visible and there was no obvious duplicate cape, atlas reversal, or displaced shadow.

- With WindwovenNH disabled, EvenMoBends' regular cape was still visible and the cloth counters stayed at zero. With EvenMoBends and Salamander removed, WindwovenNH used its original draw hook in the one- and six-actor scenes, with no EvenMoBends draws and no cloth draws in the empty scene.

These earlier checks do not qualify the 0.1.3 JAR.

## WindwovenNH 0.1.1 — display name

The production JAR and the GTNH client both report the display name **WindwovenNH**. The tested SHA-256 is `ade2ca30e581237b6435c5e9d7fabe8dc28ddbd959310445acb8a403f8e3e2cd`. In the disposable GTNH 2.9.0 / Java 25 / Angelica 2.2.43 instance, the cape hook drew normally (140 cloth ticks), and the existing configuration was unchanged.

The standalone regression and config checks passed. The 0.1.0 and 0.1.1 JARs returned the same 974,724 sampled coordinates, including both distance grids and half-tick interpolation. This patch adds no changes to physics, rendering or configuration. The full performance and network suite below documents 0.1.0; it was not repeated for this name-only patch.

## WindwovenNH 0.1.0 — first release

### Tested JAR

The tested artifact is `windwovennh-0.1.0.jar` (`windwovennh`). SHA-256: `beba5803e6f479bea52a763da2bcae6ce1876898be84c5c0cba54e03eeafd99a`.

### Solver and configuration

- The offline build passed `clothTest` and `configTest`. The production solver matched the pre-release reference build at **974,724 coordinates bit-for-bit**, across 14 motion cases, both grids, and tick plus half-tick sampling. The local solver/mesh allocation test was still **0 bytes over 1,500 warmed-up iterations**.
- Forge loaded the mod and the cape and Tessellator mixins from `mixins.windwovennh.json`. An existing test world opened normally. Configuration tests verified that existing settings take precedence and that importing pre-release settings leaves the original file unchanged.
- With a pre-release JAR also installed, the release JAR left cape rendering to that JAR. A failed config import logged a warning, fell back to the original cape, and did not create a new config. Unit checks also covered preserving `enabled=false` and `quality=FULL` and leaving the old settings available for rollback.

### In-game replay

GTNH 2.9.0 with Java 25, Forge 10.13.4.1614, UniMixins 0.3.1 and Angelica 2.2.43 OpenGL. With shaders off and with Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5, the reviewed runs drew the fixture's standard-layout cape and the installed GregTech cape, skipped the hidden/invisible actors, and replayed the stationary body turn and shoulder-height lift. No obvious double draw or reversed atlas was visible in the reviewed views. This is not a comprehensive check of arbitrary custom armor, shaders or renderers.

### Multiplayer

The local GTNH loopback passed: a GTNH host without the mod accepted a client with WindwovenNH installed. The client spent 60 ticks in the remote world. This does not certify every third-party server setup.

### Measured performance

At 854×480 with shaders off, two independent launches per condition and three 14-second samples per condition/scene were collected in an interleaved order. The baseline ran without any cape mod installed. P95 is whole-client frame time, and the cape time is tick plus draw/fallback work divided by captured frames. The 24-cape fixture used eight near, eight mid-distance and eight far actors.

| Mode | 0-cape P95 (ms) | 1-cape P95 (ms) | 24-cape P95 (ms) | 24-cape CPU (µs/frame) |
| --- | ---: | ---: | ---: | ---: |
| Uninstalled | 4.571 | 4.513 | 6.138 | — |
| WindwovenNH BALANCED | 3.541 | 4.565 | 6.945 | 301.25 |
| WindwovenNH FULL | 3.669 | 4.653 | 8.032 | 613.05 |

Both release gates passed on this setup: no-cape P95 changed by **−22.54%** against the uninstalled baseline (limit: +3%), and the mixed-distance cape CPU time of `BALANCED` was **50.86% lower** than that of `FULL` (target: at least 20%). The no-cape runs had zero cape steps and draws and their P95 varied between launches. These numbers do not promise an FPS improvement; the measured 24-cape whole-client P95 was higher than the uninstalled baseline. Thread-wide allocations are not the same as the standalone solver allocation check.

### Test setup

All game testing used a disposable Prism instance. The release does not include the test fixture JAR, game or shader assets, local settings, captures, or raw logs.
