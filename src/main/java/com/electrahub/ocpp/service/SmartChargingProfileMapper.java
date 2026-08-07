package com.electrahub.ocpp.service;

import com.electrahub.ocpp.web.dto.SmartChargingClearRequest;
import com.electrahub.ocpp.web.dto.SmartChargingLimitRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class SmartChargingProfileMapper {

    private final ObjectMapper objectMapper;

    public SmartChargingProfileMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode setPayload(String protocol, SmartChargingLimitRequest request) {
        return isOcpp201(protocol) ? setPayload201(request) : setPayload16(request);
    }

    public ObjectNode clearPayload(String protocol, SmartChargingClearRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (isOcpp201(protocol)) {
            payload.put("chargingProfileId", request.profileId());
            return payload;
        }
        payload.put("id", request.profileId());
        payload.put("connectorId", request.connectorId());
        payload.put("chargingProfilePurpose", profilePurpose(request.purpose(), null));
        payload.put("stackLevel", request.stackLevel());
        return payload;
    }

    private ObjectNode setPayload16(SmartChargingLimitRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("connectorId", request.connectorId());
        ObjectNode profile = payload.putObject("csChargingProfiles");
        profile.put("chargingProfileId", request.profileId());
        Integer numericTransactionId = numericTransactionId(request.transactionId());
        if (numericTransactionId != null) {
            profile.put("transactionId", numericTransactionId);
        }
        profile.put("stackLevel", request.stackLevel());
        profile.put("chargingProfilePurpose", profilePurpose(request.purpose(), numericTransactionId));
        profile.put("chargingProfileKind", "Absolute");
        profile.put("validFrom", request.validFrom().toString());
        profile.put("validTo", request.validTo().toString());
        ObjectNode schedule = profile.putObject("chargingSchedule");
        schedule.put("startSchedule", request.validFrom().toString());
        schedule.put("chargingRateUnit", "W");
        addPeriod(schedule.putArray("chargingSchedulePeriod"), request.limitKw());
        return payload;
    }

    private ObjectNode setPayload201(SmartChargingLimitRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("evseId", request.evseId() == null ? request.connectorId() : request.evseId());
        ObjectNode profile = payload.putObject("chargingProfile");
        profile.put("id", request.profileId());
        profile.put("stackLevel", request.stackLevel());
        profile.put("chargingProfilePurpose", profilePurpose(request.purpose(), request.transactionId()));
        profile.put("chargingProfileKind", "Absolute");
        if (request.transactionId() != null && !request.transactionId().isBlank()
                && request.purpose() == SmartChargingLimitRequest.Purpose.SESSION_LIMIT) {
            profile.put("transactionId", request.transactionId().trim());
        }
        profile.put("validFrom", request.validFrom().toString());
        profile.put("validTo", request.validTo().toString());
        ObjectNode schedule = profile.putArray("chargingSchedule").addObject();
        schedule.put("id", request.scheduleId());
        schedule.put("startSchedule", request.validFrom().toString());
        schedule.put("chargingRateUnit", "W");
        addPeriod(schedule.putArray("chargingSchedulePeriod"), request.limitKw());
        return payload;
    }

    private void addPeriod(ArrayNode periods, BigDecimal limitKw) {
        ObjectNode period = periods.addObject();
        period.put("startPeriod", 0);
        period.put("limit", limitKw.multiply(BigDecimal.valueOf(1000))
                .setScale(1, RoundingMode.HALF_UP));
    }

    private String profilePurpose(SmartChargingLimitRequest.Purpose purpose, Object transactionId) {
        return purpose == SmartChargingLimitRequest.Purpose.SESSION_LIMIT && transactionId != null
                ? "TxProfile"
                : "TxDefaultProfile";
    }

    private Integer numericTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(transactionId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isOcpp201(String protocol) {
        return "OCPP201".equalsIgnoreCase(protocol);
    }
}
