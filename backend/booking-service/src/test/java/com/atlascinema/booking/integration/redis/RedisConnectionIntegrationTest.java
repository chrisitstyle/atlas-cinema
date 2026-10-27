package com.atlascinema.booking.integration.redis;

import com.atlascinema.booking.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void redis_shouldStoreAndReadValue_whenConnectionIsAvailable() {
        String key = "test:" + UUID.randomUUID();

        String value = UUID.randomUUID().toString();

        try {
            redisTemplate.opsForValue().set(key, value);

            String storedValue = redisTemplate.opsForValue().get(key);

            assertThat(storedValue).isEqualTo(value);

        } finally {
            redisTemplate.delete(key);
        }
    }
}
