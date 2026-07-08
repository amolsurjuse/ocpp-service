package com.electrahub.ocpp.web;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.service.RemoteCommandService;
import com.electrahub.ocpp.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
    public ResponseEntity<CommandResponse> remoteStart(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody RemoteStartRequest request) {
        log.info("Remote start command for {}: idTag={}", chargePointId, request.idTag());
        JsonNode result = waitForCommand(remoteCommandService
                .remoteStartTransaction(chargePointId, request.idTag(), request.connectorId()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/remote-stop")
    public ResponseEntity<CommandResponse> remoteStop(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody RemoteStopRequest request) {
        log.info("Remote stop command for {}: transactionId={}", chargePointId, request.transactionId());
        JsonNode result = waitForCommand(remoteCommandService
                .remoteStopTransaction(chargePointId, request.transactionId()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/reset")
    public ResponseEntity<CommandResponse> reset(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ResetRequest request) {
        log.info("Reset command for {}: type={}", chargePointId, request.type());
        JsonNode result = waitForCommand(remoteCommandService.reset(chargePointId, request.type()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/unlock-connector")
    public ResponseEntity<CommandResponse> unlockConnector(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody UnlockConnectorRequest request) {
        log.info("Unlock connector command for {}: connectorId={}", chargePointId, request.connectorId());
        JsonNode result = waitForCommand(remoteCommandService
                .unlockConnector(chargePointId, request.connectorId()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/change-availability")
    public ResponseEntity<CommandResponse> changeAvailability(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ChangeAvailabilityRequest request) {
        log.info("Change availability command for {}: connectorId={} type={}",
                chargePointId, request.connectorId(), request.type());
        JsonNode result = waitForCommand(remoteCommandService
                .changeAvailability(chargePointId, request.connectorId(), request.type()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/set-charging-profile")
    public ResponseEntity<CommandResponse> setChargingProfile(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody SetChargingProfileRequest request) {
        log.info("Set charging profile command for {}: connectorId={}", chargePointId, request.connectorId());
        JsonNode result = waitForCommand(remoteCommandService
                .setChargingProfile(chargePointId, request.connectorId(), request.chargingProfile()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/change-configuration")
    public ResponseEntity<CommandResponse> changeConfiguration(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ChangeConfigurationRequest request) {
        log.info("Change configuration command for {}: key={}", chargePointId, request.key());
        JsonNode result = waitForCommand(remoteCommandService
                .changeConfiguration(chargePointId, request.key(), request.value()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/get-configuration")
    public ResponseEntity<CommandResponse> getConfiguration(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody GetConfigurationRequest request) {
        log.info("Get configuration command for {}", chargePointId);
        JsonNode result = waitForCommand(remoteCommandService
                .getConfiguration(chargePointId, request.keys()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    @PostMapping("/{chargePointId}/trigger-message")
    public ResponseEntity<CommandResponse> triggerMessage(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody TriggerMessageRequest request) {
        log.info("Trigger message command for {}: message={}", chargePointId, request.requestedMessage());
        JsonNode result = waitForCommand(remoteCommandService
                .triggerMessage(chargePointId, request.requestedMessage()));
        return ResponseEntity.ok(new CommandResponse("success", responsePayload(result)));
    }

    private JsonNode waitForCommand(CompletableFuture<JsonNode> command) {
        try {
            return command.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for OCPP command response", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("OCPP command failed", cause);
        }
    }

    private Object responsePayload(JsonNode result) {
        if (result == null || result.isNull()) {
            return null;
        }
        return objectMapper.convertValue(result, Object.class);
    }

}
