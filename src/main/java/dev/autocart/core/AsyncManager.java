package dev.autocart.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncManager {

    private static final AtomicInteger threadId = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "autocart-async-" + threadId.getAndIncrement());
        t.setDaemon(true);
        return t;
    });

    public Future<?> run(Runnable task) {
        return executor.submit(task);
    }

    /**
     * @param delayMs delay in milliseconds before running the task
     */
    public Future<?> run(Runnable task, int delayMs) {
        return executor.submit(() -> {
            if (delayMs > 0) sleep(delayMs);
            task.run();
        });
    }

    public static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
