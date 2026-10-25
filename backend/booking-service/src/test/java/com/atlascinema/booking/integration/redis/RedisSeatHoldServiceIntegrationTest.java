package com.atlascinema.booking.integration.redis;

import com.atlascinema.booking.service.RedisSeatHoldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisSeatHoldServiceIntegrationTest {

    @Autowired
    private RedisSeatHoldService redisSeatHoldService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void holdSeat_shouldAcquireHold_whenSeatIsAvailable() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String key = "seat-hold:" + screeningId + ":" + seatId;

        try {
            boolean acquired = redisSeatHoldService.holdSeat(
                    screeningId,
                    seatId,
                    bookingId,
                    Duration.ofMinutes(10));

            assertThat(acquired).isTrue();

            assertThat(redisTemplate.opsForValue().get(key))
                    .isEqualTo(bookingId.toString());

        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void holdSeat_shouldNotAcquireHold_whenSeatIsAlreadyHeld() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        UUID firstBookingId = UUID.randomUUID();

        UUID secondBookingId = UUID.randomUUID();

        String key = "seat-hold:" + screeningId + ":" + seatId;

        try {
            boolean firstAcquired = redisSeatHoldService.holdSeat(
                    screeningId,
                    seatId,
                    firstBookingId,
                    Duration.ofMinutes(10));

            boolean secondAcquired = redisSeatHoldService.holdSeat(
                    screeningId,
                    seatId,
                    secondBookingId,
                    Duration.ofMinutes(10));

            assertThat(firstAcquired).isTrue();

            assertThat(secondAcquired).isFalse();

            assertThat(redisTemplate.opsForValue().get(key))
                    .isEqualTo(firstBookingId.toString());

        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void holdSeat_shouldSetTtl_whenSeatIsAcquired() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        try {
            redisSeatHoldService.holdSeat(
                    screeningId,
                    seatId,
                    bookingId,
                    Duration.ofMinutes(10)
            );

            Long ttlSeconds =
                    redisTemplate.getExpire(key);

            assertThat(ttlSeconds)
                    .isNotNull();

            Duration ttl =
                    Duration.ofSeconds(ttlSeconds);

            // the hold must expire automatically
            assertThat(ttl)
                    .isPositive();

            assertThat(ttl)
                    .isLessThanOrEqualTo(
                            Duration.ofMinutes(10)
                    );

        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void holdSeat_shouldExpireHold_whenTtlPasses() throws InterruptedException {

        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        redisSeatHoldService.holdSeat(
                screeningId,
                seatId,
                bookingId,
                Duration.ofMillis(200));

        assertThat(redisTemplate.hasKey(key)).isTrue();

        Thread.sleep(300);

        // redis removes the key automatically after the TTL expires
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void holdSeat_shouldAllowOnlyOneBooking_whenRequestsAreConcurrent() throws Exception {

        int attempts = 100;

        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(attempts);

        CountDownLatch ready = new CountDownLatch(attempts);

        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(
                        executor.submit(() -> {
                            UUID bookingId =
                                    UUID.randomUUID();

                            // wait until all threads are ready before starting the race
                            ready.countDown();
                            start.await();

                            return redisSeatHoldService.holdSeat(
                                    screeningId,
                                    seatId,
                                    bookingId,
                                    Duration.ofMinutes(10));
                        }));
            }

            // make sure most threads are ready before releasing them
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            int acquired = 0;
            int rejected = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(
                        30,
                        TimeUnit.SECONDS
                )) {
                    acquired++;
                } else {
                    rejected++;
                }
            }

            // SET NX must allow exactly one booking to acquire the seat
            assertThat(acquired)
                    .isEqualTo(1);

            assertThat(rejected)
                    .isEqualTo(attempts - 1);

            assertThat(redisTemplate.hasKey(key))
                    .isTrue();

        } finally {
            redisTemplate.delete(key);

            executor.shutdownNow();
        }
    }

    @Test
    void releaseSeat_shouldReleaseHold_whenBookingOwnsSeat() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        try {
            redisSeatHoldService.holdSeat(
                    screeningId,
                    seatId,
                    bookingId,
                    Duration.ofMinutes(10));

            boolean released = redisSeatHoldService.releaseSeat(
                    screeningId,
                    seatId,
                    bookingId);

            assertThat(released).isTrue();

            assertThat(redisTemplate.hasKey(key)).isFalse();

        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void releaseSeat_shouldNotReleaseHold_whenBookingDoesNotOwnSeat() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        UUID ownerBookingId = UUID.randomUUID();

        UUID otherBookingId = UUID.randomUUID();

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        try {
            redisSeatHoldService.holdSeat(
                    screeningId,
                    seatId,
                    ownerBookingId,
                    Duration.ofMinutes(10));

            boolean released = redisSeatHoldService.releaseSeat(
                    screeningId,
                    seatId,
                    otherBookingId);

            assertThat(released).isFalse();

            //  stale or unrelated booking must not remove someone else's hold
            assertThat(redisTemplate.opsForValue().get(key))
                    .isEqualTo(ownerBookingId.toString());

        } finally {
            redisTemplate.delete(key);
        }
    }
}