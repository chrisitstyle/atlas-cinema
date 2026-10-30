package com.atlascinema.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisSeatHoldService {

    private static final String KEY_PREFIX = "seat-hold:";

    // verify every seat owner atomically in Redis
    private static final RedisScript<Long> VERIFY_SEAT_HOLDS_SCRIPT = RedisScript.of("""
            for index = 1, #KEYS do
                if redis.call('GET', KEYS[index]) ~= ARGV[1] then
                    return 0
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> releaseSeatScript;

    public boolean holdSeat(
            UUID screeningId,
            UUID seatId,
            UUID bookingId,
            Duration ttl
    ) {
        String key = buildKey(
                screeningId,
                seatId);

        /* setIfAbsent maps to Redis SET ... NX
         The check and the write happen atomically, so two bookings
         cannot acquire the same seat at the same time*/
        Boolean acquired = redisTemplate
                .opsForValue()
                .setIfAbsent(
                        key,
                        bookingId.toString(),
                        ttl);

        // spring returns Boolean here, so this also safely handles null
        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseSeat(
            UUID screeningId,
            UUID seatId,
            UUID bookingId
    ) {
        String key = buildKey(
                screeningId,
                seatId);

        /* lua script checks the owner and deletes the hold
         as one atomic Redis operation */
        Long deleted = redisTemplate.execute(
                releaseSeatScript,
                List.of(key),
                bookingId.toString());

        return Long.valueOf(1L)
                .equals(deleted);
    }

    public boolean areSeatsHeldByBooking(
            UUID screeningId,
            List<UUID> seatIds,
            UUID bookingId
    ) {
        if (seatIds.isEmpty()) {
            return false;
        }

        List<String> seatKeys = seatIds.stream()
                .map(seatId -> buildKey(screeningId, seatId))
                .toList();

        Long allSeatsOwned = redisTemplate.execute(
                VERIFY_SEAT_HOLDS_SCRIPT,
                seatKeys,
                bookingId.toString());

        return Long.valueOf(1L).equals(allSeatsOwned);
    }

    private String buildKey(
            UUID screeningId,
            UUID seatId
    ) {
        /* seat is unique only within a specific screening
         the same physical seat can be held for different screenings*/
        return KEY_PREFIX
                + screeningId
                + ":"
                + seatId;
    }
}