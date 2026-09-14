package dev.autocart.mixin;

import dev.autocart.AutoCartMod;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void onStopUsingItem(PlayerEntity player, CallbackInfo ci) {
        if (AutoCartMod.module == null || !AutoCartMod.module.isEnabled()) return;
        if (player != AutoCartMod.mc.player) return;
        if (!player.isUsingItem()) return;
        if (player.getActiveItem().getItem() != Items.BOW) return;
        AutoCartMod.module.onBowRelease();
    }
}
