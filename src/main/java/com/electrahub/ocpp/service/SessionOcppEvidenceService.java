package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppMessageLog;
import com.electrahub.ocpp.repository.OcppMessageLogRepository;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Correlates archive records server-side and exposes only operational metadata, never raw payloads. */
@Service
public class SessionOcppEvidenceService {
    static final int SCAN_LIMIT = 5000;
    private static final int PAGE_SIZE = 200;
    private static final int TIMELINE_LIMIT = 60;
    private final OcppMessageLogRepository messages;
    private final OcppConnectionRepository connections;
    private final ObjectMapper mapper = new ObjectMapper();

    public SessionOcppEvidenceService(OcppMessageLogRepository messages, OcppConnectionRepository connections) {
        this.messages = messages;
        this.connections = connections;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Evidence collect(String charger, String transaction, Integer connector, Instant from, Instant to) {
        if (charger == null || charger.isBlank() || charger.length() > 128 || from == null || to == null
                || from.isAfter(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0
                || (transaction != null && (transaction.isBlank() || transaction.length() > 128))
                || (connector != null && connector < 1)) throw new IllegalArgumentException("Invalid OCPP evidence scope");
        List<Event> correlated = new ArrayList<>();
        Map<String, Long> counts = new TreeMap<>();
        long total = 0;
        int scanned = 0, unreadable = 0;
        for (int page = 0; page * PAGE_SIZE < SCAN_LIMIT; page++) {
            var rows = messages.findByChargePointIdAndCreatedAtBetween(charger, from, to,
                    PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
            total = rows.getTotalElements();
            for (var row : rows) {
                scanned++;
                try {
                    Event event = correlate(row, transaction, connector);
                    if (event != null) {
                        correlated.add(event);
                        counts.merge(event.correlation() + ":" + event.action(), 1L, Long::sum);
                    }
                } catch (Exception ex) { unreadable++; }
            }
            if (!rows.hasNext()) break;
        }
        correlated.sort(Comparator.comparing(Event::sentAt).thenComparing(e -> e.id().toString()));
        List<Event> timeline = new ArrayList<>();
        for (int i = 0; i < correlated.size(); i++) {
            if (correlated.size() <= TIMELINE_LIMIT || i < TIMELINE_LIMIT / 2 || i >= correlated.size() - TIMELINE_LIMIT / 2)
                timeline.add(correlated.get(i));
        }
        Connection connection = connections.findByChargePointId(charger)
                .map(c -> new Connection(c.isActive(), c.getLastHeartbeatAt(), c.getLastSeenAt(), c.getDisconnectedAt()))
                .orElse(null);
        return new Evidence(charger, transaction, from, to, Instant.now(), total, scanned,
                scanned < total, unreadable, Map.copyOf(counts), correlated.size() - timeline.size(), List.copyOf(timeline), connection);
    }

    Event correlate(OcppMessageLog row, String transaction, Integer connector) throws Exception {
        JsonNode request = payload(row.getPayload()), response = payload(row.getResponsePayload());
        String requestTransaction = transaction(request), responseTransaction = transaction(response);
        String correlation;
        if (transaction != null && (transaction.equals(requestTransaction) || transaction.equals(responseTransaction))) {
            if ((requestTransaction != null && !transaction.equals(requestTransaction))
                    || (responseTransaction != null && !transaction.equals(responseTransaction))) return null;
            correlation = "TRANSACTION";
        } else {
            // Never downgrade a different explicit transaction to a connector-only association.
            if (requestTransaction != null || responseTransaction != null || connector == null) return null;
            // OCPP 2.x EVSE ids are not OCPP 1.6 connector numbers; do not conflate them.
            if (!request.path("connectorId").canConvertToInt() || request.path("connectorId").asInt() != connector
                    || !Set.of("RemoteStartTransaction", "StatusNotification").contains(row.getAction())) return null;
            correlation = "CONNECTOR_WINDOW_ONLY";
        }
        return new Event(row.getId(), safe(row.getAction()), safe(row.getDirection()), row.getCreatedAt(), row.getRespondedAt(),
                safe(response.path("status").asText(null)), safe(row.getStatus()), safe(response.path("errorCode").asText(row.getErrorCode())),
                safe(request.path("status").asText(null)), correlation);
    }

    private JsonNode payload(String raw) throws Exception {
        if (raw == null || raw.isBlank()) return mapper.createObjectNode();
        if (raw.length() > 262144) throw new IllegalArgumentException("Oversized archive payload");
        JsonNode node = mapper.readTree(raw);
        if (node.isArray()) {
            int type = node.path(0).asInt();
            if (type == 4) return mapper.createObjectNode().put("status", "CALL_ERROR").put("errorCode", safe(node.path(2).asText(null)));
            node = node.path(type == 2 ? 3 : type == 3 ? 2 : -1);
        }
        if (!node.isObject()) throw new IllegalArgumentException("Unreadable archive payload");
        return node;
    }

    private static String transaction(JsonNode node) {
        JsonNode id = node.path("transactionId");
        if (id.isMissingNode()) id = node.path("transactionInfo").path("transactionId");
        return id.isTextual() || id.isIntegralNumber() ? id.asText() : null;
    }
    private static String safe(String value) { return value != null && value.matches("[A-Za-z0-9_.:-]{1,80}") ? value : "unavailable"; }
    public record Event(UUID id, String action, String direction, Instant sentAt, Instant respondedAt,
            String responseStatus, String logStatus, String errorCode, String connectorStatus, String correlation) {}
    public record Connection(boolean recordedActive, Instant lastHeartbeatAt, Instant lastSeenAt, Instant disconnectedAt) {}
    public record Evidence(String chargerId, String transactionId, Instant from, Instant to, Instant collectedAt,
            long archiveRowsInWindow, int scannedRows, boolean scanTruncated, int unreadableRows,
            Map<String, Long> counts, int omittedTimelineRows, List<Event> timeline, Connection connection) {}
}
