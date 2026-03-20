package com.electrahub.ocpp.web.dto;

public record ConnectionCountDto(
        long activeConnections,
        int totalConnectors
) {
}
