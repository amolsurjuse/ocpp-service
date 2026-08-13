package com.electrahub.ocpp.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ForwardedClientCertificateParser {

    public X509Certificate parse(String header) {
        if (header == null || header.isBlank() || header.length() > 16384) {
            throw new IllegalArgumentException("Client certificate header is missing or too large");
        }
        String value = header.trim();
        byte[] encoded;
        try {
            if (value.startsWith(":") && value.endsWith(":") && value.length() > 2) {
                encoded = Base64.getDecoder().decode(value.substring(1, value.length() - 1));
            } else if (value.contains("-----BEGIN CERTIFICATE-----")) {
                encoded = value.getBytes(StandardCharsets.US_ASCII);
            } else {
                throw new IllegalArgumentException("Client certificate must use RFC 9440 binary encoding");
            }
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded));
        } catch (CertificateException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Client certificate could not be parsed", exception);
        }
    }
}
