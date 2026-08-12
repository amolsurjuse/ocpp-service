package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppRemoteStartCommand;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandOutcome;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandState;
import com.electrahub.ocpp.exception.RemoteStartCommandKeyConflictException;
import com.electrahub.ocpp.repository.OcppRemoteStartCommandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class RemoteStartCommandStore {

    static final String EXPIRED_PENDING_REASON =
            "No terminal OCPP response was durably recorded before the command deadline";
    private static final int MAX_REMOTE_START_ID_ALLOCATION_ATTEMPTS = 1024;

    private final OcppRemoteStartCommandRepository repository;
    private final Clock clock;

    @Autowired
    public RemoteStartCommandStore(OcppRemoteStartCommandRepository repository) {
        this(repository, Clock.systemUTC());
    }

    RemoteStartCommandStore(OcppRemoteStartCommandRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Claim claim(CommandSpec spec, Duration responseTimeout) {
        Instant now = clock.instant();
        Duration effectiveTimeout = responseTimeout == null ? Duration.ZERO : responseTimeout;
        Integer remoteStartId = spec.remoteStartId();

        for (int attempt = 0; attempt < MAX_REMOTE_START_ID_ALLOCATION_ATTEMPTS; attempt++) {
            OcppRemoteStartCommand requested = requestedCommand(
                    spec,
                    remoteStartId,
                    now,
                    now.plus(effectiveTimeout)
            );

            try {
                OcppRemoteStartCommand saved = repository.saveAndFlush(requested);
                if (effectiveTimeout.isZero() || effectiveTimeout.isNegative()) {
                    return new Claim(true, expire(saved, now));
                }
                return new Claim(true, saved);
            } catch (DataIntegrityViolationException duplicate) {
                Optional<OcppRemoteStartCommand> existing = repository.findByCommandKey(spec.commandKey());
                if (existing.isPresent()) {
                    requireSameCommand(existing.get(), spec);
                    return new Claim(false, expireIfNecessary(existing.get()));
                }

                if (remoteStartId == null
                        || repository.findByChargePointIdAndRemoteStartId(
                                spec.chargePointId(), remoteStartId).isEmpty()) {
                    throw duplicate;
                }
                remoteStartId = nextRemoteStartId(remoteStartId);
            }
        }

        throw new IllegalStateException(
                "Unable to allocate a unique OCPP 2.0.1 remoteStartId for the charge point");
    }

    public Optional<OcppRemoteStartCommand> find(String commandKey) {
        return repository.findByCommandKey(commandKey).map(this::expireIfNecessary);
    }

    public OcppRemoteStartCommand complete(
            OcppRemoteStartCommand command,
            RemoteStartCommandState state,
            RemoteStartCommandOutcome outcome,
            String responsePayload,
            String failureReason
    ) {
        Instant now = clock.instant();
        repository.transitionPending(
                command.getId(),
                RemoteStartCommandState.PENDING,
                state,
                outcome,
                responsePayload,
                abbreviate(failureReason),
                now,
                now
        );
        return repository.findById(command.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Remote-start command disappeared while recording its outcome"));
    }

    private OcppRemoteStartCommand expireIfNecessary(OcppRemoteStartCommand command) {
        Instant now = clock.instant();
        if (command.getState() != RemoteStartCommandState.PENDING
                || command.getDeadlineAt().isAfter(now)) {
            return command;
        }

        return expire(command, now);
    }

    private OcppRemoteStartCommand expire(OcppRemoteStartCommand command, Instant now) {
        repository.transitionPending(
                command.getId(),
                RemoteStartCommandState.PENDING,
                RemoteStartCommandState.UNKNOWN,
                null,
                null,
                EXPIRED_PENDING_REASON,
                now,
                now
        );
        return repository.findById(command.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Remote-start command disappeared while expiring its pending state"));
    }

    private void requireSameCommand(OcppRemoteStartCommand existing, CommandSpec requested) {
        if (!Objects.equals(existing.getChargePointId(), requested.chargePointId())
                || !Objects.equals(existing.getIdTagFingerprint(), requested.idTagFingerprint())
                || !Objects.equals(existing.getConnectorId(), requested.connectorId())
                || !Objects.equals(existing.getCorrelationId(), requested.correlationId())) {
            throw new RemoteStartCommandKeyConflictException(
                    "commandKey is already bound to a different remote-start request");
        }
    }

    private OcppRemoteStartCommand requestedCommand(
            CommandSpec spec,
            Integer remoteStartId,
            Instant createdAt,
            Instant deadlineAt
    ) {
        return OcppRemoteStartCommand.builder()
                .commandKey(spec.commandKey())
                .correlationId(spec.correlationId())
                .chargePointId(spec.chargePointId())
                .idTagFingerprint(spec.idTagFingerprint())
                .connectorId(spec.connectorId())
                .protocol(spec.protocol())
                .messageId(spec.messageId())
                .remoteStartId(remoteStartId)
                .state(RemoteStartCommandState.PENDING)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .deadlineAt(deadlineAt)
                .build();
    }

    private int nextRemoteStartId(int current) {
        return current == Integer.MAX_VALUE ? 1 : current + 1;
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    public record CommandSpec(
            String commandKey,
            String correlationId,
            String chargePointId,
            String idTagFingerprint,
            Integer connectorId,
            String protocol,
            String messageId,
            Integer remoteStartId
    ) {
    }

    public record Claim(boolean claimed, OcppRemoteStartCommand command) {
    }
}
