package com.electrahub.ocpp.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.ocpp-events.kafka-enabled", havingValue = "true")
public class OcppDeviceEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String tenantId;
    private final Duration publishTimeout;

    public OcppDeviceEventPublisher(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        @Value("${app.ocpp-events.topic:ocpp.device-events.v2}") String topic,
        @Value("${app.ocpp-events.tenant-id:electrahub}") String tenantId,
        @Value("${app.ocpp-events.publish-timeout:3s}") Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.tenantId = tenantId;
        this.publishTimeout = publishTimeout;
    }

    /** Publishes synchronously so the OCPP call is acknowledged only after Kafka durability. */
    public UUID publish(String eventType, String chargePointId, Integer connectorId, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        String connectorKey = connectorKey(chargePointId, connectorId);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventVersion", 2);
        envelope.put("eventType", eventType);
        envelope.put("connectorKey", connectorKey);
        envelope.put("receivedAt", OffsetDateTime.now());
        envelope.put("traceId", MDC.get("traceId"));
        envelope.put("payload", payload);
        try {
            kafkaTemplate.send(topic, connectorKey, objectMapper.writeValueAsString(envelope))
                .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OCPP event payload is not JSON serializable", exception);
        } catch (Exception exception) {
            throw new OcppEventPublishException("Kafka did not durably accept OCPP event " + eventId, exception);
        }
    }

    String connectorKey(String chargePointId, Integer connectorId) {
        String charger = chargePointId == null || chargePointId.isBlank() ? "unknown" : chargePointId.trim();
        int connector = connectorId == null ? 0 : Math.max(0, connectorId);
        int evse = connector == 0 ? 0 : 1;
        return tenantId + ":" + charger + ":" + evse + ":" + connector;
    }

    public static class OcppEventPublishException extends RuntimeException {
        public OcppEventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
