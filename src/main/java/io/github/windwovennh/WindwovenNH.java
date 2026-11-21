package io.github.windwovennh;

import java.io.IOException;
import java.nio.file.Path;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import io.github.windwovennh.client.PlayerCloths;
import net.minecraftforge.common.config.Configuration;

@Mod(modid = WindwovenNH.ID, name = "\u98ce\u7ec7 \u00b7 WindwovenNH", version = "0.1.0",
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class WindwovenNH {
    public static final String ID = "windwovennh";
    private static final String LEGACY_ID = "trailcloth";

    @Mod.EventHandler
    public void load(FMLPreInitializationEvent event) {
        if (!event.getSide().isClient()) return;
        if (Loader.isModLoaded(LEGACY_ID)) {
            event.getModLog().warn("Legacy Trailcloth detected. WindwovenNH cape override is disabled; remove the old Trailcloth JAR to upgrade.");
            return;
        }
        Path target = event.getSuggestedConfigurationFile().toPath();
        try {
            if (LegacyConfig.prepare(target, LEGACY_ID + ".cfg")) {
                event.getModLog().info("Imported trailcloth.cfg into windwovennh.cfg; the old file is unchanged.");
            }
        } catch (IOException | SecurityException e) {
            event.getModLog().warn("Could not prepare windwovennh.cfg; keeping the original cape this launch and retrying next time.", e);
            return;
        }
        Configuration settings = new Configuration(target.toFile());
        settings.load();
        boolean enabled = settings.getBoolean("enabled", "cloth", true,
            "Enable animated cloth. On false, the original cape is drawn. Restart to apply.");
        String quality = settings.getString("quality", "cloth", "BALANCED",
            "BALANCED: 5x13 within 24 blocks, 3x7 within 48, original cape farther away. FULL: 5x13 at every distance. Restart to apply.",
            new String[] { "BALANCED", "FULL" });
        if (settings.hasChanged()) settings.save();
        if (enabled) PlayerCloths.enable("FULL".equals(quality));
        event.getModLog().info("WindwovenNH: enabled={}, quality={}", enabled, quality);
    }
}
