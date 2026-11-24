# WindwovenNH

[简体中文](README.zh-CN.md)

WindwovenNH makes the cape you already have move like cloth. When you turn, the hem follows a little late. In a fast fall it can catch the air and lift, then settle when you stop. Looking around without turning your body won't make it flap.

It doesn't give you a cape or change its texture. If Minecraft or GregTech wouldn't show your cape, WindwovenNH won't show it either.

## Install

Download [windwovennh-0.1.1.jar](https://github.com/Nydixia233/WindwovenNH/releases/download/0.1.1/windwovennh-0.1.1.jar) and put it in your client's `mods` folder. The server doesn't need it.

The stable build was tested on **GT New Horizons 2.9.0** (Minecraft Forge 1.7.10) with Java 25 and the pack's UniMixins and Angelica 2.2.43 in OpenGL mode. The test included shaders off and Complementary Reimagined with Euphoria Patches. Other packs, OptiFine and other rendering backends haven't been tested.

**Using EvenMoBends?** Try the [0.1.7 preview](https://github.com/Nydixia233/WindwovenNH/releases/tag/0.1.7). It fixes the loading order that could leave the cape motionless in 0.1.3. Replace the old WindwovenNH JAR; don’t keep both. EvenMoBends is optional. In GTNH, Galacticraft may still draw the player instead; WindwovenNH does not switch the player renderer or enable body animations. See the [test report](RELEASE-QA.md) for what was tested.

## Settings

The defaults are fine for most players. To change them, edit `config/windwovennh.cfg` and restart the game:

- **`BALANCED`**: more detail within 24 blocks, less detail from there to 48, and the original cape farther away.
- **`FULL`**: full detail at every distance where the game draws a cape.
- **`enabled=false`**: keep the game's original cape movement.
- **`windStrength=1.0` (0.0–3.0, 0.1.7 preview)**: default air response. Try `1.3`–`1.8` if flying or sprinting looks too close to the body; `0.0` removes external air pressure while keeping gravity and cloth inertia.

The cape can bend around a regular helmet and chestplate, but unusual armor can still clip. The mod doesn't promise an FPS boost.

## Build

Use JDK 25. The first build needs internet access for dependencies.

```sh
./gradlew build
```

On Windows, run `.\gradlew.bat build` instead. Install the plain `windwovennh-<version>.jar` from `build/libs/`, not the `-dev` or `-sources` JAR.

WindwovenNH is an unofficial mod, not part of GT New Horizons. [MIT license](LICENSE).
