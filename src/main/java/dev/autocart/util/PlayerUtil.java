package dev.autocart.util;

import dev.autocart.AutoCartMod;
import net.minecraft.util.math.Vec3d;

public final class PlayerUtil {

    private PlayerUtil() {}

    public static float squaredDistanceFromEyes(Vec3d targetPos) {
        if (AutoCartMod.mc.player == null) return 0f;
        double dx = targetPos.x - AutoCartMod.mc.player.getX();
        double dy = targetPos.y - (AutoCartMod.mc.player.getY() + AutoCartMod.mc.player.getEyeHeight(AutoCartMod.mc.player.getPose()));
        double dz = targetPos.z - AutoCartMod.mc.player.getZ();
        return (float)(dx * dx + dy * dy + dz * dz);
    }
}
