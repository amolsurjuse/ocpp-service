package com.electrahub.ocpp.service;

import java.security.cert.X509Certificate;
import java.time.Instant;

public interface ChargerCertificateVerifier {
    enum Decision {
        VALID,
        MALFORMED,
        IDENTITY_MISMATCH,
        UNREGISTERED,
        INACTIVE,
        NOT_YET_VALID,
        EXPIRED,
        WEAK_KEY
    }

    Decision verify(String chargePointId, X509Certificate certificate, Instant now);
}
