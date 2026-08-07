package com.electrahub.ocpp.service;

import com.electrahub.ocpp.web.dto.SmartChargingClearRequest;
import com.electrahub.ocpp.web.dto.SmartChargingCommandResponse;
import com.electrahub.ocpp.web.dto.SmartChargingLimitRequest;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
public class SmartChargingCommandService {

    private final ConnectionManager connectionManager;
    private final RemoteCommandService remoteCommandService;
    private final SmartChargingProfileMapper profileMapper;
    private final SmartChargingIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public SmartChargingCommandService(
            ConnectionManager connectionManager,
            RemoteCommandService remoteCommandService,
            SmartChargingProfileMapper profileMapper,
            SmartChargingIdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.connectionManager = connectionManager;
        this.remoteCommandService = remoteCommandService;
        this.profileMapper = profileMapper;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public CompletableFuture<SmartChargingCommandResponse> setLimit(
            String chargePointId,
            SmartChargingLimitRequest request
    ) {
        String action = "SetChargingProfile";
        String scope = chargePointId + ":set:" + request.idempotencyKey();
        return idempotencyService.execute(scope, requestHash(action, request), () -> {
            String protocol = connectionManager.getProtocol(chargePointId);
            return remoteCommandService.sendCommand(chargePointId, action, profileMapper.setPayload(protocol, request))
                    .thenApply(payload -> result(protocol, action, request.profileId(), payload));
        });
    }

    public CompletableFuture<SmartChargingCommandResponse> clearLimit(
            String chargePointId,
            SmartChargingClearRequest request
    ) {
        String action = "ClearChargingProfile";
        String scope = chargePointId + ":clear:" + request.idempotencyKey();
        return idempotencyService.execute(scope, requestHash(action, request), () -> {
            String protocol = connectionManager.getProtocol(chargePointId);
            return remoteCommandService.sendCommand(chargePointId, action, profileMapper.clearPayload(protocol, request))
                    .thenApply(payload -> result(protocol, action, request.profileId(), payload));
        });
    }

    private SmartChargingCommandResponse result(String protocol, String action, int profileId, JsonNode payload) {
        String chargerStatus = payload == null ? null : payload.path("status").asText(null);
        boolean accepted = chargerStatus != null && "accepted".equals(chargerStatus.toLowerCase(Locale.ROOT));
        meterRegistry.counter(
                "electrahub.ocpp.smart_charging.command",
                "protocol", protocol,
                "action", action,
                "outcome", accepted ? "accepted" : "rejected"
        ).increment();
        Object responsePayload = payload == null || payload.isNull()
                ? null
                : objectMapper.convertValue(payload, Object.class);
        return new SmartChargingCommandResponse(
                protocol, action, profileId, chargerStatus, accepted, false, responsePayload);
    }

    private String requestHash(String action, Object request) {
        String canonical;
        if (request instanceof SmartChargingLimitRequest value) {
            canonical = String.join("|",
                    value.idempotencyKey(), value.connectorId().toString(), normalized(value.transactionId()),
                    value.profileId().toString(), value.scheduleId().toString(), value.stackLevel().toString(),
                    value.limitKw().stripTrailingZeros().toPlainString(), value.validFrom().toString(),
                    value.validTo().toString(), value.purpose().name());
        } else if (request instanceof SmartChargingClearRequest value) {
            canonical = String.join("|",
                    value.idempotencyKey(), value.connectorId().toString(), value.profileId().toString(),
                    value.stackLevel().toString(), value.purpose().name());
        } else {
            throw new IllegalArgumentException("Unsupported smart-charging request type");
        }
        return SmartChargingIdempotencyService.sha256(action + "|" + canonical);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
