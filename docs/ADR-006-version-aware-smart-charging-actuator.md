# ADR-006: Version-Aware Smart-Charging Actuator

## Status

Proposed; design checkpoint for load-management gate 7B

## Context

The existing `/set-charging-profile` endpoint accepts an untyped map, always
builds the OCPP 1.6J `connectorId`/`csChargingProfiles` shape, and reports
transport completion as `status=success` even when the charger payload says
`Rejected`. OCPP 2.0.1 connections are already supported and stored in the
distributed connection registry, so callers must not choose the wire version.

The deployed Go simulator currently answers only `BootNotification`; it cannot
prove the frames emitted by the CSMS for either protocol.

## Decision

### Typed internal contract

Add an internal-token-protected endpoint:

`POST /api/v1/ocpp/commands/{chargePointId}/smart-charging-limit`

```json
{
  "idempotencyKey": "decision-id:connector-id:policy-version",
  "connectorId": 1,
  "transactionId": "optional",
  "profileId": 120045,
  "scheduleId": 220045,
  "stackLevel": 10,
  "limitKw": 42.5,
  "validFrom": "2026-08-07T12:00:00Z",
  "validTo": "2026-08-07T12:02:00Z",
  "purpose": "SESSION_LIMIT"
}
```

All identifiers and numeric bounds are validated. `validTo` must be after
`validFrom`, validity is bounded, and kW is converted to integer watts using
decimal arithmetic. Version comes only from `ConnectionManager`.

Add a corresponding clear endpoint:

`POST /api/v1/ocpp/commands/{chargePointId}/smart-charging-limit/clear`

with idempotency key, connector/EVSE ID, profile ID, stack level, and purpose.

### OCPP 1.6J mapping

`SetChargingProfile` payload:

```json
{
  "connectorId": 1,
  "csChargingProfiles": {
    "chargingProfileId": 120045,
    "transactionId": 123,
    "stackLevel": 10,
    "chargingProfilePurpose": "TxProfile",
    "chargingProfileKind": "Absolute",
    "validFrom": "...Z",
    "validTo": "...Z",
    "chargingSchedule": {
      "startSchedule": "...Z",
      "chargingRateUnit": "W",
      "chargingSchedulePeriod": [{"startPeriod": 0, "limit": 42500.0}]
    }
  }
}
```

`SESSION_LIMIT` uses `TxProfile` only when the transaction ID is a valid OCPP
1.6 integer; otherwise it uses `TxDefaultProfile` and omits transaction ID.
Fallback/site constraints use `TxDefaultProfile`. Clear maps to
`ClearChargingProfile` with the known profile ID and connector criteria.

### OCPP 2.0.1 mapping

`SetChargingProfile` payload:

```json
{
  "evseId": 1,
  "chargingProfile": {
    "id": 120045,
    "stackLevel": 10,
    "chargingProfilePurpose": "TxProfile",
    "chargingProfileKind": "Absolute",
    "transactionId": "optional-string-id",
    "validFrom": "...Z",
    "validTo": "...Z",
    "chargingSchedule": [{
      "id": 220045,
      "startSchedule": "...Z",
      "chargingRateUnit": "W",
      "chargingSchedulePeriod": [{"startPeriod": 0, "limit": 42500.0}]
    }]
  }
}
```

Fallback/site constraints map to `TxDefaultProfile`. Clear uses
`ClearChargingProfile` with the known `chargingProfileId`.

### Result semantics

The typed response contains protocol, action, profile ID, charger status,
`accepted`, `replayed`, and the bounded response payload. Only case-insensitive
`Accepted` is accepted. `Rejected`, `NotSupported`, or a missing status returns
HTTP 200 with `accepted=false`; the coordinator, not HTTP, decides retry class.
Disconnected, routing, timeout, and protocol CALLERROR behavior remains the
existing typed non-2xx error flow.

Metrics count attempts by protocol/action/outcome without charge-point IDs.

### Idempotency

The idempotency key is scoped by charge point and operation. Its request hash is
stored in Redis with a bounded TTL longer than the maximum profile validity.

- a completed identical request replays the stored typed result;
- an in-flight identical request joins the same future on the receiving node;
- the same key with a different request hash returns 409;
- Redis failure is fail-closed for this new actuator endpoint;
- raw legacy endpoints retain their current behavior.

The profile ID is stable, so retrying after an ambiguous network failure
replaces the same profile rather than stacking another limit.

### Compatibility and rollout

- Keep `/set-charging-profile` unchanged for existing admin callers.
- The new endpoint is not called by charger-management gate 7A and cannot alter
  production charging behavior by deployment alone.
- Add mapper and controller tests for exact frames, validation, accepted and
  rejected results, idempotent replay/conflict, timeout, and both protocols.
- Extend the Go simulator to validate required fields, store the last accepted
  profile per charger/profile ID, answer accepted/rejected deterministically,
  and expose test-only state through its existing API. It must support both
  Set/Clear actions without enabling autonomous enforcement.
- Deploy ocpp-service and simulator to development, run exact wire conformance,
  then promote identical images to production. Existing BootNotification,
  remote start/stop, configuration, HA routing, and metering checks must pass.

## Rollback

No existing caller uses the new endpoints. Roll back the services or stop
calling them; legacy raw commands and existing WebSocket sessions remain
compatible. Redis idempotency records expire automatically and contain no
credentials.

## Rejected alternatives

- Letting charger-management construct OCPP JSON duplicates protocol behavior
  outside the transport owner.
- Accepting protocol in the request allows a caller to send the wrong frame to
  a live connection.
- Treating every CALLRESULT as accepted hides charger rejection and makes safe
  closed-loop control impossible.
- Removing the raw endpoint in this gate would break existing admin workflows.
