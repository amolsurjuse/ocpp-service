package com.electrahub.ocpp.web.dto;

import java.util.List;

public record GetConfigurationRequest(
        List<String> keys
) {
}
