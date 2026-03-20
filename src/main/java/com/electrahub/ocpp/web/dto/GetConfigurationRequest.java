package com.electrahub.ocpp.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record GetConfigurationRequest(
        JsonNode keys
) {
}
