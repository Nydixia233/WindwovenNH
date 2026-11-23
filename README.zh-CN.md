# WindwovenNH

[English](README.md)

WindwovenNH 让你已有的披风像布一样动。身体转向时，布尾会慢半拍；快速下落时，下摆可能被气流掀起来，停下后再落回去。只转镜头、不转身，披风不会跟着镜头乱甩。

它不发披风，也不换贴图。原版或 GregTech 本来不显示的披风，装了 WindwovenNH 也不会显示。

## 安装

下载 [windwovennh-0.1.1.jar](https://github.com/Nydixia233/WindwovenNH/releases/download/0.1.1/windwovennh-0.1.1.jar)，放进客户端的 `mods` 文件夹。服务器不用装。

实测环境是 **GTNH 2.9.0**（Minecraft Forge 1.7.10）、Java 25，以及整合包自带的 UniMixins 和 Angelica 2.2.43（OpenGL 模式）。关光影和开 Complementary Reimagined + Euphoria Patches 都测过。其他整合包、OptiFine 和其他渲染后端没测过。

**想试 EvenMoBends 适配？** 可以用 [0.1.3 预发布版](https://github.com/Nydixia233/WindwovenNH/releases/tag/0.1.3)。EvenMoBends 负责画人物时，披风挂在肩上，跳跃前倾或飞行时不会整片跟着身体硬转。不装 EvenMoBends 也能用。GTNH 里的 Galacticraft 仍可能接管人物绘制；WindwovenNH 不会替换人物渲染器，也不会启用身体动画。实测范围见[测试报告](RELEASE-QA.md)。

## 设置

大多数时候用默认设置就行。想改的话，编辑 `config/windwovennh.cfg`，然后重启游戏：

- **`BALANCED`（默认）**：24 格内多算一些细节，24 到 48 格降低细节，再远就用原版披风。
- **`FULL`**：只要游戏还在画披风，就用最高细节。
- **`enabled=false`**：改回游戏原本的披风动画。

披风能绕开常见的头盔和胸甲，异形盔甲仍可能穿模。这不是一个提帧模组。

## 自己构建

需要 JDK 25。第一次构建要联网下载依赖。

```powershell
.\gradlew.bat build
```

非 Windows 系统可运行 `./gradlew build`。构建后安装 `build/libs/` 中的 `windwovennh-<版本号>.jar`，不要装带 `-dev` 或 `-sources` 后缀的文件。

WindwovenNH 是为 GTNH 做的非官方模组。[MIT 许可证](LICENSE)。
