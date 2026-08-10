package com.electrahub.ocpp.integration;

public interface ChargingStationTenantResolver {
    String resolveTenant(String chargingStationId);
}
