package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotNull;

public record RemoteStopRequest(
        @NotNull(message = "transactionId is required")
        Integer transactionId
) {
}
