package com.electrahub.ocpp.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OcppTelemetryDispatcherTest {

    @Test
    void coalescesPendingTelemetryAndKeepsConnectorDeliverySerial() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            TaskExecutor executor = workers::execute;
            OcppTelemetryDispatcher dispatcher = new OcppTelemetryDispatcher(executor, new SimpleMeterRegistry());
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch terminalDelivered = new CountDownLatch(1);
            List<String> delivered = new CopyOnWriteArrayList<>();

            dispatcher.dispatchMeterValues("EH-US-CHG-0001", 1, () -> {
                firstStarted.countDown();
                await(releaseFirst);
                delivered.add("meter-1");
            });
            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

            dispatcher.dispatchMeterValues("EH-US-CHG-0001", 1, () -> delivered.add("meter-2"));
            dispatcher.dispatchStatusNotification("EH-US-CHG-0001", 1, () -> delivered.add("charging"));
            dispatcher.dispatchStatusNotification("EH-US-CHG-0001", 1, () -> {
                delivered.add("available");
                terminalDelivered.countDown();
            });

            releaseFirst.countDown();
            assertThat(terminalDelivered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(delivered).containsExactly("meter-1", "meter-2", "available");
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void deliversLifecycleBeforeLaterTelemetryForTheSameConnector() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            OcppTelemetryDispatcher dispatcher = new OcppTelemetryDispatcher(workers::execute, new SimpleMeterRegistry());
            CountDownLatch startRunning = new CountDownLatch(1);
            CountDownLatch allowStartToFinish = new CountDownLatch(1);
            CountDownLatch statusDelivered = new CountDownLatch(1);
            List<String> delivered = new CopyOnWriteArrayList<>();

            dispatcher.dispatchStartTransaction("EH-US-CHG-0001", 1, () -> {
                startRunning.countDown();
                await(allowStartToFinish);
                delivered.add("start");
            });
            assertThat(startRunning.await(1, TimeUnit.SECONDS)).isTrue();
            dispatcher.dispatchStatusNotification("EH-US-CHG-0001", 1, () -> {
                delivered.add("charging");
                statusDelivered.countDown();
            });

            allowStartToFinish.countDown();
            assertThat(statusDelivered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(delivered).containsExactly("start", "charging");
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for telemetry delivery latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for telemetry delivery latch", exception);
        }
    }
}
