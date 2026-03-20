package com.electrahub.ocpp.web.dto;

public record MessageStatsDto(
        String chargePointId,
        long inboundMessages,
        long outboundMessages,
        long totalMessages
) {
}
