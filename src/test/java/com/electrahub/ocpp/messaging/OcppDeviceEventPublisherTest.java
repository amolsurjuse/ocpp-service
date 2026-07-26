package com.electrahub.ocpp.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcppDeviceEventPublisherTest {
    @Test
    void publishesVersionedEnvelopeUsingCanonicalConnectorKey() throws Exception {
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        OcppDeviceEventPublisher publisher = new OcppDeviceEventPublisher(
            kafka, new ObjectMapper().findAndRegisterModules(), "ocpp.device-events.v2", "tenant-a", Duration.ofSeconds(1));

        publisher.publish("MeterValues", "CP-7", 2, Map.of("transactionId", 41));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(org.mockito.ArgumentMatchers.eq("ocpp.device-events.v2"), key.capture(), body.capture());
        assertEquals("tenant-a:CP-7:1:2", key.getValue());
        assertTrue(body.getValue().contains("\"eventVersion\":2"));
        assertTrue(body.getValue().contains("\"transactionId\":41"));
    }
}
