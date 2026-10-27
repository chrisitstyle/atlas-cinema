package com.atlascinema.notification.service;

import com.atlascinema.notification.event.BookingCancelledEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    public void handleBookingCancelled(BookingCancelledEvent bookingCancelledEvent) {
        // the actual email notification will be added here later
        log.info(
                "Handling cancellation notification for booking {} and email {}",
                bookingCancelledEvent.bookingId(),
                bookingCancelledEvent.contactEmail());
    }
}
