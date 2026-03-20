package com.electrahub.ocpp.service;

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

    private final OcppMessageLogRepository messageLogRepository;

    public OcppMessageLogService(OcppMessageLogRepository messageLogRepository) {
        this.messageLogRepository = messageLogRepository;
    }

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
