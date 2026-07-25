package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppAuthorizationGrantService;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class StartTransactionHandler implements OcppMessageHandler {
    private final SessionServiceClient sessionServiceClient;
    private final OcppAuthorizationGrantService authorizationGrants;
    private final OcppTelemetryDispatcher callbackDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates start transaction handler for `StartTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by StartTransactionHandler.
     */
    public StartTransactionHandler(
            SessionServiceClient sessionServiceClient,
            OcppAuthorizationGrantService authorizationGrants,
            OcppTelemetryDispatcher callbackDispatcher
    ) {
        this.sessionServiceClient = sessionServiceClient;
        this.authorizationGrants = authorizationGrants;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * Retrieves get action for `StartTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "StartTransaction";
    }

    /**
     * Processes handle for `StartTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param chargePointId input consumed by handle.
     * @param payload input consumed by handle.
     * @return result produced by handle.
     */
    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            int connectorId = payload.path("connectorId").asInt();
            String idTag = payload.path("idTag").asText();
            int meterStart = payload.path("meterStart").asInt();
            String timestamp = payload.path("timestamp").asText();
            int transactionId = payload.path("transactionId").asInt(generateTransactionId());

            log.info("Starting transaction for charge point: {}, connector: {}, idTag: {}",
                chargePointId, connectorId, idTag);

            if (isCardPresentToken(idTag)) {
                // Card-present sessions must be verified against the payment service before
                // the charger is told that the transaction is accepted.
                sessionServiceClient.onStartTransaction(
                        chargePointId,
                        connectorId,
                        idTag,
                        null,
                        meterStart,
                        timestamp,
                        transactionId
                );
                return acceptedResponse(transactionId);
            }

            if (!authorizationGrants.consumeForStart(chargePointId, connectorId, idTag, transactionId)) {
                log.warn("Rejecting StartTransaction without an active authorization grant for chargePointId={} connectorId={}",
                        chargePointId, connectorId);
                return invalidResponse();
            }

            boolean queued = callbackDispatcher.dispatchStartTransaction(chargePointId, connectorId, () ->
                    sessionServiceClient.onStartTransaction(
                            chargePointId,
                            connectorId,
                            idTag,
                            null,
                            meterStart,
                            timestamp,
                            transactionId
                    )
            );
            if (!queued) {
                return invalidResponse();
            }

            log.debug("StartTransaction response: transactionId={}", transactionId);
            return acceptedResponse(transactionId);
        } catch (Exception e) {
            if (SessionServiceClient.isExpectedSessionCallbackFailure(e)) {
                log.warn("StartTransaction rejected by session-service for chargePointId={} summary={}",
                        chargePointId, SessionServiceClient.callbackFailureSummary(e));
            } else {
                log.error("Error handling StartTransaction: {}", e.getMessage(), e);
            }
            return invalidResponse();
        }
    }

    private ObjectNode acceptedResponse(int transactionId) {
        ObjectNode idTagInfo = objectMapper.createObjectNode();
        idTagInfo.put("status", "Accepted");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("transactionId", transactionId);
        response.set("idTagInfo", idTagInfo);
        return response;
    }

    private ObjectNode invalidResponse() {
        ObjectNode idTagInfo = objectMapper.createObjectNode();
        idTagInfo.put("status", "Invalid");
        ObjectNode response = objectMapper.createObjectNode();
        response.put("transactionId", 0);
        response.set("idTagInfo", idTagInfo);
        return response;
    }

    private boolean isCardPresentToken(String idTag) {
        return idTag != null && idTag.regionMatches(true, 0, "CP:", 0, 3);
    }

    private int generateTransactionId() {
        long nowMillis = Instant.now().toEpochMilli();
        return (int) (nowMillis % Integer.MAX_VALUE);
    }

}
