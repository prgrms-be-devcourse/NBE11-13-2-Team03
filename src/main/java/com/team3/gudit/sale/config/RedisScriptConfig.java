package com.team3.gudit.sale.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> stockDecrementScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(
                new ResourceScriptSource(
                        new ClassPathResource(
                                "scripts/stock_decrement.lua"
                        )
                )
        );
        redisScript.setResultType(Long.class);

        return redisScript;
    }

    @Bean
    public DefaultRedisScript<Long> stockRestoreScript() {
        DefaultRedisScript<Long> redisScript =
                new DefaultRedisScript<>();

        redisScript.setScriptSource(
                new ResourceScriptSource(
                        new ClassPathResource(
                                "scripts/stock_restore.lua"
                        )
                )
        );

        redisScript.setResultType(Long.class);

        return redisScript;
    }
}
