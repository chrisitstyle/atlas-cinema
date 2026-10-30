
package com.atlascinema.booking.payment.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingSeat;
import com.atlascinema.booking.domain.BookingStatus;
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
 * A failed payment attempt does not cancel a booking awaiting other attempts
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

        PaymentResultEvent paymentEvent = jsonMapper.readValue(
                inboxEvent.payload(),
                PaymentResultEvent.class);

        if (!eventId.equals(paymentEvent.eventId())) {
            throw new IllegalStateException("Inbox event id does not match the stored payload");
        }

        // lock the booking to serialize payment handling with other state changes
        Booking booking = bookingRepository
                .findByIdForUpdate(paymentEvent.bookingId())
                .orElseThrow(() ->
                        new BookingNotFoundException(paymentEvent.bookingId()));

        validatePaymentAmount(booking, paymentEvent);

        Instant processedAt = clock.instant();

        LocalDateTime now = LocalDateTime.ofInstant(processedAt, clock.getZone());

        switch (paymentEvent.eventType()) {
            case PAYMENT_SUCCEEDED -> handleSuccessfulPayment(
                            booking,
                            paymentEvent,
                            processedAt,
                            now);

            case PAYMENT_FAILED -> {
                // The failed attempt is recorded in the inbox
                // The booking stays HELD so other attempts can still succeed
                // BookingExpirationScheduler handles expiration when TTL ends
            }
        }

        // The business decision and inbox completion commit together
        paymentInboxRepository.markAsProcessed(eventId, processedAt);

        return true;
    }

    private void handleSuccessfulPayment(
            Booking booking,
            PaymentResultEvent paymentEvent,
            Instant occurredAt,
            LocalDateTime now
    ) {
        // the payment that already confirmed this booking needs no refund
        if (booking.getStatus() == BookingStatus.CONFIRMED
                && paymentEvent.paymentId().equals(
                booking.getConfirmedPaymentId()
        )) {
            return;
        }

        if (!canConfirmBooking(booking, now)) {
            // an additional or late successful payment must be compensated
            outboxEventWriter.savePaymentRefundRequested(
                    booking.getId(),
                    paymentEvent.paymentId(),
                    paymentEvent.providerTransactionId(),
                    occurredAt,
                    now);

            return;
        }

        bookingConfirmationService.confirmPaidBooking(
                booking.getId(),
                paymentEvent.paymentId()
        );

        // release temporary holds only after the database transaction commits
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

        // a permanently sold seat cannot be sold again
        if (confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                booking.getScreeningId(),
                seatIds
        )) {
            return false;
        }

        // all temporary holds must still belong to this booking
        return redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                seatIds,
                booking.getId());
    }

    private void requestSeatHoldRelease(Booking booking) {
        List<UUID> seatIds = booking.getSeats().stream()
                .map(BookingSeat::getSeatId)
                .toList();

        applicationEventPublisher.publishEvent(
                new BookingSeatHoldsReleaseRequested(
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
