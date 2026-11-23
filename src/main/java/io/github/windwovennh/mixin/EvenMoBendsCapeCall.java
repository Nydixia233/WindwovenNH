package io.github.windwovennh.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.windwovennh.client.PlayerCloths;
import io.github.windwovennh.cloth.CapeFrame;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.gobbob.mobends.client.renderer.entity.RenderBendsPlayer", remap = false)
abstract class EvenMoBendsCapeCall extends RenderPlayer {
    @Unique private final CapeFrame windwovennh$root = new CapeFrame();
    @Unique private boolean windwovennh$rootValid;

    @Inject(method = {
        "rotateCorpse(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V",
        "func_77043_a(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V"
    }, at = @At("HEAD"), remap = false, require = 0)
    private void windwovennh$resetRoot(AbstractClientPlayer wearer, float age, float yaw, float part, CallbackInfo call) {
        windwovennh$root.identity();
        windwovennh$rootValid = true;
    }

    @Inject(method = {
        "rotateCorpse(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V",
        "func_77043_a(Lnet/minecraft/client/entity/AbstractClientPlayer;FFF)V"
    }, at = @At(value = "INVOKE", target = "Lnet/gobbob/mobends/client/model/entity/ModelBendsPlayer;postRender(F)V"),
        remap = false, require = 0)
    private void windwovennh$captureRoot(AbstractClientPlayer wearer, float age, float yaw, float part, CallbackInfo call) {
        // The model updates its rotation again later; use the rotation actually sent to this draw.
        windwovennh$rootValid = PlayerCloths.captureBendsRoot(wearer, modelBipedMain, windwovennh$root);
    }

    // The optional renderer is not on the compile classpath. Match both MCP and Forge SRG names.
    @ModifyExpressionValue(method = {
        "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V",
        "func_77029_c(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V"
    }, at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getHideCape()Z"),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;func_82238_cc()Z")
    }, remap = false, require = 0, allow = 1)
    private boolean windwovennh$drawAfterVisibilityCheck(boolean hidden, AbstractClientPlayer wearer, float partialTick) {
        // The cape/event/invisibility checks have passed. Suppress only this cape branch, only after drawing.
        if (hidden) return true;
        if (!windwovennh$rootValid || wearer.isPlayerSleeping() || wearer.isRiding()) return false;
        long marker = PlayerCloths.startDraw();
        try {
            return PlayerCloths.replaceBends(wearer, partialTick, modelBipedMain, windwovennh$root);
        } finally {
            PlayerCloths.finishDraw(marker);
        }
    }
}
