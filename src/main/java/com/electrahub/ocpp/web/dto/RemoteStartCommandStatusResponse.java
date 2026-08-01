package com.electrahub.ocpp.web.dto;

import java.time.Instant;

public record RemoteStartCommandStatusResponse(
        String commandKey,
        String state,
        String outcome,
        String chargePointId,
        String protocol,
        Integer remoteStartId,
        Object responsePayload,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
