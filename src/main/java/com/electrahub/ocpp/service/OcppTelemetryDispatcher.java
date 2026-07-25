package com.electrahub.ocpp.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Delivers OCPP session callbacks in connector order. Start and stop lifecycle
 * events are retained and retried; high-frequency meter/status telemetry is
 * coalesced behind those lifecycle events. This keeps the WebSocket protocol
 * loop responsive without allowing telemetry to race the charging state.
 */
@Component
@Slf4j
public class OcppTelemetryDispatcher {
    private final TaskExecutor executor;
    private final RetryScheduler retryScheduler;
    private final ConcurrentHashMap<String, ConnectorTelemetrySlot> connectorSlots = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<CallbackType, Counter> coalesced = new EnumMap<>(CallbackType.class);
    private final Map<CallbackType, Counter> delivered = new EnumMap<>(CallbackType.class);
    private final Map<CallbackType, Counter> failed = new EnumMap<>(CallbackType.class);
    private final Map<CallbackType, Counter> rejected = new EnumMap<>(CallbackType.class);
    private final Map<CallbackType, Counter> retries = new EnumMap<>(CallbackType.class);
    private final Map<CallbackType, Counter> deadLetters = new EnumMap<>(CallbackType.class);
    private final int lifecycleRetryAttempts;
    private final long initialRetryBackoffMillis;
    private final long maxRetryBackoffMillis;
    private final int maxLifecycleEventsPerConnector;

    @Autowired
    public OcppTelemetryDispatcher(
            @Qualifier("ocppTelemetryExecutor") TaskExecutor executor,
            OcppCallbackRetryScheduler callbackRetryScheduler,
            MeterRegistry meterRegistry,
            @Value("${ocpp.session-callback.retry.max-attempts:8}") int lifecycleRetryAttempts,
            @Value("${ocpp.session-callback.retry.initial-backoff-ms:250}") long initialRetryBackoffMillis,
            @Value("${ocpp.session-callback.retry.max-backoff-ms:5000}") long maxRetryBackoffMillis,
            @Value("${ocpp.session-callback.max-lifecycle-events-per-connector:32}") int maxLifecycleEventsPerConnector
    ) {
        this(
                executor,
                callbackRetryScheduler::schedule,
                meterRegistry,
                lifecycleRetryAttempts,
                initialRetryBackoffMillis,
                maxRetryBackoffMillis,
                maxLifecycleEventsPerConnector
        );
    }

    public OcppTelemetryDispatcher(TaskExecutor executor, MeterRegistry meterRegistry) {
        this(executor, (delay, callback) -> callback.run(), meterRegistry, 1, 0, 0, 32);
    }

    private OcppTelemetryDispatcher(
            TaskExecutor executor,
            RetryScheduler retryScheduler,
            MeterRegistry meterRegistry,
            int lifecycleRetryAttempts,
            long initialRetryBackoffMillis,
            long maxRetryBackoffMillis,
            int maxLifecycleEventsPerConnector
    ) {
        this.executor = executor;
        this.retryScheduler = retryScheduler;
        this.lifecycleRetryAttempts = Math.max(1, lifecycleRetryAttempts);
        this.initialRetryBackoffMillis = Math.max(0L, initialRetryBackoffMillis);
        this.maxRetryBackoffMillis = Math.max(this.initialRetryBackoffMillis, maxRetryBackoffMillis);
        this.maxLifecycleEventsPerConnector = Math.max(4, maxLifecycleEventsPerConnector);
        for (CallbackType type : CallbackType.values()) {
            delivered.put(type, counter(meterRegistry, "ocpp.session.callbacks.delivered", type));
            failed.put(type, counter(meterRegistry, "ocpp.session.callbacks.failed", type));
            rejected.put(type, counter(meterRegistry, "ocpp.session.callbacks.rejected", type));
            if (type.coalesced()) {
                coalesced.put(type, counter(meterRegistry, "ocpp.telemetry.coalesced", type));
            }
            if (type.lifecycle()) {
                retries.put(type, counter(meterRegistry, "ocpp.session.callbacks.retries", type));
                deadLetters.put(type, counter(meterRegistry, "ocpp.session.callbacks.dead_letters", type));
            }
        }
        Gauge.builder("ocpp.telemetry.pending.connectors", connectorSlots, value -> value.size())
                .description("Connectors with pending OCPP session callback delivery")
                .register(meterRegistry);
        Gauge.builder("ocpp.session.callbacks.pending.lifecycle", connectorSlots,
                        value -> value.values().stream().mapToLong(ConnectorTelemetrySlot::pendingLifecycleCount).sum())
                .description("Queued OCPP start and stop callbacks")
                .register(meterRegistry);
    }

    public boolean dispatchMeterValues(String chargePointId, Integer connectorId, Runnable callback) {
        return dispatch(connectorKey(chargePointId, connectorId), CallbackType.METER, callback);
    }

    public boolean dispatchStatusNotification(String chargePointId, Integer connectorId, Runnable callback) {
        return dispatch(connectorKey(chargePointId, connectorId), CallbackType.STATUS, callback);
    }

    public boolean dispatchStartTransaction(String chargePointId, Integer connectorId, Runnable callback) {
        return dispatch(connectorKey(chargePointId, connectorId), CallbackType.START, callback);
    }

    public boolean dispatchStopTransaction(String chargePointId, Integer connectorId, Runnable callback) {
        return dispatch(connectorKey(chargePointId, connectorId), CallbackType.STOP, callback);
    }

    private boolean dispatch(String key, CallbackType type, Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        while (true) {
            ConnectorTelemetrySlot slot = connectorSlots.computeIfAbsent(
                    key,
                    ignored -> new ConnectorTelemetrySlot(maxLifecycleEventsPerConnector)
            );
            DispatchDecision decision = slot.offer(type, sequence.incrementAndGet(), callback);
            if (decision == DispatchDecision.RETIRED) {
                connectorSlots.remove(key, slot);
                continue;
            }

            if (decision == DispatchDecision.COALESCED) {
                coalesced.get(type).increment();
            }
            if (decision == DispatchDecision.REJECTED) {
                rejected.get(type).increment();
                log.error("OCPP {} callback queue is full for connector {}", type.metricValue, key);
                return false;
            }
            if (decision == DispatchDecision.SCHEDULED) {
                try {
                    executor.execute(() -> drain(key, slot));
                } catch (RuntimeException exception) {
                    slot.retire();
                    connectorSlots.remove(key, slot);
                    rejected.get(type).increment();
                    log.warn("OCPP callback queue rejected {} for connector {}", type.metricValue, key, exception);
                    return false;
                }
            }
            return true;
        }
    }

    private void drain(String key, ConnectorTelemetrySlot slot) {
        while (true) {
            PendingCallback callback = slot.next();
            if (callback == null) {
                connectorSlots.remove(key, slot);
                return;
            }
            try {
                callback.callback().run();
                delivered.get(callback.type()).increment();
            } catch (RuntimeException exception) {
                if (callback.type().lifecycle()
                        && callback.attempt() < lifecycleRetryAttempts
                        && isRetriableLifecycleFailure(exception)) {
                    int nextAttempt = callback.attempt() + 1;
                    retries.get(callback.type()).increment();
                    Duration delay = retryDelay(nextAttempt);
                    log.warn("Retrying OCPP {} callback for connector {} attempt {}/{} after {}ms: {}",
                            callback.type().metricValue,
                            key,
                            nextAttempt,
                            lifecycleRetryAttempts,
                            delay.toMillis(),
                            exception.getMessage());
                    retryScheduler.schedule(delay, () -> resumeRetry(key, slot, callback.withAttempt(nextAttempt)));
                    return;
                }

                failed.get(callback.type()).increment();
                if (callback.type().lifecycle()) {
                    deadLetters.get(callback.type()).increment();
                    log.error("OCPP {} callback permanently failed for connector {} after {} attempt(s)",
                            callback.type().metricValue, key, callback.attempt(), exception);
                } else {
                    log.warn("OCPP {} telemetry delivery failed for connector {}", callback.type().metricValue, key, exception);
                }
            }
        }
    }

    private void resumeRetry(String key, ConnectorTelemetrySlot slot, PendingCallback callback) {
        if (!slot.requeueFirst(callback)) {
            return;
        }
        try {
            executor.execute(() -> drain(key, slot));
        } catch (RuntimeException exception) {
            slot.retire();
            connectorSlots.remove(key, slot);
            rejected.get(callback.type()).increment();
            deadLetters.get(callback.type()).increment();
            log.error("OCPP {} retry could not be scheduled for connector {}", callback.type().metricValue, key, exception);
        }
    }

    private String connectorKey(String chargePointId, Integer connectorId) {
        return (chargePointId == null || chargePointId.isBlank() ? "unknown" : chargePointId)
                + ':' + (connectorId == null ? 0 : connectorId);
    }

    private Counter counter(MeterRegistry meterRegistry, String name, CallbackType type) {
        return Counter.builder(name)
                .tag("type", type.metricValue)
                .register(meterRegistry);
    }

    private boolean isRetriableLifecycleFailure(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 404 || status == 409 || status == 429 || status >= 500;
        }
        return exception instanceof ResourceAccessException || exception.getCause() instanceof java.io.IOException;
    }

    private Duration retryDelay(int attempt) {
        long shift = Math.min(20, Math.max(0, attempt - 2));
        long multiplier = 1L << shift;
        long delay = Math.min(maxRetryBackoffMillis, initialRetryBackoffMillis * multiplier);
        return Duration.ofMillis(delay);
    }

    private enum CallbackType {
        START("start", true, false),
        STOP("stop", true, false),
        METER("meter", false, true),
        STATUS("status", false, true);

        private final String metricValue;
        private final boolean lifecycle;
        private final boolean coalesced;

        CallbackType(String metricValue, boolean lifecycle, boolean coalesced) {
            this.metricValue = metricValue;
            this.lifecycle = lifecycle;
            this.coalesced = coalesced;
        }

        private boolean lifecycle() {
            return lifecycle;
        }

        private boolean coalesced() {
            return coalesced;
        }
    }

    private enum DispatchDecision {
        SCHEDULED,
        QUEUED,
        COALESCED,
        REJECTED,
        RETIRED
    }

    private record PendingCallback(CallbackType type, long sequence, Runnable callback, int attempt) {
        private PendingCallback withAttempt(int nextAttempt) {
            return new PendingCallback(type, sequence, callback, nextAttempt);
        }
    }

    private static final class ConnectorTelemetrySlot {
        private final int maxLifecycleEvents;
        private final ArrayDeque<PendingCallback> lifecycle = new ArrayDeque<>();
        private PendingCallback meter;
        private PendingCallback status;
        private boolean running;
        private boolean retired;

        private ConnectorTelemetrySlot(int maxLifecycleEvents) {
            this.maxLifecycleEvents = maxLifecycleEvents;
        }

        synchronized DispatchDecision offer(CallbackType type, long sequence, Runnable callback) {
            if (retired) {
                return DispatchDecision.RETIRED;
            }

            PendingCallback pending = new PendingCallback(type, sequence, callback, 1);
            boolean replaced = false;
            if (type.lifecycle()) {
                if (lifecycle.size() >= maxLifecycleEvents) {
                    return DispatchDecision.REJECTED;
                }
                lifecycle.addLast(pending);
            } else if (type == CallbackType.METER) {
                replaced = meter != null;
                meter = pending;
            } else {
                replaced = status != null;
                status = pending;
            }

            if (!running) {
                running = true;
                return DispatchDecision.SCHEDULED;
            }
            return replaced ? DispatchDecision.COALESCED : DispatchDecision.QUEUED;
        }

        synchronized PendingCallback next() {
            PendingCallback next;
            if (!lifecycle.isEmpty()) {
                return lifecycle.removeFirst();
            }
            if (meter == null && status == null) {
                running = false;
                retired = true;
                return null;
            }
            if (meter == null) {
                next = status;
                status = null;
                return next;
            }
            if (status == null) {
                next = meter;
                meter = null;
                return next;
            }
            if (meter.sequence() <= status.sequence()) {
                next = meter;
                meter = null;
            } else {
                next = status;
                status = null;
            }
            return next;
        }

        synchronized boolean requeueFirst(PendingCallback callback) {
            if (retired) {
                return false;
            }
            // The callback was already admitted. It must retain its place even
            // if newer lifecycle events filled the configured queue limit.
            lifecycle.addFirst(callback);
            return true;
        }

        synchronized long pendingLifecycleCount() {
            return lifecycle.size();
        }

        synchronized void retire() {
            retired = true;
            running = false;
            lifecycle.clear();
            meter = null;
            status = null;
        }
    }

    @FunctionalInterface
    private interface RetryScheduler {
        void schedule(Duration delay, Runnable callback);
    }
}
