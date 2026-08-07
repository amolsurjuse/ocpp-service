package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppRemoteStartCommand;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandOutcome;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandState;
import com.electrahub.ocpp.exception.RemoteStartCommandKeyConflictException;
import com.electrahub.ocpp.repository.OcppRemoteStartCommandRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RemoteStartCommandStoreTest {

    @Autowired
    private RemoteStartCommandStore store;

    @Autowired
    private OcppRemoteStartCommandRepository repository;

    @Autowired
    private IdTagFingerprintService idTagFingerprints;

    private ExecutorService executor;

    @BeforeEach
    void clearCommands() {
        repository.deleteAll();
    }

    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentClaimsPersistOneCommandAcrossCallers() throws Exception {
        int callers = 12;
        executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> claims = new ArrayList<>();

        for (int index = 0; index < callers; index++) {
            claims.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.claim(spec("remote-start:session-1", UUID.randomUUID().toString()), Duration.ofSeconds(30))
                        .claimed();
            }));
        }

        ready.await();
        start.countDown();

        int claimed = 0;
        for (Future<Boolean> result : claims) {
            if (result.get()) {
                claimed++;
            }
        }

        assertThat(claimed).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByCommandKey("remote-start:session-1"))
                .get()
                .extracting(OcppRemoteStartCommand::getState)
                .isEqualTo(RemoteStartCommandState.PENDING);
    }

    @Test
    void expiredPendingClaimBecomesUnknownAndCannotLaterBecomeAccepted() {
        RemoteStartCommandStore.Claim claim = store.claim(
                spec("remote-start:crash-window", UUID.randomUUID().toString()),
                Duration.ofDays(-1)
        );

        assertThat(claim.command().getDeadlineAt()).isBefore(Instant.now());

        OcppRemoteStartCommand unknown = store.find("remote-start:crash-window").orElseThrow();
        assertThat(unknown.getState()).isEqualTo(RemoteStartCommandState.UNKNOWN);
        assertThat(unknown.getOutcome()).isNull();
        assertThat(unknown.getFailureReason()).isEqualTo(RemoteStartCommandStore.EXPIRED_PENDING_REASON);

        OcppRemoteStartCommand afterLateAcceptance = store.complete(
                claim.command(),
                RemoteStartCommandState.TERMINAL,
                RemoteStartCommandOutcome.ACCEPTED,
                "{\"status\":\"Accepted\"}",
                null
        );

        assertThat(afterLateAcceptance.getState()).isEqualTo(RemoteStartCommandState.UNKNOWN);
        assertThat(afterLateAcceptance.getOutcome()).isNull();
    }

    @Test
    void commandKeyCannotBeReusedForDifferentRemoteStartParameters() {
        store.claim(spec("remote-start:session-2", "message-1"), Duration.ofSeconds(30));

        RemoteStartCommandStore.CommandSpec conflicting = new RemoteStartCommandStore.CommandSpec(
                "remote-start:session-2",
                "different-session",
                "CP-2",
                idTagFingerprints.fingerprint("RFID-DIFFERENT"),
                2,
                "OCPP16J",
                "message-2",
                null
        );

        assertThatThrownBy(() -> store.claim(conflicting, Duration.ofSeconds(30)))
                .isInstanceOf(RemoteStartCommandKeyConflictException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void persistedCommandContainsOnlyAKeyedIdTagFingerprint() {
        store.claim(spec("remote-start:fingerprint", "message-fingerprint"), Duration.ofSeconds(30));

        OcppRemoteStartCommand persisted = repository.findByCommandKey("remote-start:fingerprint")
                .orElseThrow();

        assertThat(persisted.getIdTagFingerprint())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .doesNotContain("RFID-1");
    }

    @Test
    void sameCommandKeyWithDifferentIdTagFingerprintConflicts() {
        store.claim(spec("remote-start:tag-conflict", "message-tag-1"), Duration.ofSeconds(30));
        RemoteStartCommandStore.CommandSpec differentTag = new RemoteStartCommandStore.CommandSpec(
                "remote-start:tag-conflict",
                "session-1",
                "CP-1",
                idTagFingerprints.fingerprint("RFID-2"),
                1,
                "OCPP201",
                "message-tag-2",
                RemoteCommandService.deterministicRemoteStartId("remote-start:tag-conflict")
        );

        assertThatThrownBy(() -> store.claim(differentTag, Duration.ofSeconds(30)))
                .isInstanceOf(RemoteStartCommandKeyConflictException.class);
    }

    @Test
    void remoteStartIdCollisionAllocatesNextIdForTheSameChargePoint() {
        int forcedCollision = 123_456;
        RemoteStartCommandStore.CommandSpec first = specWithRemoteStartId(
                "remote-start:collision-one", "message-collision-1", forcedCollision);
        RemoteStartCommandStore.CommandSpec second = specWithRemoteStartId(
                "remote-start:collision-two", "message-collision-2", forcedCollision);

        RemoteStartCommandStore.Claim firstClaim = store.claim(first, Duration.ofSeconds(30));
        RemoteStartCommandStore.Claim secondClaim = store.claim(second, Duration.ofSeconds(30));

        assertThat(firstClaim.command().getRemoteStartId()).isEqualTo(forcedCollision);
        assertThat(secondClaim.command().getRemoteStartId()).isEqualTo(forcedCollision + 1);
        assertThat(repository.count()).isEqualTo(2);
    }

    private RemoteStartCommandStore.CommandSpec spec(String commandKey, String messageId) {
        return new RemoteStartCommandStore.CommandSpec(
                commandKey,
                "session-1",
                "CP-1",
                idTagFingerprints.fingerprint("RFID-1"),
                1,
                "OCPP201",
                messageId,
                RemoteCommandService.deterministicRemoteStartId(commandKey)
        );
    }

    private RemoteStartCommandStore.CommandSpec specWithRemoteStartId(
            String commandKey,
            String messageId,
            int remoteStartId
    ) {
        return new RemoteStartCommandStore.CommandSpec(
                commandKey,
                "session-1",
                "CP-1",
                idTagFingerprints.fingerprint("RFID-1"),
                1,
                "OCPP201",
                messageId,
                remoteStartId
        );
    }
}
