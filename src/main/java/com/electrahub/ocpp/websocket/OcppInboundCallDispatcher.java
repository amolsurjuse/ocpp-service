package com.electrahub.ocpp.websocket;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs OCPP CALL messages away from the WebSocket I/O threads while preserving
 * the order of callbacks emitted by an individual charge point.
 */
@Component
public class OcppInboundCallDispatcher {
    private final TaskExecutor executor;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private final Counter rejectedCalls;
    private final Counter failedCalls;

    public OcppInboundCallDispatcher(
            @Qualifier("ocppInboundCallExecutor") TaskExecutor executor,
            MeterRegistry meterRegistry
    ) {
        this.executor = executor;
        this.rejectedCalls = Counter.builder("ocpp.inbound.calls.rejected")
                .description("Inbound OCPP CALL messages rejected before processing")
                .register(meterRegistry);
        this.failedCalls = Counter.builder("ocpp.inbound.calls.failed")
                .description("Inbound OCPP CALL messages that could not be processed")
                .register(meterRegistry);
    }

    /**
     * Queues an inbound charger callback. Calls from the same charge point are
     * strictly serial; calls from different charge points may run in parallel.
     */
    public boolean dispatch(String chargePointId, Runnable task, Runnable onRejected) {
        try {
            CompletableFuture<Void> next = tails.compute(chargePointId, (key, previous) -> {
                CompletableFuture<Void> predecessor = previous == null
                        ? CompletableFuture.completedFuture(null)
                        : previous.handle((ignored, error) -> null);
                return predecessor.thenRunAsync(task, executor);
            });
            next.whenComplete((ignored, error) -> {
                if (error != null) {
                    failedCalls.increment();
                    onRejected.run();
                }
                tails.remove(chargePointId, next);
            });
            return true;
        } catch (RuntimeException ex) {
            rejectedCalls.increment();
            return false;
        }
    }

    int pendingChargePointCount() {
        return tails.size();
    }
}
