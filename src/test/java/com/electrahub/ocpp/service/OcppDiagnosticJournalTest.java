package com.electrahub.ocpp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OcppDiagnosticJournalTest {
    @Test
    void journalKeepsCorrelationAndDropsCredentialsAndRawPayloads() throws Exception {
        var source = new ObjectMapper().readTree("""
                {"connectorId":1,"transactionId":42,"idTag":"secret-token","cardNumber":"4111111111111111",
                 "transactionInfo":{"transactionId":"tx-42","secret":"hidden"},"customData":{"password":"secret"},
                 "status":"Accepted","sampledValue":[{"value":123}]}
                """);
        assertThat(OcppMessageLogService.diagnosticPayload(source)).contains("connectorId", "42", "Accepted")
                .doesNotContain("secret", "cardNumber", "password", "sampledValue", "411111");
    }
}
