package dev.autocart.util;

import dev.autocart.AutoCartMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class InventoryUtil {

    private InventoryUtil() {}

    public record InvResult(int slot, boolean found, ItemStack stack) {
        private static final InvResult NOT_FOUND = new InvResult(-1, false, ItemStack.EMPTY);
        public static InvResult notFound() { return NOT_FOUND; }
        public boolean isInHotbar() { return found && slot >= 0 && slot < 9; }
    }

    public static InvResult findInHotbar(Predicate<ItemStack> test) {
        if (AutoCartMod.mc.player == null) return InvResult.notFound();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = AutoCartMod.mc.player.getInventory().getStack(i);
            if (test.test(stack)) return new InvResult(i, true, stack);
        }
        return InvResult.notFound();
    }

    public static InvResult findItemInHotbar(Item... items) {
        List<Item> list = Arrays.asList(items);
        return findInHotbar(s -> list.contains(s.getItem()));
    }

    public static InvResult findItemInInventory(Item... items) {
        if (AutoCartMod.mc.player == null) return InvResult.notFound();
        List<Item> list = Arrays.asList(items);
        for (int i = 36; i >= 0; i--) {
            ItemStack s = AutoCartMod.mc.player.getInventory().getStack(i);
            if (list.contains(s.getItem())) {
                int slot = i < 9 ? i : i;
                return new InvResult(slot, true, s);
            }
        }
        return InvResult.notFound();
    }

    public static void switchToSlot(int slot) {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.interactionManager == null) return;
        if (slot < 0 || slot > 8) return;
        AutoCartMod.mc.player.getInventory().selectedSlot = slot;
        ((dev.autocart.accessor.IInteractionManager) AutoCartMod.mc.interactionManager).syncSlot();
    }

    public static int getItemCount(Item item) {
        if (AutoCartMod.mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i <= 44; i++) {
            ItemStack s = AutoCartMod.mc.player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }
}
