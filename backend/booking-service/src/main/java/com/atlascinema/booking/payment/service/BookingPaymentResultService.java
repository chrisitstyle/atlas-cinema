
package com.atlascinema.booking.payment.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingSeat;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.event.BookingSeatHoldsReleaseRequested;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.payment.event.PaymentResultEvent;
import com.atlascinema.booking.payment.repository.PaymentInboxRepository;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import com.atlascinema.booking.service.BookingConfirmationService;
import com.atlascinema.booking.service.RedisSeatHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * Applies payment outcomes to bookings and records compensation requests
 * The booking decision, outbox writes and inbox completion are transactional
 */
@Service
@RequiredArgsConstructor
public class BookingPaymentResultService {

    private final PaymentInboxRepository paymentInboxRepository;
    private final BookingRepository bookingRepository;
    private final ConfirmedSeatRepository confirmedSeatRepository;
    private final BookingConfirmationService bookingConfirmationService;
    private final RedisSeatHoldService redisSeatHoldService;
    private final OutboxEventWriter outboxEventWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public boolean processEvent(UUID eventId) {
        PaymentInboxRepository.LockedPaymentInboxEvent inboxEvent =
                paymentInboxRepository.findByIdForUpdate(eventId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Payment inbox event %s was not found"
                                        .formatted(eventId)));

        if (inboxEvent.processed()) {
            return false;
        }

        PaymentResultEvent paymentEvent = jsonMapper.readValue(inboxEvent.payload(), PaymentResultEvent.class);

        if (!eventId.equals(paymentEvent.eventId())) {
            throw new IllegalStateException("Inbox event id does not match the stored payload");
        }

        // Lock the booking to serialize payment handling with cancellation
        // and expiration
        Booking booking = bookingRepository
                .findByIdForUpdate(paymentEvent.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(paymentEvent.bookingId()));

        validatePaymentAmount(booking, paymentEvent);

        Instant occurredAt = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(occurredAt, clock.getZone());

        switch (paymentEvent.eventType()) {
            case PAYMENT_SUCCEEDED -> handleSuccessfulPayment(
                            booking,
                            paymentEvent,
                            occurredAt,
                            now);

            case PAYMENT_FAILED -> handleFailedPayment(
                            booking,
                            occurredAt,
                            now);
        }

        // Mark the event processed only after all business writes succeed
        // The whole transaction rolls back if any operation fails
        paymentInboxRepository.markAsProcessed(eventId, occurredAt);

        return true;
    }

    private void handleSuccessfulPayment(
            Booking booking,
            PaymentResultEvent paymentEvent,
            Instant occurredAt,
            LocalDateTime now
    ) {
        // a previously confirmed booking may receive the same payment result again
        if (booking.getStatus() == BookingStatus.CONFIRMED
                && paymentEvent.paymentId().equals(
                booking.getConfirmedPaymentId()
        )) {
            return;
        }

        if (!canConfirmBooking(booking, now)) {
            // A different successful payment must be refunded
            // This also covers expired or cancelled bookings
            outboxEventWriter.savePaymentRefundRequested(
                    booking.getId(),
                    paymentEvent.paymentId(),
                    paymentEvent.providerTransactionId(),
                    occurredAt,
                    now
            );

            return;
        }

        // permanently assign the seats to this specific payment
        bookingConfirmationService.confirmPaidBooking(booking.getId(), paymentEvent.paymentId());

        // Redis cleanup occurs only after the transaction commits
        requestSeatHoldRelease(booking);
    }

    private void handleFailedPayment(
            Booking booking,
            Instant occurredAt,
            LocalDateTime now
    ) {
        if (booking.getStatus() != BookingStatus.HELD) {
            return;
        }

        booking.cancelAfterPaymentFailure();

        BookingCancelledEvent cancelledEvent = new BookingCancelledEvent(
                UUID.randomUUID(),
                booking.getId(),
                booking.getCustomerId(),
                booking.getContactEmail(),
                booking.getScreeningId(),
                occurredAt);

        outboxEventWriter.saveCancelled(cancelledEvent, now);

        requestSeatHoldRelease(booking);
    }

    private boolean canConfirmBooking(
            Booking booking,
            LocalDateTime now
    ) {
        if (booking.getStatus() != BookingStatus.HELD
                || booking.getExpiresAt() == null
                || !now.isBefore(booking.getExpiresAt())
                || booking.getSeats().isEmpty()) {
            return false;
        }

        List<UUID> seatIds = booking.getSeats().stream()
                .map(BookingSeat::getSeatId)
                .toList();

        // a permanent seat allocation always wins over a temporary hold
        if (confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                booking.getScreeningId(),
                seatIds
        )) {
            return false;
        }

        // a missing or transferred hold makes confirmation unsafe
        return redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                seatIds,
                booking.getId());
    }

    private void requestSeatHoldRelease(Booking booking) {
        List<UUID> seatIds = booking.getSeats().stream()
                .map(BookingSeat::getSeatId)
                .toList();

        applicationEventPublisher.publishEvent(new BookingSeatHoldsReleaseRequested(
                        booking.getId(),
                        booking.getScreeningId(),
                        seatIds));
    }

    private void validatePaymentAmount(
            Booking booking,
            PaymentResultEvent paymentEvent
    ) {
        if (paymentEvent.amount() == null
                || booking.getTotalPrice().compareTo(
                paymentEvent.amount()
        ) != 0
                || !"PLN".equals(paymentEvent.currencyCode())) {
            throw new IllegalStateException(
                    "Payment %s does not match booking %s"
                            .formatted(
                                    paymentEvent.paymentId(),
                                    booking.getId()));
        }
    }
}
