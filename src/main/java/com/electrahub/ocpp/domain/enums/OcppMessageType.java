package com.electrahub.ocpp.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OcppMessageType {
    CALL(2),
    CALL_RESULT(3),
    /**
     * Executes call error for `OcppMessageType`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.domain.enums`.
     * @param 4 input consumed by CALL_ERROR.
     * @return result produced by CALL_ERROR.
     */
    CALL_ERROR(4);

    private final int value;

    /**
     * Executes from value for `OcppMessageType`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.domain.enums`.
     * @param value input consumed by fromValue.
     * @return result produced by fromValue.
     */
    public static OcppMessageType fromValue(int value) {
        for (OcppMessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OCPP message type: " + value);
    }
}
