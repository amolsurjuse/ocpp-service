package com.electrahub.ocpp.web;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.exception.OcppCommandTimeoutException;
import com.electrahub.ocpp.service.RemoteCommandService;
import com.electrahub.ocpp.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/ocpp/commands")
@Slf4j
public class RemoteCommandController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCommandController.class);


    private final RemoteCommandService remoteCommandService;
    private final ObjectMapper objectMapper;

    /**
     * Executes remote command controller for `RemoteCommandController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.web`.
     * @param remoteCommandService input consumed by RemoteCommandController.
     */
    public RemoteCommandController(RemoteCommandService remoteCommandService, ObjectMapper objectMapper) {
        LOGGER.info(" Entering RemoteCommandController#RemoteCommandController");
        LOGGER.debug(" Entering RemoteCommandController#RemoteCommandController with debug context");
        this.remoteCommandService = remoteCommandService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{chargePointId}/remote-start")
    public CompletableFuture<ResponseEntity<CommandResponse>> remoteStart(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody RemoteStartRequest request) {
        log.info("Remote start command for {}: connectorId={} commandKeyPresent={}",
                chargePointId, request.connectorId(), request.commandKey() != null);
        return commandResponse(remoteCommandService
                .remoteStartTransaction(
                        chargePointId,
                        request.idTag(),
                        request.connectorId(),
                        request.correlationId(),
                        request.commandKey()
                ));
    }

    @GetMapping("/remote-start/status")
    public ResponseEntity<RemoteStartCommandStatusResponse> remoteStartStatus(
            @RequestParam("commandKey") String commandKey
    ) {
        return remoteCommandService.remoteStartStatus(commandKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{chargePointId}/remote-stop")
    public CompletableFuture<ResponseEntity<CommandResponse>> remoteStop(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody RemoteStopRequest request) {
        log.info("Remote stop command for {}: transactionId={}", chargePointId, request.transactionId());
        return commandResponse(remoteCommandService
                .remoteStopTransaction(chargePointId, request.transactionId()));
    }

    @PostMapping("/{chargePointId}/reset")
    public CompletableFuture<ResponseEntity<CommandResponse>> reset(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ResetRequest request) {
        log.info("Reset command for {}: type={}", chargePointId, request.type());
        return commandResponse(remoteCommandService.reset(chargePointId, request.type()));
    }

    @PostMapping("/{chargePointId}/unlock-connector")
    public CompletableFuture<ResponseEntity<CommandResponse>> unlockConnector(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody UnlockConnectorRequest request) {
        log.info("Unlock connector command for {}: connectorId={}", chargePointId, request.connectorId());
        return commandResponse(remoteCommandService
                .unlockConnector(chargePointId, request.connectorId()));
    }

    @PostMapping("/{chargePointId}/change-availability")
    public CompletableFuture<ResponseEntity<CommandResponse>> changeAvailability(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ChangeAvailabilityRequest request) {
        log.info("Change availability command for {}: connectorId={} type={}",
                chargePointId, request.connectorId(), request.type());
        return commandResponse(remoteCommandService
                .changeAvailability(chargePointId, request.connectorId(), request.type()));
    }

    @PostMapping("/{chargePointId}/set-charging-profile")
    public CompletableFuture<ResponseEntity<CommandResponse>> setChargingProfile(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody SetChargingProfileRequest request) {
        log.info("Set charging profile command for {}: connectorId={}", chargePointId, request.connectorId());
        return commandResponse(remoteCommandService
                .setChargingProfile(
                        chargePointId,
                        request.connectorId(),
                        objectMapper.valueToTree(request.chargingProfile())
                ));
    }

    @PostMapping("/{chargePointId}/change-configuration")
    public CompletableFuture<ResponseEntity<CommandResponse>> changeConfiguration(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ChangeConfigurationRequest request) {
        log.info("Change configuration command for {}: key={}", chargePointId, request.key());
        return commandResponse(remoteCommandService
                .changeConfiguration(chargePointId, request.key(), request.value()));
    }

    @PostMapping("/{chargePointId}/get-configuration")
    public CompletableFuture<ResponseEntity<CommandResponse>> getConfiguration(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody GetConfigurationRequest request) {
        log.info("Get configuration command for {}", chargePointId);
        return commandResponse(remoteCommandService
                .getConfiguration(chargePointId, objectMapper.valueToTree(request.keys())));
    }

    @PostMapping("/{chargePointId}/trigger-message")
    public CompletableFuture<ResponseEntity<CommandResponse>> triggerMessage(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody TriggerMessageRequest request) {
        log.info("Trigger message command for {}: message={}", chargePointId, request.requestedMessage());
        return commandResponse(remoteCommandService
                .triggerMessage(chargePointId, request.requestedMessage()));
    }

    private CompletableFuture<ResponseEntity<CommandResponse>> commandResponse(CompletableFuture<JsonNode> command) {
        return command.handle((result, throwable) -> {
            if (throwable != null) {
                throw commandFailure(throwable);
            }
            return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
        });
    }

    private RuntimeException commandFailure(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof TimeoutException) {
            return new OcppCommandTimeoutException("Timed out waiting for an OCPP command response");
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("OCPP command failed", cause);
    }

    private Object responsePayload(JsonNode result) {
        if (result == null || result.isNull()) {
            return null;
        }
        return objectMapper.convertValue(result, Object.class);
    }

}
