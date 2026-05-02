package com.electrahub.ocpp.web;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.service.RemoteCommandService;
import com.electrahub.ocpp.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ocpp/commands")
@Slf4j
public class RemoteCommandController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCommandController.class);


    private final RemoteCommandService remoteCommandService;

    /**
     * Executes remote command controller for `RemoteCommandController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.web`.
     * @param remoteCommandService input consumed by RemoteCommandController.
     */
    public RemoteCommandController(RemoteCommandService remoteCommandService) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering RemoteCommandController#RemoteCommandController");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering RemoteCommandController#RemoteCommandController with debug context");
        this.remoteCommandService = remoteCommandService;
    }

    @PostMapping("/{chargePointId}/remote-start")
    public ResponseEntity<CommandResponse> remoteStart(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody RemoteStartRequest request) {
        try {
            log.info("Remote start command for {}: idTag={}", chargePointId, request.idTag());
            JsonNode result = remoteCommandService
                .remoteStartTransaction(chargePointId, request.idTag(), request.connectorId())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in remote start: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/remote-stop")
    public ResponseEntity<CommandResponse> remoteStop(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody RemoteStopRequest request) {
        try {
            log.info("Remote stop command for {}: transactionId={}", chargePointId, request.transactionId());
            JsonNode result = remoteCommandService
                .remoteStopTransaction(chargePointId, request.transactionId())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in remote stop: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/reset")
    public ResponseEntity<CommandResponse> reset(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ResetRequest request) {
        try {
            log.info("Reset command for {}: type={}", chargePointId, request.type());
            JsonNode result = remoteCommandService
                .reset(chargePointId, request.type())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in reset: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/unlock-connector")
    public ResponseEntity<CommandResponse> unlockConnector(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody UnlockConnectorRequest request) {
        try {
            log.info("Unlock connector command for {}: connectorId={}", chargePointId, request.connectorId());
            JsonNode result = remoteCommandService
                .unlockConnector(chargePointId, request.connectorId())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in unlock connector: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/set-charging-profile")
    public ResponseEntity<CommandResponse> setChargingProfile(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody SetChargingProfileRequest request) {
        try {
            log.info("Set charging profile command for {}: connectorId={}", chargePointId, request.connectorId());
            JsonNode result = remoteCommandService
                .setChargingProfile(chargePointId, request.connectorId(), request.chargingProfile())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in set charging profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/change-configuration")
    public ResponseEntity<CommandResponse> changeConfiguration(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody ChangeConfigurationRequest request) {
        try {
            log.info("Change configuration command for {}: key={}", chargePointId, request.key());
            JsonNode result = remoteCommandService
                .changeConfiguration(chargePointId, request.key(), request.value())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in change configuration: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/get-configuration")
    public ResponseEntity<CommandResponse> getConfiguration(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody GetConfigurationRequest request) {
        try {
            log.info("Get configuration command for {}", chargePointId);
            JsonNode result = remoteCommandService
                .getConfiguration(chargePointId, request.keys())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in get configuration: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

    @PostMapping("/{chargePointId}/trigger-message")
    public ResponseEntity<CommandResponse> triggerMessage(
            @PathVariable("chargePointId") String chargePointId,
            @Valid @RequestBody TriggerMessageRequest request) {
        try {
            log.info("Trigger message command for {}: message={}", chargePointId, request.requestedMessage());
            JsonNode result = remoteCommandService
                .triggerMessage(chargePointId, request.requestedMessage())
                .get();
            return ResponseEntity.ok(new CommandResponse("success", result));
        } catch (Exception e) {
            log.error("Error in trigger message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new CommandResponse("error", null));
        }
    }

}
