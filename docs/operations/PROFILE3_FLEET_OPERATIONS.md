# Profile 3 fleet operations

## Enforcement invariant

Fleet-wide `OCPP_MTLS_MODE=ENFORCE` is permitted only when the internal
readiness endpoint reports `enforcementReady=true`. The endpoint compares the
known OCPP charger inventory with distinct active or retiring certificate
bindings and fails closed when any charger is missing a certificate or when an
admissible certificate expires within 30 days.

This gate is necessary but not sufficient. Operators must also attach the
physical charger installation report, batch reconnect evidence, rotation and
revocation rehearsal, and the configuration-only rollback result.

## Batch enrollment

1. Export the authoritative physical charger inventory and resolve duplicate,
   retired, laboratory, and synthetic identities before issuance.
2. Generate the private key and CSR on each charger or its approved secure
   provisioning appliance. Never export the private key to the CSMS.
3. Issue the leaf through the Cloudflare client-certificate issuer and register
   only its public certificate with the CSMS.
4. Reconnect the charger while fleet mode is `AUDIT`. Require the bounded
   `accepted` handshake counter and a fresh BootNotification and Heartbeat.
5. Enroll in batches with a pause after each batch. Stop if connection count,
   session completion, error rate, or certificate outcome breaches its SLO.

## Rotation rehearsal

Register a new certificate with no more than 24 hours of overlap. Reconnect
using the new leaf and verify its accepted fingerprint. The old record must be
`RETIRING`; revoke it after the new connection remains healthy. A connection
attempt using the old certificate must then fail.

## Rollback rehearsal

Set `OCPP_MTLS_MODE=AUDIT` through GitOps while leaving certificate metadata and
Cloudflare header sanitization intact. Confirm Profile 2 reconnect succeeds.
Rollback never restores a revoked certificate or reintroduces a client-supplied
`Client-Cert` header.

## Evidence command

`Get-FleetProfile3Readiness.ps1` produces the bounded fleet totals used by the
release gate. `-RequireReady` exits non-zero when enforcement is unsafe. Tokens,
private keys, certificate PEM, and charger identifiers are not printed.
