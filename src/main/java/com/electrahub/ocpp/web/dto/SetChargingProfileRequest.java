package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SetChargingProfileRequest(
        @NotNull(message = "connectorId is required")
        Integer connectorId,
        @NotNull(message = "chargingProfile is required")
        Map<String, Object> chargingProfile
) {
}
