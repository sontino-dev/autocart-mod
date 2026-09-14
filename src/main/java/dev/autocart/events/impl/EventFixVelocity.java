package dev.autocart.events.impl;

import dev.autocart.events.ACEvent;
import net.minecraft.util.math.Vec3d;

public class EventFixVelocity extends ACEvent {
    private final Vec3d movementInput;
    private final float speed;
    private Vec3d velocity;

    public EventFixVelocity(Vec3d movementInput, float speed, Vec3d velocity) {
        this.movementInput = movementInput;
        this.speed = speed;
        this.velocity = velocity;
    }

    public Vec3d getMovementInput() { return movementInput; }
    public float getSpeed() { return speed; }
    public Vec3d getVelocity() { return velocity; }
    public void setVelocity(Vec3d velocity) { this.velocity = velocity; }
}
