package com.electrahub.ocpp.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SetChargingProfileRequest(
        @NotNull(message = "connectorId is required")
        Integer connectorId,
        @NotNull(message = "chargingProfile is required")
        JsonNode chargingProfile
) {
}
