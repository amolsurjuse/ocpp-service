package com.electrahub.ocpp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
@Slf4j
public class RestExceptionHandler {

    @ExceptionHandler(ChargePointNotConnectedException.class)
    public ResponseEntity<ApiError> handleChargePointNotConnected(
            ChargePointNotConnectedException ex,
            WebRequest request) {
        ApiError apiError = new ApiError(
            HttpStatus.NOT_FOUND.value(),
            "CHARGE_POINT_NOT_CONNECTED",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(OcppCommandTimeoutException.class)
    public ResponseEntity<ApiError> handleOcppCommandTimeout(
            OcppCommandTimeoutException ex,
            WebRequest request) {
        ApiError apiError = new ApiError(
            HttpStatus.GATEWAY_TIMEOUT.value(),
            "COMMAND_TIMEOUT",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(apiError);
    }

    @ExceptionHandler(OcppProtocolException.class)
    public ResponseEntity<ApiError> handleOcppProtocolError(
            OcppProtocolException ex,
            WebRequest request) {
        ApiError apiError = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "PROTOCOL_ERROR",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(
            Exception ex,
            WebRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

}
