# ADR-004: OCPP Security Profile 3 and fleet PKI

Status: Accepted for implementation

Date: 2026-08-12

## Context

Production currently enforces OCPP Security Profile 2: WSS at the public edge,
an explicit OCPP subprotocol, and a distinct Basic credential for every charging
station. Traffic reaches the cluster through Cloudflare Tunnel and ingress-nginx.
Consequently, neither ingress-nginx nor `ocpp-service` participates in the public
client TLS handshake. Requiring a client certificate only at ingress-nginx would
authenticate Cloudflare, not the charging station.

Security Profile 3 requires TLS client authentication. It is distinct from the
ISO 15118 contract-certificate and V2G PKI already used by Plug & Charge.

## Decision

### Trust boundary

Cloudflare terminates the public mutual-TLS connection and rejects an untrusted,
expired, or revoked client certificate. A request-header transform MUST first
remove client-supplied `Client-Cert` and `Client-Cert-Chain` headers and then set
them from Cloudflare's verified RFC 9440 certificate fields. Cloudflare Tunnel is
the only public route to the OCPP origin.

`ocpp-service` independently parses the forwarded leaf certificate and requires
an exact active SHA-256 fingerprint binding for the `{chargePointId}` path. It
also verifies certificate validity, acceptable public-key parameters, and that
the certificate subject identity equals the normalized charge-point ID. This
second check limits CA-wide certificate misuse and provides charger-scoped
revocation without waiting for edge propagation.

### Certificate profile

- One private key and leaf certificate per charging station; fleet-shared client
  certificates are prohibited.
- Charging stations generate and retain their own private keys. Private keys are
  never sent to or stored by the CSMS.
- ECDSA uses `secp256r1`; RSA keys, when required for hardware compatibility,
  are at least 2048 bits. New synthetic certificates use ECDSA.
- The certificate Common Name is the normalized charge-point ID. A URI SAN of
  `urn:electrahub:charge-point:<id>` is preferred and becomes authoritative when
  present.
- Certificate validity is bounded by issuer policy. Rotation begins before
  expiry and permits a short, explicitly configured overlap of old and new
  fingerprints.

### Fleet PKI records

The CSMS stores public metadata only: charger ID, SHA-256 fingerprint, serial,
subject, issuer, validity, status, version, and timestamps. The certificate
lifecycle is `ACTIVE -> RETIRED` or `ACTIVE -> REVOKED`. A rotation may leave the
old certificate `RETIRING` for a bounded overlap. Every create, rotate, retire,
and revoke operation writes an append-only audit row with a sanitized actor.

Management endpoints remain internal-token protected. Certificate PEM may enter
only during registration; responses never return private material.

### Admission modes

`OCPP_MTLS_MODE` controls the independent Profile 3 gate:

- `DISABLED`: Profile 2 remains authoritative.
- `AUDIT`: parse and validate a forwarded certificate when present, publish
  outcome metrics, and continue requiring Profile 2 credentials.
- `ENFORCE`: require a valid charger-bound certificate. Basic authentication is
  not required after successful certificate authentication, but may remain
  supplied during the rollback window.

Outcomes use bounded labels only: `accepted`, `missing_certificate`,
`malformed_certificate`, `identity_mismatch`, `unregistered_certificate`,
`inactive_certificate`, `expired_certificate`, `weak_key`, and
`untrusted_forwarder`. Charger IDs and fingerprints are never metric labels.

### Provisioning and rotation

1. Generate the private key and CSR on the charger or controlled simulator.
2. Set the CSR CN/SAN to the authoritative charger ID.
3. Submit the CSR to the approved Cloudflare client-certificate issuer.
4. Install the returned leaf and chain on the charger.
5. Register the public leaf with the CSMS internal certificate endpoint.
6. Reconnect in audit mode and prove certificate/identity binding.
7. For rotation, register the new leaf with a bounded overlap, reconnect, prove
   the new fingerprint, then retire or revoke the old leaf.

Cloudflare API credentials are deployment credentials and are not embedded in
the OCPP runtime. Production bulk issuance is performed by an audited operations
workflow using a least-privilege `SSL and Certificates Write` token.

### Rollout

1. Deploy schema, APIs, validator, and metrics with `OCPP_MTLS_MODE=DISABLED`.
2. Enable `AUDIT`; Profile 2 remains enforced.
3. Issue and register certificates for a non-billing simulator canary.
4. Configure edge mTLS and secure RFC 9440 header forwarding for a dedicated
   OCPP hostname or narrowly scoped OCPP path.
5. Validate valid, missing, malformed, mismatched, expired, revoked, and rotated
   certificate cases plus multi-pod reconnect and rollback.
6. Migrate the fleet in controlled batches. Promote to `ENFORCE` only after the
   complete registered fleet has reconnected successfully during the evidence
   window and the Profile 2 rollback has been exercised.

## Acceptance gates

- No charger private key exists in Git, Kubernetes ConfigMaps, CSMS databases,
  application logs, or API responses.
- Every admitted Profile 3 connection has a valid edge-verified certificate and
  an active exact charger/fingerprint binding in the CSMS.
- Revoked, expired, malformed, unknown, and identity-mismatched certificates are
  rejected in enforcement mode.
- Rotation succeeds without duplicate charger ownership or session loss outside
  the approved reconnect window.
- Profile 2 rollback is configuration-only and tested before fleet enforcement.
- OCA Advanced Security/OCTT evidence and independent laboratory certification
  remain explicit external acceptance artifacts; deployment alone does not claim
  certification.

## Consequences

The CSMS can implement and observe charger-scoped Profile 3 safely behind the
existing Cloudflare architecture. Final public enforcement still depends on an
edge mTLS configuration, per-device certificate installation, full-fleet
reconnect evidence, and external OCPP Advanced Security certification.
