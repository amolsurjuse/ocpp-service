package com.electrahub.ocpp.service;

import com.electrahub.ocpp.web.dto.SmartChargingClearRequest;
import com.electrahub.ocpp.web.dto.SmartChargingLimitRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SmartChargingProfileMapperTest {

    private final SmartChargingProfileMapper mapper = new SmartChargingProfileMapper(new ObjectMapper());

    @Test
    void mapsExactOcpp16TransactionProfile() {
        JsonNode payload = mapper.setPayload("OCPP16J", request("123", SmartChargingLimitRequest.Purpose.SESSION_LIMIT));

        assertThat(payload.path("connectorId").asInt()).isEqualTo(1);
        JsonNode profile = payload.path("csChargingProfiles");
        assertThat(profile.path("chargingProfileId").asInt()).isEqualTo(120045);
        assertThat(profile.path("transactionId").asInt()).isEqualTo(123);
        assertThat(profile.path("chargingProfilePurpose").asText()).isEqualTo("TxProfile");
        assertThat(profile.path("chargingSchedule").path("chargingRateUnit").asText()).isEqualTo("W");
        assertThat(profile.path("chargingSchedule").path("chargingSchedulePeriod").get(0).path("limit").decimalValue())
                .isEqualByComparingTo("42500.0");
        assertThat(payload.has("evseId")).isFalse();
    }

    @Test
    void nonNumericOcpp16TransactionFallsBackToDefaultProfile() {
        JsonNode profile = mapper.setPayload(
                "OCPP16J", request("uuid-transaction", SmartChargingLimitRequest.Purpose.SESSION_LIMIT))
                .path("csChargingProfiles");

        assertThat(profile.path("chargingProfilePurpose").asText()).isEqualTo("TxDefaultProfile");
        assertThat(profile.has("transactionId")).isFalse();
    }

    @Test
    void mapsExactOcpp201Profile() {
        JsonNode payload = mapper.setPayload("OCPP201", request("uuid-transaction", SmartChargingLimitRequest.Purpose.SESSION_LIMIT));

        assertThat(payload.path("evseId").asInt()).isEqualTo(1);
        JsonNode profile = payload.path("chargingProfile");
        assertThat(profile.path("id").asInt()).isEqualTo(120045);
        assertThat(profile.path("transactionId").asText()).isEqualTo("uuid-transaction");
        assertThat(profile.path("chargingProfilePurpose").asText()).isEqualTo("TxProfile");
        assertThat(profile.path("chargingSchedule").isArray()).isTrue();
        assertThat(profile.path("chargingSchedule").get(0).path("id").asInt()).isEqualTo(220045);
        assertThat(profile.path("chargingSchedule").get(0).path("chargingSchedulePeriod").get(0).path("limit").decimalValue())
                .isEqualByComparingTo("42500.0");
        assertThat(payload.has("connectorId")).isFalse();
    }

    @Test
    void clearPayloadIsVersionAware() {
        SmartChargingClearRequest request = new SmartChargingClearRequest(
                "decision:clear", 1, 120045, 10, SmartChargingLimitRequest.Purpose.SESSION_LIMIT);

        JsonNode v16 = mapper.clearPayload("OCPP16J", request);
        JsonNode v201 = mapper.clearPayload("OCPP201", request);

        assertThat(v16.path("id").asInt()).isEqualTo(120045);
        assertThat(v16.path("connectorId").asInt()).isEqualTo(1);
        assertThat(v16.path("chargingProfilePurpose").asText()).isEqualTo("TxDefaultProfile");
        assertThat(v201.path("chargingProfileId").asInt()).isEqualTo(120045);
        assertThat(v201.size()).isEqualTo(1);
    }

    private SmartChargingLimitRequest request(String transactionId, SmartChargingLimitRequest.Purpose purpose) {
        return new SmartChargingLimitRequest(
                "decision:connector:1", 1, transactionId, 120045, 220045, 10,
                new BigDecimal("42.5"),
                Instant.parse("2026-08-07T12:00:00Z"),
                Instant.parse("2026-08-07T12:02:00Z"),
                purpose
        );
    }
}
