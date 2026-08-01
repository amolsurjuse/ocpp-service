package com.electrahub.ocpp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Service
public class IdTagFingerprintService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public IdTagFingerprintService(
            @Value("${app.security.id-tag-fingerprint-key:${APP_SECURITY_ID_TAG_FINGERPRINT_KEY:}}")
            String fingerprintKey
    ) {
        if (fingerprintKey == null || fingerprintKey.isBlank()) {
            throw new IllegalStateException("APP_SECURITY_ID_TAG_FINGERPRINT_KEY must be configured");
        }
        byte[] keyBytes = fingerprintKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "APP_SECURITY_ID_TAG_FINGERPRINT_KEY must contain at least 32 UTF-8 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    public String fingerprint(String idTag) {
        if (idTag == null || idTag.isBlank()) {
            throw new IllegalArgumentException("idTag is required to calculate its fingerprint");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(idTag.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", impossible);
        }
    }
}
