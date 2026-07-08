package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeAvailabilityRequest(
        Integer connectorId,
        @NotBlank(message = "type is required")
        String type
) {
}
