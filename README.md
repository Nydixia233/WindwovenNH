# 风织 · WindwovenNH

[简体中文](README.zh-CN.md)

WindwovenNH makes the cape you already have move like cloth. When you turn, the hem follows a little late. In a fast fall it can catch the air and lift, then settle when you stop. Looking around without turning your body won't make it flap.

It doesn't give you a cape or change its texture. If Minecraft or GregTech wouldn't show your cape, WindwovenNH won't show it either.

## Install

Put **`windwovennh-0.1.0.jar`** in your client's `mods` folder. The server doesn't need it. If you don't have the JAR, you can [build it below](#build).

Tested on **GT New Horizons 2.9.0** (Minecraft Forge 1.7.10) with Java 25 and the pack's UniMixins and Angelica 2.2.43 in OpenGL mode. The test included shaders off and Complementary Reimagined with Euphoria Patches. Other packs, OptiFine and other rendering backends haven't been tested.

## Settings

The defaults are fine for most players. To change them, edit `config/windwovennh.cfg` and restart the game:

- **`BALANCED`**: more detail within 24 blocks, less detail from there to 48, and the original cape farther away.
- **`FULL`**: full detail at every distance where the game draws a cape.
- **`enabled=false`**: keep the game's original cape movement.

The cape can bend around a regular helmet and chestplate, but unusual armor can still clip. The mod doesn't promise an FPS boost.

## Build

Use JDK 25. The first build needs internet access for dependencies.

```sh
./gradlew build
```

On Windows, run `.\gradlew.bat build` instead. The JAR to install is `build/libs/windwovennh-0.1.0.jar`, not the `-dev` or `-sources` JAR.

WindwovenNH is an unofficial mod, not part of GT New Horizons. [MIT license](LICENSE).
