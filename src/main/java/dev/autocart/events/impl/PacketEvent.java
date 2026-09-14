package dev.autocart.events.impl;

import dev.autocart.events.ACEvent;
import net.minecraft.network.packet.Packet;

public class PacketEvent extends ACEvent {
    private final Packet<?> packet;

    public PacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet<?>> T getPacket() { return (T) packet; }

    public static class Receive extends PacketEvent {
        public Receive(Packet<?> packet) { super(packet); }
    }

    public static class SendPost extends PacketEvent {
        public SendPost(Packet<?> packet) { super(packet); }
    }
}
