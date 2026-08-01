package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RemoteStartRequest(
        @NotBlank(message = "idTag is required")
        String idTag,
        Integer connectorId,
        String correlationId,
        String commandKey
) {
}
