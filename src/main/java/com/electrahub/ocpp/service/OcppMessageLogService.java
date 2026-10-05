package com.electrahub.ocpp.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.OcppMessageLog;
import com.electrahub.ocpp.repository.OcppMessageLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class OcppMessageLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppMessageLogService.class);


    private final OcppMessageLogRepository messageLogRepository;

    /**
     * Executes ocpp message log service for `OcppMessageLogService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param messageLogRepository input consumed by OcppMessageLogService.
     */
    public OcppMessageLogService(OcppMessageLogRepository messageLogRepository) {
        LOGGER.info(" Entering OcppMessageLogService#OcppMessageLogService");
        LOGGER.debug(" Entering OcppMessageLogService#OcppMessageLogService with debug context");
        this.messageLogRepository = messageLogRepository;
    }

    /** One completed exchange per row avoids races between asynchronous insert and response updates. */
    @Async
    public void recordExchange(String charger, String messageId, String direction, String action,
            com.fasterxml.jackson.databind.JsonNode request, com.fasterxml.jackson.databind.JsonNode response,
            String outcome, String errorCode, Instant receivedAt, Instant respondedAt) {
        try {
            messageLogRepository.save(OcppMessageLog.builder().chargePointId(charger).messageId(messageId)
                    .direction(direction).action(action).payload(diagnosticPayload(request))
                    .responsePayload(diagnosticPayload(response)).status(outcome).errorCode(errorCode)
                    .createdAt(receivedAt).respondedAt(respondedAt).build());
        } catch (RuntimeException ex) {
            log.warn("OCPP diagnostic exchange could not be persisted; evidence may be incomplete errorType={}", ex.getClass().getSimpleName());
        }
    }

    static String diagnosticPayload(com.fasterxml.jackson.databind.JsonNode source) {
        var target = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        if (source != null && source.isObject()) {
            for (String key : java.util.List.of("transactionId", "connectorId", "status", "timestamp", "eventType", "seqNo")) {
                var field = source.path(key);
                if (field.isNumber() || field.isBoolean() || (field.isTextual() && field.asText().length() <= 128)) target.set(key, field);
            }
            var transaction = source.path("transactionInfo").path("transactionId");
            if ((transaction.isTextual() && transaction.asText().length() <= 128) || transaction.isIntegralNumber())
                target.putObject("transactionInfo").set("transactionId", transaction);
        }
        return target.toString();
    }

    /**
     * Executes log message for `OcppMessageLogService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by logMessage.
     * @param messageId input consumed by logMessage.
     * @param direction input consumed by logMessage.
     * @param action input consumed by logMessage.
     * @param payload input consumed by logMessage.
     */
    @Async
    public void logMessage(String chargePointId, String messageId, String direction, String action, String payload) {
        try {
            OcppMessageLog messageLog = OcppMessageLog.builder()
                .id(UUID.randomUUID())
                .chargePointId(chargePointId)
                .messageId(messageId)
                .direction(direction)
                .action(action)
                .payload(payload)
                .createdAt(Instant.now())
                .build();

            messageLogRepository.save(messageLog);
            log.debug("Logged OCPP message: chargePointId={}, messageId={}, direction={}", chargePointId, messageId, direction);
        } catch (Exception e) {
            log.error("Error logging OCPP message: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes log response for `OcppMessageLogService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param messageId input consumed by logResponse.
     * @param responsePayload input consumed by logResponse.
     * @param status input consumed by logResponse.
     */
    @Async
    public void logResponse(String messageId, String responsePayload, String status) {
        try {
            messageLogRepository.findByMessageId(messageId).ifPresent(messageLog -> {
                messageLog.setResponsePayload(responsePayload);
                messageLog.setStatus(status);
                messageLog.setRespondedAt(Instant.now());
                messageLogRepository.save(messageLog);
                log.debug("Logged response for messageId: {}", messageId);
            });
        } catch (Exception e) {
            log.error("Error logging response: {}", e.getMessage(), e);
        }
    }

}
