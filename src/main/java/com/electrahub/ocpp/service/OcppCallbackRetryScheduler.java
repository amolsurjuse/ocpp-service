package com.electrahub.ocpp.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules retry work without tying up the connector callback workers while
 * a downstream service is temporarily unavailable.
 */
@Component
public class OcppCallbackRetryScheduler {
    private final ScheduledExecutorService executor;

    public OcppCallbackRetryScheduler(
            @Value("${ocpp.session-callback.retry.scheduler-threads:2}") int schedulerThreads
    ) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "ocpp-callback-retry-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newScheduledThreadPool(Math.max(1, schedulerThreads), threadFactory);
    }

    public void schedule(Duration delay, Runnable task) {
        executor.schedule(task, Math.max(0L, delay.toMillis()), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
