package com.electrahub.ocpp.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfig.class);


    /**
     * Executes redis template for `RedisConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.config`.
     * @param connectionFactory input consumed by redisTemplate.
     * @return result produced by redisTemplate.
     */
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        LOGGER.info(" Entering RedisConfig#redisTemplate");
        LOGGER.debug(" Entering RedisConfig#redisTemplate with debug context");
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

}
