package com.electrahub.ocpp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdTagFingerprintServiceTest {

    private static final String KEY_ONE = "test-only-fingerprint-key-one-32-bytes";
    private static final String KEY_TWO = "test-only-fingerprint-key-two-32-bytes";

    @Test
    void fingerprintIsStableKeyedAndDoesNotContainTheRawTag() {
        IdTagFingerprintService first = new IdTagFingerprintService(KEY_ONE);
        IdTagFingerprintService second = new IdTagFingerprintService(KEY_TWO);

        String fingerprint = first.fingerprint("RFID-1234");

        assertThat(fingerprint)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .doesNotContain("RFID-1234")
                .isEqualTo(first.fingerprint("RFID-1234"))
                .isNotEqualTo(first.fingerprint("RFID-5678"))
                .isNotEqualTo(second.fingerprint("RFID-1234"));
    }

    @Test
    void weakOrMissingFingerprintKeysFailClosed() {
        assertThatThrownBy(() -> new IdTagFingerprintService("short-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 UTF-8 bytes");
        assertThatThrownBy(() -> new IdTagFingerprintService(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
    }
}
