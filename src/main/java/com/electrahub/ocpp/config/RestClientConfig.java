package com.electrahub.ocpp.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientConfig.class);


    /**
     * Executes rest client builder for `RestClientConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.config`.
     * @return result produced by restClientBuilder.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        LOGGER.info("CODEx_ENTRY_LOG: Entering RestClientConfig#restClientBuilder");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering RestClientConfig#restClientBuilder with debug context");
        return RestClient.builder();
    }
}
