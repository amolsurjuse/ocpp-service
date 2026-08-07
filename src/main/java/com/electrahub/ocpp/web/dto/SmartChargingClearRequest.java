package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SmartChargingClearRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,199}") String idempotencyKey,
        @NotNull @Min(1) @Max(256) Integer connectorId,
        @Min(1) @Max(65535) Integer evseId,
        @NotNull @Min(0) Integer profileId,
        @NotNull @Min(0) @Max(100) Integer stackLevel,
        @NotNull SmartChargingLimitRequest.Purpose purpose
) {
}
