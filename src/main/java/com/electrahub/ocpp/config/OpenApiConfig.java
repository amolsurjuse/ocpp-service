package com.electrahub.ocpp.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiConfig.class);


    /**
     * Executes open api for `OpenApiConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.config`.
     * @return result produced by openAPI.
     */
    @Bean
    public OpenAPI openAPI() {
        LOGGER.info(" Entering OpenApiConfig#openAPI");
        LOGGER.debug(" Entering OpenApiConfig#openAPI with debug context");
        return new OpenAPI()
            .info(new Info()
                .title("OCPP Service API")
                .description("OCPP WebSocket service for ElectraHub CSMS")
                .version("1.0.0"));
    }

}
