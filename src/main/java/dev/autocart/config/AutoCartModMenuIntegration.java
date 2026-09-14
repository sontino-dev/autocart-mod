package dev.autocart.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.autocart.config.screen.AutoCartConfigScreen;

public class AutoCartModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AutoCartConfigScreen::new;
    }
}
