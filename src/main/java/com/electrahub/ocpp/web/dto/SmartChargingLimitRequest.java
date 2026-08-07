package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record SmartChargingLimitRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,199}") String idempotencyKey,
        @NotNull @Min(1) @Max(256) Integer connectorId,
        @Min(1) @Max(65535) Integer evseId,
        @Size(max = 64) String transactionId,
        @NotNull @Min(0) Integer profileId,
        @NotNull @Min(0) Integer scheduleId,
        @NotNull @Min(0) @Max(100) Integer stackLevel,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal limitKw,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        @NotNull Purpose purpose
) {
    public enum Purpose {
        SESSION_LIMIT,
        FALLBACK_LIMIT
    }

    @AssertTrue(message = "validTo must be after validFrom and no more than 24 hours later")
    public boolean isValidityWindowValid() {
        if (validFrom == null || validTo == null || !validTo.isAfter(validFrom)) {
            return false;
        }
        return Duration.between(validFrom, validTo).compareTo(Duration.ofHours(24)) <= 0;
    }
}
