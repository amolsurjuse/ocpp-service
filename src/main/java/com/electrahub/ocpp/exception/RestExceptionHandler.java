package com.electrahub.ocpp.exception;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@ControllerAdvice
@Slf4j
public class RestExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);


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

    @ExceptionHandler(ChargePointRouteUnavailableException.class)
    public ResponseEntity<ApiError> handleChargePointRouteUnavailable(
            ChargePointRouteUnavailableException ex,
            WebRequest request) {
        ApiError apiError = new ApiError(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "CHARGE_POINT_ROUTE_UNAVAILABLE",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(apiError);
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

    @ExceptionHandler(RemoteStartCommandKeyConflictException.class)
    public ResponseEntity<ApiError> handleRemoteStartCommandKeyConflict(
            RemoteStartCommandKeyConflictException ex,
            WebRequest request) {
        ApiError apiError = new ApiError(
            HttpStatus.CONFLICT.value(),
            "REMOTE_START_COMMAND_KEY_CONFLICT",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public ResponseEntity<ApiError> handleAsyncCommandFailure(Exception ex, WebRequest request) {
        Throwable cause = ex;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ChargePointNotConnectedException notConnected) {
            return handleChargePointNotConnected(notConnected, request);
        }
        if (cause instanceof ChargePointRouteUnavailableException routeUnavailable) {
            return handleChargePointRouteUnavailable(routeUnavailable, request);
        }
        if (cause instanceof OcppCommandTimeoutException timeout) {
            return handleOcppCommandTimeout(timeout, request);
        }
        if (cause instanceof OcppProtocolException protocol) {
            return handleOcppProtocolError(protocol, request);
        }
        if (cause instanceof Exception nested) {
            return handleGeneralException(nested, request);
        }
        return handleGeneralException(ex, request);
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
