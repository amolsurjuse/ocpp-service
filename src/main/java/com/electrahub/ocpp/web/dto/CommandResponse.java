package com.electrahub.ocpp.web.dto;

public record CommandResponse(
        String status,
        Object payload
) {
}
