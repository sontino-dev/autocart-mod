package dev.autocart.mixin;

import dev.autocart.AutoCartMod;
import dev.autocart.events.impl.EventKeyboardInput;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void onTick(CallbackInfo ci) {
        if (!AutoCartMod.nullCheck()) {
            AutoCartMod.EVENT_BUS.post(new EventKeyboardInput((Input)(Object)this));
        }
    }
}
