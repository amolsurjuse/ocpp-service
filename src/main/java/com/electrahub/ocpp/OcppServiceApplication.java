package com.electrahub.ocpp;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class OcppServiceApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppServiceApplication.class);


    /**
     * Executes main for `OcppServiceApplication`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp`.
     * @param args input consumed by main.
     */
    public static void main(String[] args) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering OcppServiceApplication#main");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering OcppServiceApplication#main with debug context");
        SpringApplication.run(OcppServiceApplication.class, args);
    }

}
