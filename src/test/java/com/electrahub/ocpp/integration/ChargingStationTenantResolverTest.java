package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargingStationTenantResolverTest {
    private final StationServiceClient stations = mock(StationServiceClient.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void returnsTheDeploymentTenantOnlyAfterStationIdentityIsRevalidated() throws Exception {
        when(stations.getStation("EH-US-CHG-0003"))
                .thenReturn(json.readTree("{\"chargePointId\":\"EH-US-CHG-0003\"}"));
        ChargingStationTenantResolver resolver = new ChargingStationTenantResolver(stations, "electrahub");

        assertEquals("electrahub", resolver.resolve("EH-US-CHG-0003"));
        verify(stations).getStation("EH-US-CHG-0003");
    }

    @Test
    void failsClosedWhenStationServiceReturnsAnotherStation() throws Exception {
        when(stations.getStation("EH-US-CHG-0003"))
                .thenReturn(json.readTree("{\"chargePointId\":\"EH-US-CHG-9999\"}"));
        ChargingStationTenantResolver resolver = new ChargingStationTenantResolver(stations, "electrahub");

        assertThrows(IllegalStateException.class, () -> resolver.resolve("EH-US-CHG-0003"));
    }

    @Test
    void refusesToStartWithoutOneValidDeploymentTenant() {
        assertThrows(IllegalStateException.class, () -> new ChargingStationTenantResolver(stations, ""));
        assertThrows(IllegalStateException.class, () -> new ChargingStationTenantResolver(stations, "Tenant A"));
    }
}
