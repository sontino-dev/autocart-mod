package dev.autocart.events;

public class ACEvent {
    private boolean cancelled = false;

    public boolean isCancelled() { return cancelled; }
    public void cancel() { this.cancelled = true; }
}
