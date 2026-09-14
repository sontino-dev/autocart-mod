package dev.autocart;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class AutoCartKeybinds {

    public static KeyBinding TOGGLE;
    public static KeyBinding CROSSBOW_USE;

    private AutoCartKeybinds() {}

    public static void register() {
        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocart.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.autocart"
        ));
        CROSSBOW_USE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocart.crossbow",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "key.categories.autocart"
        ));
    }
}
