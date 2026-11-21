# WindwovenNH 0.1.1

- The display name is now simply **WindwovenNH**. No changes to cape movement, settings, or the mod ID.

## WindwovenNH 0.1.0

First public version of WindwovenNH.

- Forge ID: `windwovennh`. Replace the old JAR rather than installing both.
- Copies `trailcloth.cfg` to `windwovennh.cfg` on first use if the latter is absent. Existing new settings win; the old file is kept for rollback.
- Keeps the shoulder lift, stationary torso twist, distance tiers and legacy cape texture/visibility behavior. Client only, with no cape service, telemetry or server protocol changes.

See [current QA](RELEASE-QA.md) for the status of this build.

## Trailcloth 0.2.2 — standing torso turns

- The cape now twists when the body turns in place. The free end trails briefly, and eases back after stopping instead of rotating rigidly with the carrier.
- Head-only turns and the observer camera are not inputs. At running/flying speeds, the existing turn behavior continues; shoulder lift, collision, texture handling and LOD stay in place.
- No new configuration or per-frame solve. Replace the previous client JAR. See [targeted QA](TURN-QA.md).

## Trailcloth 0.2.1 — free hem above the shoulders

- Replaces the global shoulder ceiling with local head contact and keeps the existing rear torso/chestplate constraint. Only the shoulder seam is pinned.
- A billowed panel can catch stronger vertical airflow away from the body wake. In a sufficiently fast fall the hem can loft behind the head, then settle after braking. The effect is solved, not an animation triggered by fall speed.
- Ordinary standing jumps do not invert the cape, and the exposed-air correction is not used underwater. Both detail tiers stay lightweight with the same substeps, projection passes and render hook.
- Replace the old client JAR. No config changes. Local preview only; see [0.2.1 QA](SHOULDER-QA.md).

## Trailcloth 0.2.0 — reference-calibrated cloth

- Keeps the 5×13 / 3×7 cloth grids and uses separate fitted coefficients for their air response and damping.
- The new stateful pressure model responds to local cloth orientation, its own velocity and the player's motion. No recorded pose lookup or offline tooling is shipped in the runtime JAR.
- Retains the existing cape texture, visibility, config and distance tiers. Replace the previous client JAR rather than installing both.
- Local delivery. See [0.2.0 QA](REFERENCE-QA.md) for held-out results, performance and limitations.

## Trailcloth 0.1.4 — takeoff and descent

- Loose cloth lags downward at takeoff, and vertical climbing no longer lifts the cape like a dive.
- Descending airflow raises the middle and hem earlier without curling the tip into a tight hook. The cape settles after landing.
- No new config or rendering changes. This is a local motion preview; see [vertical-motion QA](VERTICAL-QA.md).

## Trailcloth 0.1.3 — trailing turns and smaller folds

- The free end follows lateral reversals later than the upper cloth. During a moving turn it retains part of its previous world orientation rather than receiving a uniform turn push.
- Ascent keeps its flowing silhouette; descent lifts the hem without repeatedly curling as far upward. Gusts make small uneven folds in the lower cloth without animating the whole sheet at rest.
- Same client configuration, draw hook, and LODs. Local preview only. See [motion QA](MOTION-QA.md) for the old/new results and the isolated GTNH replay.

## Trailcloth 0.1.2 — high-speed flutter preview

- Adds travelling folds and a staggered roll at the cape hem, even at a constant high speed. Speed controls the amount of flutter; it fades after stopping and is reduced underwater.
- Force-based motion shares the existing solver, attachment, water damping, distance tiers and render interpolation. No new solver passes, texture assets or config keys.
- Replace the previous client JAR. See [flutter QA](FLUTTER-QA.md) for the scoped checks for this local preview.

## Trailcloth 0.1.1 — vertical flight preview

- During fast ascent the hem trails out without the whole cape going taut. In a dive the lower rows curl first, with a gradual return after braking.
- The change applies to both cloth grids. It retains torso clearance, strain limiting, and water damping without adding solver iterations.
- Replace the old client JAR rather than installing both. The flight preview has targeted QA; the full GTNH release metrics below are for 0.1.0.

## Trailcloth 0.1.0

First release candidate for Forge 1.7.10 and GT New Horizons 2.9.0.

- Two-dimensional cloth with independent edge motion, underwater damping and standard chest plate clearance.
- Automatic reduction of the cloth grid with distance and original-cape fallback beyond 48 blocks in the default mode.
- Client only. Uses cape textures and visibility chosen by the game; no cape service, telemetry, or server component.
- Use the GTNH-provided UniMixins 0.3.1. Only install the reobfuscated `trailcloth-0.1.0.jar`.

Local QA on GTNH 2.9.0 passed with Angelica shaders off and with Complementary Reimagined + Euphoria Patches. No-cape frame P95 changed by +0.66% versus an uninstalled client; the default mode used 50.89% less cape CPU time per frame than `FULL` in the 24-cape mixed-distance scene. An installed client also joined an GTNH host without Trailcloth in the local TCP fixture. These results do not imply higher FPS than leaving the mod uninstalled.

Trailcloth source and the mod binary are MIT-licensed. The source archive includes the Gradle wrapper and its separate notices; see [provenance](PROVENANCE.md) and the [QA report](QA-REPORT.md). This is an unofficial, independently implemented mod. Public upload is manual; opt-in platform listings or monetization are not included.
