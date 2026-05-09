package com.electrahub.ocpp.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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

}
