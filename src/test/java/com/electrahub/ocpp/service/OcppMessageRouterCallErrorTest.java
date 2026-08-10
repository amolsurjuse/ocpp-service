package com.electrahub.ocpp.service;

import com.electrahub.ocpp.exception.OcppCallErrorException;
import com.electrahub.ocpp.websocket.OcppJsonRpcMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OcppMessageRouterCallErrorTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preservesBoundedProtocolErrorsWithoutReturningInternalDetails() {
        OcppMessageHandler handler = new OcppMessageHandler() {
            @Override public String getAction() { return "Get15118EVCertificate"; }
            @Override public JsonNode handle(String station, JsonNode payload) { return null; }
            @Override public JsonNode handle(String station, String messageId, JsonNode payload) {
                throw new OcppCallErrorException("PropertyConstraintViolation", "The EXI request is invalid");
            }
        };
        OcppMessageRouter router = new OcppMessageRouter(List.of(handler), mock(RemoteCommandService.class));

        OcppJsonRpcMessage response = router.routeMessage("EH-US-CHG-0003",
                OcppJsonRpcMessage.createCall("message-42", handler.getAction(), json.createObjectNode()));

        assertEquals(4, response.getMessageTypeId());
        assertEquals("message-42", response.getMessageId());
        assertEquals("PropertyConstraintViolation", response.getErrorCode());
        assertEquals("The EXI request is invalid", response.getErrorDescription());
    }
}
