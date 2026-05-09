package com.electrahub.ocpp.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonConfig.class);


    /**
     * Executes object mapper for `JacksonConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.config`.
     * @return result produced by objectMapper.
     */
    @Bean
    public ObjectMapper objectMapper() {
        LOGGER.info(" Entering JacksonConfig#objectMapper");
        LOGGER.debug(" Entering JacksonConfig#objectMapper with debug context");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

}
