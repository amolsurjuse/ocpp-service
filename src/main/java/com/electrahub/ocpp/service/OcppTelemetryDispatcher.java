package com.electrahub.ocpp.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coalesces best-effort charger telemetry without allowing it to block OCPP
 * lifecycle callbacks. Work for one connector remains serial so a meter value
 * cannot race a status transition for the same charging session.
 */
@Component
@Slf4j
public class OcppTelemetryDispatcher {
    private final TaskExecutor executor;
    private final ConcurrentHashMap<String, ConnectorTelemetrySlot> connectorSlots = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Counter meterCoalesced;
    private final Counter statusCoalesced;
    private final Counter meterDelivered;
    private final Counter statusDelivered;
    private final Counter meterFailed;
    private final Counter statusFailed;
    private final Counter meterRejected;
    private final Counter statusRejected;

    public OcppTelemetryDispatcher(
            @Qualifier("ocppTelemetryExecutor") TaskExecutor executor,
            MeterRegistry meterRegistry
    ) {
        this.executor = executor;
        this.meterCoalesced = counter(meterRegistry, "ocpp.telemetry.coalesced", TelemetryType.METER);
        this.statusCoalesced = counter(meterRegistry, "ocpp.telemetry.coalesced", TelemetryType.STATUS);
        this.meterDelivered = counter(meterRegistry, "ocpp.telemetry.delivered", TelemetryType.METER);
        this.statusDelivered = counter(meterRegistry, "ocpp.telemetry.delivered", TelemetryType.STATUS);
        this.meterFailed = counter(meterRegistry, "ocpp.telemetry.failed", TelemetryType.METER);
        this.statusFailed = counter(meterRegistry, "ocpp.telemetry.failed", TelemetryType.STATUS);
        this.meterRejected = counter(meterRegistry, "ocpp.telemetry.rejected", TelemetryType.METER);
        this.statusRejected = counter(meterRegistry, "ocpp.telemetry.rejected", TelemetryType.STATUS);
        Gauge.builder("ocpp.telemetry.pending.connectors", connectorSlots, value -> value.size())
                .description("Connectors with pending OCPP telemetry delivery")
                .register(meterRegistry);
    }

    public void dispatchMeterValues(String chargePointId, Integer connectorId, Runnable callback) {
        dispatch(connectorKey(chargePointId, connectorId), TelemetryType.METER, callback);
    }

    public void dispatchStatusNotification(String chargePointId, Integer connectorId, Runnable callback) {
        dispatch(connectorKey(chargePointId, connectorId), TelemetryType.STATUS, callback);
    }

    private void dispatch(String key, TelemetryType type, Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        while (true) {
            ConnectorTelemetrySlot slot = connectorSlots.computeIfAbsent(key, ignored -> new ConnectorTelemetrySlot());
            DispatchDecision decision = slot.offer(type, sequence.incrementAndGet(), callback);
            if (decision == DispatchDecision.RETIRED) {
                connectorSlots.remove(key, slot);
                continue;
            }

            if (decision == DispatchDecision.COALESCED) {
                coalescedCounter(type).increment();
            }
            if (decision == DispatchDecision.SCHEDULED) {
                try {
                    executor.execute(() -> drain(key, slot));
                } catch (RuntimeException exception) {
                    slot.retire();
                    connectorSlots.remove(key, slot);
                    rejectedCounter(type).increment();
                    log.warn("OCPP telemetry delivery queue rejected {} for connector {}", type, key, exception);
                }
            }
            return;
        }
    }

    private void drain(String key, ConnectorTelemetrySlot slot) {
        while (true) {
            PendingTelemetry telemetry = slot.next();
            if (telemetry == null) {
                connectorSlots.remove(key, slot);
                return;
            }
            try {
                telemetry.callback().run();
                deliveredCounter(telemetry.type()).increment();
            } catch (RuntimeException exception) {
                failedCounter(telemetry.type()).increment();
                log.warn("OCPP {} telemetry delivery failed for connector {}", telemetry.type(), key, exception);
            }
        }
    }

    private String connectorKey(String chargePointId, Integer connectorId) {
        return (chargePointId == null || chargePointId.isBlank() ? "unknown" : chargePointId)
                + ':' + (connectorId == null ? 0 : connectorId);
    }

    private Counter counter(MeterRegistry meterRegistry, String name, TelemetryType type) {
        return Counter.builder(name)
                .tag("type", type.metricValue)
                .register(meterRegistry);
    }

    private Counter coalescedCounter(TelemetryType type) {
        return type == TelemetryType.METER ? meterCoalesced : statusCoalesced;
    }

    private Counter deliveredCounter(TelemetryType type) {
        return type == TelemetryType.METER ? meterDelivered : statusDelivered;
    }

    private Counter failedCounter(TelemetryType type) {
        return type == TelemetryType.METER ? meterFailed : statusFailed;
    }

    private Counter rejectedCounter(TelemetryType type) {
        return type == TelemetryType.METER ? meterRejected : statusRejected;
    }

    private enum TelemetryType {
        METER("meter"),
        STATUS("status");

        private final String metricValue;

        TelemetryType(String metricValue) {
            this.metricValue = metricValue;
        }
    }

    private enum DispatchDecision {
        SCHEDULED,
        QUEUED,
        COALESCED,
        RETIRED
    }

    private record PendingTelemetry(TelemetryType type, long sequence, Runnable callback) {
    }

    private static final class ConnectorTelemetrySlot {
        private PendingTelemetry meter;
        private PendingTelemetry status;
        private boolean running;
        private boolean retired;

        synchronized DispatchDecision offer(TelemetryType type, long sequence, Runnable callback) {
            if (retired) {
                return DispatchDecision.RETIRED;
            }

            boolean replaced = type == TelemetryType.METER ? meter != null : status != null;
            PendingTelemetry pending = new PendingTelemetry(type, sequence, callback);
            if (type == TelemetryType.METER) {
                meter = pending;
            } else {
                status = pending;
            }

            if (!running) {
                running = true;
                return DispatchDecision.SCHEDULED;
            }
            return replaced ? DispatchDecision.COALESCED : DispatchDecision.QUEUED;
        }

        synchronized PendingTelemetry next() {
            PendingTelemetry next;
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

        synchronized void retire() {
            retired = true;
            running = false;
            meter = null;
            status = null;
        }
    }
}
