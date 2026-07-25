package com.electrahub.ocpp.websocket;

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

class OcppInboundCallDispatcherTest {

    @Test
    void preservesPerChargePointOrderWithoutBlockingOtherChargePoints() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            TaskExecutor executor = workers::execute;
            OcppInboundCallDispatcher dispatcher = new OcppInboundCallDispatcher(executor, new SimpleMeterRegistry());
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondCompleted = new CountDownLatch(1);
            CountDownLatch otherCompleted = new CountDownLatch(1);
            List<String> executionOrder = new CopyOnWriteArrayList<>();

            assertThat(dispatcher.dispatch("EH-1", () -> {
                firstStarted.countDown();
                await(releaseFirst);
                executionOrder.add("first");
            }, () -> { })).isTrue();
            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThat(dispatcher.dispatch("EH-1", () -> {
                executionOrder.add("second");
                secondCompleted.countDown();
            }, () -> { })).isTrue();
            assertThat(dispatcher.dispatch("EH-2", () -> {
                executionOrder.add("other");
                otherCompleted.countDown();
            }, () -> { })).isTrue();

            assertThat(otherCompleted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(secondCompleted.getCount()).isOne();

            releaseFirst.countDown();
            assertThat(secondCompleted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executionOrder.indexOf("first")).isLessThan(executionOrder.indexOf("second"));
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", exception);
        }
    }
}
