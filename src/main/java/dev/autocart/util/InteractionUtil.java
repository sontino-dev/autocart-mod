package dev.autocart.util;

import dev.autocart.AutoCartMod;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class InteractionUtil {

    private InteractionUtil() {}

    public static float[] calculateAngle(Vec3d target) {
        if (AutoCartMod.mc.player == null) return new float[]{0f, 0f};
        Vec3d eyes = AutoCartMod.mc.player.getEyePos();
        return calculateAngle(eyes, target);
    }

    public static float[] calculateAngle(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = -(to.y - from.y);
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dy, dist))), -90.0, 90.0);
        return new float[]{yaw, pitch};
    }

    public static Vec3d getEyePos() {
        if (AutoCartMod.mc.player == null) return Vec3d.ZERO;
        return AutoCartMod.mc.player.getEyePos();
    }
}
