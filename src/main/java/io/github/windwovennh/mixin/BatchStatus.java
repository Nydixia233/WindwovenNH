package io.github.windwovennh.mixin;

import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Tessellator.class)
public interface BatchStatus {
    @Accessor("isDrawing")
    boolean windwovennh$busy();
}
