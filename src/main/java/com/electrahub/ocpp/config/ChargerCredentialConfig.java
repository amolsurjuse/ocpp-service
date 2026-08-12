package com.electrahub.ocpp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ChargerCredentialConfig {

    @Bean
    public PasswordEncoder chargerPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
