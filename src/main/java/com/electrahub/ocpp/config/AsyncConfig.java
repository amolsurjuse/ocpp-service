package com.electrahub.ocpp.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncConfig.class);


    /**
     * Executes task executor for `AsyncConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.config`.
     * @return result produced by taskExecutor.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        LOGGER.info(" Entering AsyncConfig#taskExecutor");
        LOGGER.debug(" Entering AsyncConfig#taskExecutor with debug context");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ocpp-async-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "ocppInboundCallExecutor")
    public ThreadPoolTaskExecutor ocppInboundCallExecutor(
            MeterRegistry meterRegistry,
            @Value("${ocpp.inbound.call-executor.core-pool-size:64}") int corePoolSize,
            @Value("${ocpp.inbound.call-executor.max-pool-size:64}") int maxPoolSize,
            @Value("${ocpp.inbound.call-executor.queue-capacity:2000}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ocpp-inbound-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();

        Gauge.builder("ocpp.inbound.calls.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active OCPP inbound callback workers")
                .register(meterRegistry);
        Gauge.builder("ocpp.inbound.calls.queue.depth", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Queued OCPP inbound callbacks")
                .register(meterRegistry);
        Gauge.builder("ocpp.inbound.calls.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("OCPP inbound callback worker pool size")
                .register(meterRegistry);
        return executor;
    }

    @Bean(name = "ocppTelemetryExecutor")
    public ThreadPoolTaskExecutor ocppTelemetryExecutor(
            MeterRegistry meterRegistry,
            @Value("${ocpp.telemetry.executor.core-pool-size:16}") int corePoolSize,
            @Value("${ocpp.telemetry.executor.max-pool-size:16}") int maxPoolSize,
            @Value("${ocpp.telemetry.executor.queue-capacity:1000}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ocpp-telemetry-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();

        Gauge.builder("ocpp.telemetry.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active OCPP telemetry delivery workers")
                .register(meterRegistry);
        Gauge.builder("ocpp.telemetry.queue.depth", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Queued OCPP telemetry delivery workers")
                .register(meterRegistry);
        Gauge.builder("ocpp.telemetry.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("OCPP telemetry delivery worker pool size")
                .register(meterRegistry);
        return executor;
    }

}
