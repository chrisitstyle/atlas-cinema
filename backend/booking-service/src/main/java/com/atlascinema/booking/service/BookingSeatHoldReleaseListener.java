package com.atlascinema.booking.service;

import com.atlascinema.booking.event.BookingSeatHoldsReleaseRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/*
 * Removes temporary Redis holds only after booking cancellation commits
 * A failed Redis operation leaves cleanup to the existing hold TTL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingSeatHoldReleaseListener {

    private final RedisSeatHoldService redisSeatHoldService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void releaseAfterCommit(BookingSeatHoldsReleaseRequested event) {
        for (UUID seatId : event.seatIds()) {
            try {
                redisSeatHoldService.releaseSeat(
                        event.screeningId(),
                        seatId,
                        event.bookingId()
                );
            } catch (RuntimeException exception) {
                // the booking is already committed, so only log the cleanup failure
                log.warn("Failed to release Redis hold for screeningId={}, seatId={}, bookingId={}",
                        event.screeningId(),
                        seatId,
                        event.bookingId(),
                        exception);
            }
        }
    }
}
