# Security Profile 3 fleet PKI runbook

This runbook implements ADR-004. It handles charging-station TLS client
certificates only; it does not handle ISO 15118 contract certificates.

## Preconditions

- The OCPP service is deployed with the certificate schema and is in `AUDIT`.
- A local port-forward exposes the internal OCPP service API to `127.0.0.1:8082`.
- `CLOUDFLARE_API_TOKEN` has only `SSL and Certificates Write` for the ElectraHub
  zone. `CLOUDFLARE_ZONE_ID` and `APP_SECURITY_INTERNAL_TOKEN` are set without
  printing them.
- The material directory is encrypted, access-controlled, excluded from Git,
  and backed up through the approved secret-custody process.

## Issue or rotate

```powershell
.\scripts\pki\New-ChargerCertificateRequest.ps1 `
  -ChargePointId EH-CANARY-001 -OutputDirectory C:\secure\ocpp-pki\EH-CANARY-001

.\scripts\pki\Issue-CloudflareChargerCertificate.ps1 `
  -ChargePointId EH-CANARY-001 `
  -MaterialDirectory C:\secure\ocpp-pki\EH-CANARY-001 `
  -RotationOverlapSeconds 3600
```

Install `<id>.key` and `<id>.crt` through the charger secure-provisioning
channel. For the simulator, mount them read-only under the configured
`OCPP_MTLS_CERTIFICATE_DIR`. Reconnect and verify the `accepted` mTLS metric
before retiring the old certificate.

## Revoke

```powershell
.\scripts\pki\Revoke-ChargerCertificate.ps1 `
  -ChargePointId EH-CANARY-001 `
  -MetadataPath C:\secure\ocpp-pki\EH-CANARY-001\EH-CANARY-001.metadata.json
```

Revocation is deliberately confirmed because it disconnects and blocks the
charger. Verify rejection at both Cloudflare and the CSMS, then preserve the
audit evidence according to the security retention policy.

## Promotion gate

Do not enable `ENFORCE` until Cloudflare strips client-supplied RFC 9440 headers,
sets them only from a successfully verified client certificate, blocks invalid
certificates, and the full registered fleet has passed a reconnect window.
Profile 2 stays configured until the rollback drill has passed.
