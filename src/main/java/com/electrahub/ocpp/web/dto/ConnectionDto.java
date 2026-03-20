package com.electrahub.ocpp.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ConnectionDto(
        UUID id,
        String chargePointId,
        String nodeId,
        String ocppProtocol,
        Instant connectedAt,
        Instant lastHeartbeatAt,
        Instant disconnectedAt,
        boolean active
) {
}
