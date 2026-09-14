package dev.autocart.util;

import dev.autocart.AutoCartMod;

public final class MovementUtil {

    private MovementUtil() {}

    public static boolean isMoving() {
        if (AutoCartMod.mc.player == null) return false;
        return AutoCartMod.mc.player.input != null
                && (AutoCartMod.mc.player.input.movementForward != 0
                || AutoCartMod.mc.player.input.movementSideways != 0);
    }

    public static double getSpeed() {
        if (AutoCartMod.mc.player == null) return 0;
        return Math.hypot(
                AutoCartMod.mc.player.getVelocity().x,
                AutoCartMod.mc.player.getVelocity().z
        );
    }
}
