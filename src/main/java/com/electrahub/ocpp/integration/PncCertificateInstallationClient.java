package com.electrahub.ocpp.integration;

import java.util.UUID;

public interface PncCertificateInstallationClient {
    Response install(Request request);

    record Request(String chargingStationId, String ocppMessageId, String action,
                   String schemaVersion, String exiRequestBase64) { }

    record Response(String status, String exiResponse, String failureCategory, UUID exchangeId) { }
}
