package com.electrahub.ocpp.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetRequest(
        @NotBlank(message = "type is required (Hard or Soft)")
        String type
) {
}
