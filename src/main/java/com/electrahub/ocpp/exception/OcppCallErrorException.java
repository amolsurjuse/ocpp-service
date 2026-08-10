package com.electrahub.ocpp.exception;

public class OcppCallErrorException extends RuntimeException {
    private final String errorCode;
    private final String safeDescription;

    public OcppCallErrorException(String errorCode, String safeDescription) {
        super(safeDescription);
        this.errorCode = required(errorCode, 64, "errorCode");
        this.safeDescription = required(safeDescription, 160, "safeDescription");
    }

    public String errorCode() {
        return errorCode;
    }

    public String safeDescription() {
        return safeDescription;
    }

    private static String required(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
