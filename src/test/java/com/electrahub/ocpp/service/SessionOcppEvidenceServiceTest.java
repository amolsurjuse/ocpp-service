package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppMessageLog;
import com.electrahub.ocpp.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionOcppEvidenceServiceTest {
    private final OcppMessageLogRepository repository = mock(OcppMessageLogRepository.class);
    private final SessionOcppEvidenceService service = new SessionOcppEvidenceService(repository, mock(OcppConnectionRepository.class));

    @Test
    void rejectsOtherTransactionsEvenOnTheSameConnector() throws Exception {
        var row = row("MeterValues", "{\"connectorId\":1,\"transactionId\":99}", null);
        assertThat(service.correlate(row, "42", 1)).isNull();
    }

    @Test
    void startResponseCorrelatesTransactionWithoutExposingIdentityFields() throws Exception {
        var row = row("StartTransaction", "[2,\"x\",\"StartTransaction\",{\"idTag\":\"secret\",\"connectorId\":1}]",
                "[3,\"x\",{\"transactionId\":42,\"idTagInfo\":{\"status\":\"Accepted\"}}]");
        var event = service.correlate(row, "42", 1);
        assertThat(event.correlation()).isEqualTo("TRANSACTION");
        assertThat(event.toString()).doesNotContain("secret", "idTag");
    }

    @Test
    void connectorOnlyCommandsAreClearlyContextual() throws Exception {
        var event = service.correlate(row("RemoteStartTransaction", "{\"connectorId\":1}", "{\"status\":\"Rejected\"}"), "42", 1);
        assertThat(event.correlation()).isEqualTo("CONNECTOR_WINDOW_ONLY");
        assertThat(event.responseStatus()).isEqualTo("Rejected");
        assertThat(service.correlate(row("StatusNotification", "{\"connectorId\":2}", null), "42", 1)).isNull();
    }

    @Test
    void understandsOcpp201AndCallErrorFrames() throws Exception {
        assertThat(service.correlate(row("TransactionEvent", "{\"transactionInfo\":{\"transactionId\":\"tx-42\"}}", null), "tx-42", null).correlation())
                .isEqualTo("TRANSACTION");
        var event = service.correlate(row("RemoteStopTransaction", "{\"transactionId\":42}", "[4,\"x\",\"InternalError\",\"private stack trace\",{}]"), "42", null);
        assertThat(event.errorCode()).isEqualTo("InternalError");
        assertThat(event.toString()).doesNotContain("private stack");
    }

    @Test
    void boundsTheScanAndLabelsCountsAsPartial() {
        var page = java.util.stream.IntStream.range(0, 200).mapToObj(i -> row("MeterValues", "{\"transactionId\":42}", null)).toList();
        when(repository.findByChargePointIdAndCreatedAtBetween(anyString(), any(), any(), any())).thenAnswer(inv ->
                new PageImpl<>(page, inv.getArgument(3, Pageable.class), 6000));
        var report = service.collect("EH-1", "42", 1, Instant.parse("2026-09-28T10:00:00Z"), Instant.parse("2026-09-28T11:00:00Z"));
        assertThat(report.scannedRows()).isEqualTo(5000);
        assertThat(report.scanTruncated()).isTrue();
        assertThat(report.timeline()).hasSize(60);
        assertThat(report.omittedTimelineRows()).isEqualTo(4940);
        verify(repository, times(25)).findByChargePointIdAndCreatedAtBetween(anyString(), any(), any(), any());
    }

    @Test
    void rejectsUnboundedTimeRangesBeforeReadingTheDatabase() {
        assertThatThrownBy(() -> service.collect("EH-1", "42", 1, Instant.EPOCH, Instant.now())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    private OcppMessageLog row(String action, String payload, String response) {
        return OcppMessageLog.builder().id(UUID.randomUUID()).chargePointId("EH-1").action(action).direction("INBOUND")
                .createdAt(Instant.parse("2026-09-28T10:30:00Z")).payload(payload).responsePayload(response).build();
    }
}
