package com.atlascinema.notification.service;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.event.BookingExpiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/*
 * Handles notification actions for supported booking events
 */
@Service
@Slf4j
public class NotificationService {

    public void handleBookingCancelled(BookingCancelledEvent bookingCancelledEvent) {
        // the actual email notification will be added here later
        log.info("Handling cancellation notification for booking {} and email {}",
                bookingCancelledEvent.bookingId(),
                bookingCancelledEvent.contactEmail());
    }

    public void handleBookingExpired(BookingExpiredEvent bookingExpiredEvent) {

          // the actual email notification will be added here later

        log.info(
                "Handling expiration notification for booking {} and email {}",
                bookingExpiredEvent.bookingId(),
                bookingExpiredEvent.contactEmail()
        );
    }
}
