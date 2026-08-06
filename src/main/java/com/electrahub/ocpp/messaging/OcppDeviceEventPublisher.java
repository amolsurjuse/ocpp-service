package com.electrahub.ocpp.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
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
        ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate,
        ObjectMapper objectMapper,
        @Value("${app.ocpp-events.topic:ocpp.device-events.v2}") String topic,
        @Value("${app.ocpp-events.tenant-id:electrahub}") String tenantId,
        @Value("${app.ocpp-events.publish-timeout:3s}") Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.tenantId = tenantId;
        this.publishTimeout = publishTimeout;
    }

    /** Publishes synchronously so the OCPP call is acknowledged only after Kafka durability. */
    public UUID publish(String eventType, String chargePointId, Integer connectorId, Map<String, Object> payload) {
        return publish(eventType, chargePointId, connectorId, null, payload);
    }

    public UUID publish(String eventType, String chargePointId, Integer connectorId,
                        String sourceMessageId, Map<String, Object> payload) {
        if (kafkaTemplate == null) {
            throw new OcppEventPublishException(
                    "Kafka publisher is unavailable; refusing to acknowledge a non-durable OCPP event", null);
        }
        UUID eventId = durableEventId(eventType, chargePointId, sourceMessageId, payload);
        String connectorKey = connectorKey(chargePointId, connectorId);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventVersion", 2);
        envelope.put("eventType", eventType);
        envelope.put("connectorKey", connectorKey);
        envelope.put("receivedAt", OffsetDateTime.now());
        envelope.put("traceId", MDC.get("traceId"));
        envelope.put("sourceMessageId", sourceMessageId);
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

    UUID durableEventId(String eventType, String chargePointId, String sourceMessageId, Map<String, Object> payload) {
        if (sourceMessageId == null || sourceMessageId.isBlank()) {
            return UUID.randomUUID();
        }
        String charger = chargePointId == null ? "unknown" : chargePointId.trim();
        try {
            String identity = tenantId + '\u001f' + charger + '\u001f' + eventType + '\u001f'
                    + sourceMessageId.trim() + '\u001f' + objectMapper.writeValueAsString(payload);
            return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OCPP event payload is not JSON serializable", exception);
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
