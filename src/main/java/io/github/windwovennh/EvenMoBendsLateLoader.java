package io.github.windwovennh;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public final class EvenMoBendsLateLoader implements ILateMixinLoader {
    @Override
    public String getMixinConfig() {
        return "mixins.windwovennh.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        // A regular mod's renderer is not available when the vanilla mixins are prepared.
        if (FMLLaunchHandler.side().isClient() && loadedMods.contains("mobends")) {
            return Collections.singletonList("EvenMoBendsCapeCall");
        }
        return Collections.emptyList();
    }
}
