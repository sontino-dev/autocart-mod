package dev.autocart;

import dev.autocart.config.AutoCartConfig;
import dev.autocart.config.AutoCartConfig.Mode;
import dev.autocart.core.AutoCartEventBus;
import dev.autocart.feature.AutoCartModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoCartMod implements ClientModInitializer {

    public static final String MOD_ID = "autocart";
    public static final Logger LOGGER = LoggerFactory.getLogger("AutoCart");

    public static MinecraftClient mc;
    public static AutoCartModule  module;
    public static AutoCartConfig  config;
    public static AutoCartEventBus EVENT_BUS;

    @Override
    public void onInitializeClient() {
        mc        = MinecraftClient.getInstance();
        EVENT_BUS = new AutoCartEventBus();
        config    = AutoCartConfig.load();
        AutoCartKeybinds.register();

        module = new AutoCartModule();
        EVENT_BUS.subscribe(module);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            module.onTick();

            if (AutoCartKeybinds.TOGGLE.wasPressed()) {
                if (module.isEnabled()) module.disable();
                else                    module.enable();
                LOGGER.info("[AutoCart] {} — mode: {}", module.isEnabled() ? "ON" : "OFF", config.mode);
            }

            if (module.isEnabled() && config.getMode() == Mode.CrossBow) {
                if (AutoCartKeybinds.CROSSBOW_USE.wasPressed()) {
                    module.triggerCrossBow();
                }
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> AutoCartHud.render(ctx));

        LOGGER.info("[AutoCart] Initialized — MC 1.21.4 🛒💥");
    }

    public static boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }
}
