package com.electrahub.ocpp.web.dto;

public record SmartChargingCommandResponse(
        String protocol,
        String action,
        Integer profileId,
        String chargerStatus,
        boolean accepted,
        boolean replayed,
        Object payload
) {
    public SmartChargingCommandResponse asReplay() {
        return replayed ? this : new SmartChargingCommandResponse(
                protocol, action, profileId, chargerStatus, accepted, true, payload);
    }
}
