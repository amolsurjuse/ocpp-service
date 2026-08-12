# ADR-003: OCPP production-plane acceptance gates

- Status: Accepted for implementation
- Date: 2026-08-12
- Scope: CPMS hardening Stage 2

## Decision

Stage 2 is promoted through four independent, reversible gates. A later gate may not be used to
hide a failure in an earlier gate.

1. **Credential lifecycle** — replace the shared production charger password with a per-charge-
   point credential registry. Store only adaptive password hashes, support bounded overlap during
   rotation, expose internal create/rotate/disable metadata operations, and append operator audit
   evidence without ever returning a hash or password.
2. **Handshake enforcement** — provision every registered charger, reconnect the complete fleet,
   and require a supported OCPP subprotocol, matching Basic username, valid per-charger password,
   approved origin when supplied, and TLS at the external boundary. `AUDIT` remains the immediate
   configuration-only rollback.
3. **High availability** — run at least two OCPP replicas with Redis ownership/routing enabled,
   a PodDisruptionBudget, topology spread, graceful pre-stop drain, and fail-closed behavior when
   ownership cannot be established. Rollback disables routing and returns to one replica.
4. **Capacity proof** — complete reproducible 5,000- then 10,000-charger reconnect/heartbeat and
   command tests. Publish connection success, reconnect convergence, command success/timeout,
   message latency, pod CPU/memory, Redis latency, callback backlog, and session success evidence.

## Credential model

The normalized WebSocket path identity is the credential key. A credential record contains the
current BCrypt hash, an optional previous hash with an explicit grace deadline, status, validity
window, monotonic version, and audit timestamps. Passwords are accepted only on an internal-token
protected operation, are bounded to 16–256 characters, are immediately hashed, and are never
logged, returned, persisted in plaintext, or attached to metrics.

Rotation is an atomic replacement. The previous hash may remain valid for at most the configured
overlap window so operators can update a charger without a disconnect race. Disablement takes
effect immediately. Handshake lookups fail closed in `ENFORCE`; `AUDIT` records the same decision
without interrupting the existing socket fleet.

The existing shared simulator password is transition-only. It is never accepted in `ENFORCE` and
must be removed after synthetic charger credentials are provisioned.

## Schema ownership

Liquibase becomes the schema authority before credential enforcement. Existing Hibernate-created
tables are baselined with idempotent preconditions, the credential and credential-audit tables are
created by an explicit expand-only change set, and production changes from `ddl-auto=update` to
`ddl-auto=validate`. Rollback disables enforcement and leaves credential evidence intact; it does
not drop tables or hashes.

## Security and abuse controls

- Basic usernames must exactly match the normalized charge-point path identity.
- External stations use `wss://` with TLS 1.2 or newer. Internal cleartext traffic is allowed only
  behind the explicitly trusted cluster boundary and is still reported.
- Failed admissions are rate limited by station identity and remote address using Redis. In
  `ENFORCE`, an unavailable distributed limiter fails closed.
- Metrics contain only bounded outcome/protocol tags. Authorization values and password material
  are forbidden in logs, traces, audits, and error bodies.
- OCPP 2.1 remains rejected; Security Profile 3 certificate authentication remains a later stage.

## Production acceptance and abort thresholds

Each production change requires Argo `Synced/Healthy`, all replicas Ready with no new restarts,
zero authentication regressions for provisioned chargers, no increase in command timeouts or
session failures, successful cross-pod command correlation, and successful drain/reconnect proof.
Abort and roll back configuration when any registered charger cannot reconnect, readiness is lost,
Redis ownership becomes indeterminate, p95 command latency doubles from baseline, error rate
exceeds 1%, or session start/stop success regresses.

The 5k/10k tests must run outside the real hardware identity range, use dedicated synthetic
credentials, ramp connections instead of opening them in one burst, and stop automatically when
the abort thresholds are crossed.
