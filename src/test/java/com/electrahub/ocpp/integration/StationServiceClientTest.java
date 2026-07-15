package com.electrahub.ocpp.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationServiceClientTest {

    @Test
    void convertsOcppSuspendedEvToStationEnumFormat() {
        assertThat(StationServiceClient.normalizeConnectorStatus("SuspendedEV"))
                .isEqualTo("SUSPENDED_EV");
    }

    @Test
    void convertsOcppSuspendedEvseToStationEnumFormat() {
        assertThat(StationServiceClient.normalizeConnectorStatus("SuspendedEVSE"))
                .isEqualTo("SUSPENDED_EVSE");
    }

    @Test
    void keepsSimpleStatusesCompatible() {
        assertThat(StationServiceClient.normalizeConnectorStatus("Charging"))
                .isEqualTo("CHARGING");
        assertThat(StationServiceClient.normalizeConnectorStatus("AVAILABLE"))
                .isEqualTo("AVAILABLE");
    }
}
