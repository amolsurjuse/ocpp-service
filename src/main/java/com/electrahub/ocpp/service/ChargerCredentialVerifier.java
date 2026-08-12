package com.electrahub.ocpp.service;

import java.time.Instant;

public interface ChargerCredentialVerifier {

    enum Decision {
        VALID,
        NOT_FOUND,
        DISABLED,
        NOT_YET_VALID,
        EXPIRED,
        INVALID
    }

    Decision verify(String chargePointId, String password, Instant now);
}
