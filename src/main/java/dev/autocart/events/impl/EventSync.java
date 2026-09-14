package dev.autocart.events.impl;

import dev.autocart.events.ACEvent;

public class EventSync extends ACEvent {
    private final float yaw;
    private final float pitch;

    public EventSync(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
