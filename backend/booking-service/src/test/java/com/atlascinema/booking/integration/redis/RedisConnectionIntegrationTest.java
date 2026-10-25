package com.atlascinema.booking.integration.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConnectionIntegrationTest {

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
