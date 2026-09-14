package dev.autocart.core;

import dev.autocart.events.ACEvent;
import dev.autocart.events.ACEventHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoCartEventBus {

    private final Map<Class<?>, List<HandlerEntry>> handlers = new HashMap<>();

    public void subscribe(Object listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(ACEventHandler.class)
                    && method.getParameterCount() == 1
                    && ACEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                method.setAccessible(true);
                Class<?> eventType = method.getParameterTypes()[0];
                handlers.computeIfAbsent(eventType, k -> new ArrayList<>())
                        .add(new HandlerEntry(listener, method));
            }
        }
    }

    public void unsubscribe(Object listener) {
        for (List<HandlerEntry> list : handlers.values()) {
            list.removeIf(h -> h.listener == listener);
        }
    }

    public <T extends ACEvent> T post(T event) {
        List<HandlerEntry> list = handlers.get(event.getClass());
        if (list == null) return event;
        for (HandlerEntry h : list) {
            try {
                h.method.invoke(h.listener, event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return event;
    }

    private record HandlerEntry(Object listener, Method method) {}
}
