package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotNull;

public record UnlockConnectorRequest(
        @NotNull(message = "connectorId is required")
        Integer connectorId
) {
}
