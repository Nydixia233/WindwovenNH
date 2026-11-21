# WindwovenNH release validation

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
