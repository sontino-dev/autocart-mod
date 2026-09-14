package dev.autocart.events.impl;

import dev.autocart.events.ACEvent;
import net.minecraft.client.input.Input;

public class EventKeyboardInput extends ACEvent {
    private final Input input;

    public EventKeyboardInput(Input input) {
        this.input = input;
    }

    public Input getInput() { return input; }
}
