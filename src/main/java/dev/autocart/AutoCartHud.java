package dev.autocart;

import dev.autocart.config.AutoCartConfig;
import dev.autocart.feature.AutoCartModule;
import dev.autocart.util.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class AutoCartHud {

    private static final int SLOT_SIZE   = 20;
    private static final int HOTBAR_Y    = 22;
    private static final int HOTBAR_LEFT = 91;
    private static final int RED_FILL    = 0x64FF0000;

    private AutoCartHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        AutoCartModule module = AutoCartMod.module;
        AutoCartConfig  cfg   = AutoCartMod.config;
        if (module == null || cfg == null) return;

        boolean enabled = module.isEnabled();
        String  mode    = cfg.mode;
        int     color   = enabled ? 0x55FF55 : 0xFF5555;
        String  mark    = enabled ? "▶" : "■";

        String keybindText = "[" + AutoCartKeybinds.TOGGLE.getBoundKeyLocalizedText().getString() + "] Toggle";
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("§fAutoCart §7[" + mode + "] " + mark), 2, 2, color);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("§8" + keybindText), 2, 12, 0xAAAAAA);

        if (enabled && cfg.getMode() == AutoCartConfig.Mode.CrossBow) {
            String cbKey = "[" + AutoCartKeybinds.CROSSBOW_USE.getBoundKeyLocalizedText().getString() + "] Use";
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("§e" + cbKey), 2, 22, 0xFFFF55);

            if (hasCrossbowDebugRequirements(mc)) {
                renderCrossbowSlotHighlights(ctx, mc);
            }
        }
    }

    private static boolean hasCrossbowDebugRequirements(MinecraftClient mc) {
        if (!InventoryUtil.findItemInHotbar(Items.TNT_MINECART).found()) return false;
        if (!InventoryUtil.findItemInHotbar(Items.RAIL, Items.ACTIVATOR_RAIL,
                Items.DETECTOR_RAIL, Items.POWERED_RAIL).found()) return false;
        if (!hasArrowAmmo(mc)) return false;
        for (int i = 0; i < 9; i++) {
            if (isCrossbowCandidate(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private static boolean hasArrowAmmo(MinecraftClient mc) {
        for (ItemStack s : mc.player.getInventory().main) {
            if (!s.isEmpty() && s.getItem() instanceof net.minecraft.item.ArrowItem) return true;
        }
        for (ItemStack s : mc.player.getInventory().offHand) {
            if (!s.isEmpty() && s.getItem() instanceof net.minecraft.item.ArrowItem) return true;
        }
        return false;
    }

    private static boolean isCrossbowCandidate(ItemStack stack) {
        if (stack.getItem() != Items.CROSSBOW) return false;
        net.minecraft.component.type.ChargedProjectilesComponent comp =
                stack.get(net.minecraft.component.DataComponentTypes.CHARGED_PROJECTILES);
        boolean unloaded = comp == null || comp.isEmpty();
        if (!unloaded) return false;
        return AutoCartMod.module != null && AutoCartMod.module.hasFlameEnchant(stack)
                || InventoryUtil.findItemInHotbar(Items.FLINT_AND_STEEL).found();
    }

    private static void renderCrossbowSlotHighlights(DrawContext ctx, MinecraftClient mc) {
        int hotbarLeft = mc.getWindow().getScaledWidth()  / 2 - HOTBAR_LEFT;
        int hotbarTop  = mc.getWindow().getScaledHeight() - HOTBAR_Y;
        for (int slot = 0; slot < 9; slot++) {
            if (isCrossbowCandidate(mc.player.getInventory().getStack(slot))) {
                int x = hotbarLeft + slot * SLOT_SIZE + 1;
                int y = hotbarTop + 1;
                ctx.fill(x, y, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, RED_FILL);
            }
        }
    }
}
