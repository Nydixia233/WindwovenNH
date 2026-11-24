# WindwovenNH release notes

## 0.1.7 (pre-release)

- Fixed EvenMoBends cape support loading too early, which could leave the cape motionless or prevent startup. EvenMoBends is still optional. No renderer or animation mods are bundled.
- Slow flight now leaves a small gap between the cape and the back, while faster movement adds more lift. Set `windStrength` in `config/windwovennh.cfg` to tune the wind response.
- Smoothed the fold near the shoulders when the cape curls around a helmet in a fast fall. The shoulder seam stays fixed and the hem can still rise above it.

See the [test report](RELEASE-QA.md) for what this preview has and hasn't been tested with.

## 0.1.3 (pre-release)

Added optional EvenMoBends cape support ([#1](https://github.com/Nydixia233/WindwovenNH/issues/1)). The cape stays attached to the shoulders when the player leans into jumps or flies, but gravity and wind don't tilt with the body. Existing cape textures, visibility, and settings are unchanged. If EvenMoBends isn't installed, the usual WindwovenNH cape path still works.

WindwovenNH does not replace the player renderer or enable EvenMoBends body animations. See the [test report](RELEASE-QA.md) for what's been verified in this preview.

## 0.1.1

The in-game display name is now **WindwovenNH**. Cape movement, settings, and the mod ID are unchanged.

## 0.1.0

First public release of WindwovenNH, an unofficial client-side mod for GT New Horizons.

- Your existing cape moves like cloth. It trails when you turn in place, and the hem can lift above the shoulders in a fast fall.
- The default `BALANCED` mode uses full detail within 24 blocks, reduced detail up to 48 blocks, and the original cape beyond that. `FULL` always uses full detail when the game draws a cape.
- The mod keeps the cape texture and visibility rules chosen by Minecraft or GregTech. It doesn't give you a cape.

Install the release JAR in your client's `mods` folder. The server doesn't need it. See the [README](README.md) for setup and [release testing](RELEASE-QA.md) for results.
