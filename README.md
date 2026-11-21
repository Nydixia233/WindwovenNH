# 风织 · WindwovenNH

An **unofficial client-side cape physics mod** for Minecraft Forge 1.7.10 and GT New Horizons. WindwovenNH is the new name for Trailcloth, an original implementation. It is not a WaveyCapes port and is not affiliated with GTNH or Mojang. It animates the cape the game or GregTech already selected; it does not grant or download capes.

## Install or upgrade

1. Use GTNH 2.9.0 with Forge 10.13.4.1614, the pack's UniMixins 0.3.1 and Angelica 2.2.43 OpenGL. The test environment uses Java 25.
2. Remove any `trailcloth-*.jar` from the client and put **`windwovennh-0.1.0.jar`** in `mods`. Do not add dev, source or QA fixture JARs. No server installation is needed.
3. Launch the client. The mod list should display **风织 · WindwovenNH** (`windwovennh`). If the two mods are installed together, WindwovenNH leaves the old renderer in control and warns in the client log.

On first use, the mod uses `config/windwovennh.cfg`. If that file does not exist, it copies `config/trailcloth.cfg` when present, without changing the old file. An existing new file always takes precedence. If the copy fails, the client uses the original cape for that launch and logs the failure. To roll back, remove the new JAR and put back the old JAR; its config is still there. No mod blocks, items or saved-world content need migration.

```ini
cloth {
    B:enabled=true
    S:quality=BALANCED
}
```

Changes take effect on restart. `enabled=false` uses the original draw.

| Quality | Within 24 blocks | Over 24 through 48 | Beyond 48 |
| --- | --- | --- | --- |
| `BALANCED` | 5×13 cloth grid | 3×7 cloth grid | Original cape |
| `FULL` | 5×13 | 5×13 | 5×13 where the game draws a cape |

The shoulders stay pinned; the hem can trail an in-place body turn and lift above the shoulder on a fast descent. A camera-only or head-only turn is not a body turn. The cloth uses simplified head/chest contact, player-relative airflow and underwater damping. There is no self-collision, block collision or exact collision for every custom armor. Other Minecraft/GTNH versions, OptiFine and non-OpenGL Angelica backends are outside this scope.

## Build and test

Use JDK 25 and the included Gradle wrapper. The first build needs access to the declared build repositories. Windows commands:

```powershell
.\gradlew.bat build qaJar sourceBundle
.\gradlew.bat renameParity "-PparityBaselineJar=path/to/trailcloth-0.2.2.jar"
```

Install only the reobfuscated `windwovennh-0.1.0.jar` from `build/libs`. The build runs standalone physics, mesh, allocation and legacy-config checks. `renameParity` compares the two built JARs with exact tick and interpolated coordinates. The offline reference/fitter and development fixture are not in the production JAR. `referenceLab` accepts an explicit `-PbaselineJar`; it understands both old and new solver packages.

Use PowerShell 7 for the fixture tools. Create disposable Prism copies with the existing IDs `GTNH-290-Trailcloth-client-test` and `GTNH-290-Trailcloth-host-test`, with automatic world/server entry disabled. These are harness path identifiers, not the public mod name. Pass `-Prism` or set `WINDWOVENNH_PRISM` to your own launcher path. Do not use your normal instance or world:

```powershell
.\tools\Invoke-WindwovenNHLab.ps1 -Mode showcase -Run visual-off -Scenes '1,6' -Screenshots
.\tools\Invoke-WindwovenNHLab.ps1 -Mode showcase -Run visual-euphoria -Scenes '1,6' -Screenshots -Shader
.\tools\Test-WindwovenNHLoopback.ps1
```

`-LegacyJar` selects a specific prior production binary for an A/B replay. `-PreserveConfig` stops the fixture launcher from writing a default config before a migration test. The fixture is development-only and activates only with its explicit disposable-instance marker.

To collect performance evidence, use the capture mode without shaders or screenshots, with `-BenchmarkOrder` numbering each independent interleaved launch. Use `-Quality baseline`, `balanced` and `full`, run labels such as `base-a`, `balanced-a`, `full-a`, and the fixed `0,1,24` scenes. The recorder preserves the candidate, fixture and CSV hashes. Run `Measure-WindwovenNHLab.ps1` on that version's evidence directory after at least two launches and three recorded rounds per condition.

## Release and credits

[Current QA](RELEASE-QA.md) describes observed checks and their limitations. [Release notes](RELEASE-NOTES.md) preserve the earlier version history. Reports titled Trailcloth are historical, not acceptance evidence for this build. Stage the current release with `Stage-WindwovenNHRelease.ps1` only after the current version's name- and hash-bound compatibility, visual, loopback and performance evidence is available. The script does not upload anything.

MIT-licensed; the original copyright notice is retained. See [provenance](PROVENANCE.md) and the accompanying notices for the separate Gradle wrapper and GTNH example setup. No game files, shaders, third-party cape code or cape assets are distributed.
