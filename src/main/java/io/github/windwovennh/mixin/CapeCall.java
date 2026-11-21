package io.github.windwovennh.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import io.github.windwovennh.client.PlayerCloths;

@Mixin(RenderPlayer.class)
abstract class CapeCall {
    @WrapOperation(method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBiped;renderCloak(F)V"), require = 0)
    private void windwovennh$renderCloth(ModelBiped biped, float unit, Operation<Void> vanilla,
        AbstractClientPlayer wearer, float partialTick) {
        long marker = PlayerCloths.startDraw();
        if (!PlayerCloths.replace(wearer, partialTick, unit)) vanilla.call(biped, unit);
        PlayerCloths.finishDraw(marker);
    }
}
