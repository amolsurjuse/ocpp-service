package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
public class ChargingStationTenantResolver {
    private final StationServiceClient stations;
    private final String deploymentTenant;

    public ChargingStationTenantResolver(
            StationServiceClient stations,
            @Value("${ocpp.iso15118.tenant-id:${OCPP_PNC_TENANT_ID:}}") String deploymentTenant) {
        this.stations = stations;
        this.deploymentTenant = requiredTenant(deploymentTenant);
    }

    public String resolve(String chargePointId) {
        String stationId = requiredStation(chargePointId);
        JsonNode station = stations.getStation(stationId);
        String registeredId = station == null ? "" : station.path("chargePointId").asText("").trim();
        if (!MessageDigest.isEqual(stationId.getBytes(StandardCharsets.UTF_8),
                registeredId.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Charging station ownership could not be verified");
        }
        return deploymentTenant;
    }

    private static String requiredTenant(String value) {
        String tenant = value == null ? "" : value.trim();
        if (!tenant.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalStateException("OCPP_PNC_TENANT_ID must identify exactly one deployment tenant");
        }
        return tenant;
    }

    private static String requiredStation(String value) {
        String station = value == null ? "" : value.trim();
        if (station.isEmpty() || station.length() > 128 || station.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Charging station ID is invalid");
        }
        return station;
    }
}
