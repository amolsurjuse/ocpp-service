package com.electrahub.ocpp.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OcppMessageType {
    CALL(2),
    CALL_RESULT(3),
    CALL_ERROR(4);

    private final int value;

    public static OcppMessageType fromValue(int value) {
        for (OcppMessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OCPP message type: " + value);
    }
}
