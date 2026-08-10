# ADR-010: authoritative tenant resolution for Plug & Charge

## Status

Accepted. Deployment remains dark with certificate installation disabled.

## Decision

Before forwarding `Get15118EVCertificate`, OCPP resolves the charge point through
charger-management's internal ownership endpoint. That endpoint reads canonical
inventory hierarchy and returns the tenant protected by the existing internal
workload token.

- Tenant data from an OCPP payload is never accepted.
- The former environment-wide tenant fallback is removed from the PnC call.
- Unknown, ambiguous, unauthorized, malformed, or unavailable ownership lookups
  fail closed and do not call the PnC platform.
- The resolved tenant is placed in both the signed identity and tenant header;
  PnC already requires them to match.
- Resolution is performed only for the disabled-by-default certificate action.
  It does not change normal BootNotification, authorization, transaction, meter,
  or status traffic.

The current shared workload token is a compatibility boundary. Workload mTLS or
asymmetric service identity remains required before cross-cluster expansion.
