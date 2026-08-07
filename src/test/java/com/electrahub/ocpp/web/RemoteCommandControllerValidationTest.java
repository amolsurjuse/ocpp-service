package com.electrahub.ocpp.web;

import com.electrahub.ocpp.exception.RestExceptionHandler;
import com.electrahub.ocpp.service.IdTagFingerprintService;
import com.electrahub.ocpp.service.OcppAuthorizationGrantService;
import com.electrahub.ocpp.service.OcppClusterCommandRouter;
import com.electrahub.ocpp.service.RemoteCommandService;
import com.electrahub.ocpp.service.RemoteStartCommandStore;
import com.electrahub.ocpp.service.SmartChargingCommandService;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RemoteCommandControllerValidationTest {

    @Test
    void suppliedBlankCommandKeyReturnsHttp400() throws Exception {
        MockMvc mockMvc = mockMvc(mock(ConnectionManager.class));

        mockMvc.perform(post("/api/v1/ocpp/commands/CP-1/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(remoteStartBody("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PROTOCOL_ERROR"));
    }

    @Test
    void nonOwningReplicaReturnsRetryableHttp503() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        when(connectionManager.connectionOwnership("CP-REMOTE"))
                .thenReturn(ConnectionManager.ConnectionOwnership.REMOTE_OWNER);

        mockMvc(connectionManager)
                .perform(post("/api/v1/ocpp/commands/CP-REMOTE/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(remoteStartBody("remote-start:session-1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("CHARGE_POINT_ROUTE_UNAVAILABLE"));
    }

    @Test
    void genuinelyOfflineChargePointReturnsHttp404() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        when(connectionManager.connectionOwnership("CP-OFFLINE"))
                .thenReturn(ConnectionManager.ConnectionOwnership.OFFLINE);

        mockMvc(connectionManager)
                .perform(post("/api/v1/ocpp/commands/CP-OFFLINE/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(remoteStartBody("remote-start:session-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHARGE_POINT_NOT_CONNECTED"));
    }

    @Test
    void getConfigurationUsesTransportNeutralKeysPayload() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        when(connectionManager.connectionOwnership("CP-OFFLINE"))
                .thenReturn(ConnectionManager.ConnectionOwnership.OFFLINE);

        mockMvc(connectionManager)
                .perform(post("/api/v1/ocpp/commands/CP-OFFLINE/get-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"HeartbeatInterval\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHARGE_POINT_NOT_CONNECTED"));
    }

    @Test
    void setChargingProfileUsesTransportNeutralProfilePayload() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        when(connectionManager.connectionOwnership("CP-OFFLINE"))
                .thenReturn(ConnectionManager.ConnectionOwnership.OFFLINE);

        mockMvc(connectionManager)
                .perform(post("/api/v1/ocpp/commands/CP-OFFLINE/set-charging-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectorId":1,"chargingProfile":{"chargingProfileId":10,"stackLevel":0}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHARGE_POINT_NOT_CONNECTED"));
    }

    @Test
    void typedSmartChargingLimitRejectsInvalidValidityWindow() throws Exception {
        mockMvc(mock(ConnectionManager.class))
                .perform(post("/api/v1/ocpp/commands/CP-1/smart-charging-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey":"decision:connector:1",
                                  "connectorId":1,
                                  "transactionId":"tx-1",
                                  "profileId":100,
                                  "scheduleId":200,
                                  "stackLevel":10,
                                  "limitKw":42.5,
                                  "validFrom":"2026-08-07T13:00:00Z",
                                  "validTo":"2026-08-07T12:00:00Z",
                                  "purpose":"SESSION_LIMIT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private MockMvc mockMvc(ConnectionManager connectionManager) {
        RemoteCommandService service = new RemoteCommandService(
                connectionManager,
                mock(OcppAuthorizationGrantService.class),
                mock(RemoteStartCommandStore.class),
                new IdTagFingerprintService("test-only-id-tag-fingerprint-key-32-bytes"),
                new SimpleMeterRegistry(),
                mock(OcppClusterCommandRouter.class),
                30
        );
        return standaloneSetup(new RemoteCommandController(
                service, new ObjectMapper(), mock(SmartChargingCommandService.class)))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    private String remoteStartBody(String commandKey) {
        return """
                {
                  "idTag": "RFID-1",
                  "connectorId": 1,
                  "correlationId": "session-1",
                  "commandKey": "%s"
                }
                """.formatted(commandKey);
    }
}
