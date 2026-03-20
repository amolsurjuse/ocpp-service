package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeConfigurationRequest(
        @NotBlank(message = "key is required")
        String key,
        @NotBlank(message = "value is required")
        String value
) {
}
