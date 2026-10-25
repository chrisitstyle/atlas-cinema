package com.atlascinema.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> releaseSeatScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        /*
         * Delete the hold only when it still belongs to this booking
         *
         * The comparison and deletion must happen atomically
         * so another booking cannot take over the key between GET and DEL
         */
        script.setScriptText("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end

                return 0
                """);

        script.setResultType(Long.class);

        return script;
    }
}
