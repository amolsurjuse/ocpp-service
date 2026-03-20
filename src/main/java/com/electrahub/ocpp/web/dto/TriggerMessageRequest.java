package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TriggerMessageRequest(
        @NotBlank(message = "requestedMessage is required")
        String requestedMessage
) {
}
