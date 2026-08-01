package com.electrahub.ocpp.web;

import com.electrahub.ocpp.exception.RestExceptionHandler;
import com.electrahub.ocpp.service.IdTagFingerprintService;
import com.electrahub.ocpp.service.OcppAuthorizationGrantService;
import com.electrahub.ocpp.service.RemoteCommandService;
import com.electrahub.ocpp.service.RemoteStartCommandStore;
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

    private MockMvc mockMvc(ConnectionManager connectionManager) {
        RemoteCommandService service = new RemoteCommandService(
                connectionManager,
                mock(OcppAuthorizationGrantService.class),
                mock(RemoteStartCommandStore.class),
                new IdTagFingerprintService("test-only-id-tag-fingerprint-key-32-bytes"),
                new SimpleMeterRegistry(),
                30
        );
        return standaloneSetup(new RemoteCommandController(service, new ObjectMapper()))
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
