
package com.atlascinema.payment.service;

import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.exception.BookingNotPayableException;
import com.atlascinema.payment.exception.PaymentIdempotencyConflictException;
import com.atlascinema.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


// tests payment initiation and recovery from concurrent insert conflicts
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID BOOKING_ID = UUID.fromString("f5fd3520-c844-4d16-bde3-360e87648dd2");

    private static final UUID ANOTHER_BOOKING_ID = UUID.fromString("3c39c9e7-a548-42c3-97ca-6893fe878612");

    private static final BigDecimal TOTAL_PRICE = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final String IDEMPOTENCY_KEY = "payment-request-123";

    private static final Instant INITIATED_AT = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCreationTransactionService paymentCreationTransactionService;

    @Mock
    private BookingPaymentValidationService bookingPaymentValidationService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                paymentCreationTransactionService,
                bookingPaymentValidationService);
    }

    @Test
    void initiatePaymentForBooking_shouldCreatePayment_whenBookingIsPayable() {
        Payment newlyCreatedPayment = createPendingPayment(BOOKING_ID);

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenReturn(createPayableBookingDetails());

        when(paymentCreationTransactionService.createPendingPayment(
                BOOKING_ID,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY
        )).thenReturn(newlyCreatedPayment);

        Payment returnedPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY);

        assertThat(returnedPayment).isSameAs(newlyCreatedPayment);

        verify(paymentCreationTransactionService).createPendingPayment(
                BOOKING_ID,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY);
    }

    @Test
    void initiatePaymentForBooking_shouldReturnExistingPayment_whenRequestIsRepeated() {
        Payment existingPayment = createPendingPayment(BOOKING_ID);

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingPayment));

        Payment returnedPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY);

        assertThat(returnedPayment).isSameAs(existingPayment);

        // repeated requests do not revalidate an already initiated payment
        verifyNoInteractions(bookingPaymentValidationService);
        verifyNoInteractions(paymentCreationTransactionService);
    }

    @Test
    void initiatePaymentForBooking_shouldRejectRequest_whenKeyBelongsToAnotherBooking() {
        Payment existingPayment = createPendingPayment(ANOTHER_BOOKING_ID);

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        BOOKING_ID,
                        IDEMPOTENCY_KEY)
        ).isInstanceOf(PaymentIdempotencyConflictException.class);

        verifyNoInteractions(bookingPaymentValidationService);
        verifyNoInteractions(paymentCreationTransactionService);
    }

    @Test
    void initiatePaymentForBooking_shouldRejectRequest_whenBookingIsNotPayable() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenThrow(new BookingNotPayableException(
                        BOOKING_ID,
                        "booking hold has expired"));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        BOOKING_ID,
                        IDEMPOTENCY_KEY)
        ).isInstanceOf(BookingNotPayableException.class);

        verifyNoInteractions(paymentCreationTransactionService);
    }

    @Test
    void initiatePaymentForBooking_shouldRecoverPayment_whenConcurrentInsertUsesSameDetails() {
        Payment concurrentlyCreatedPayment = createPendingPayment(BOOKING_ID);

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(concurrentlyCreatedPayment)
                );

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenReturn(createPayableBookingDetails());

        when(paymentCreationTransactionService.createPendingPayment(
                BOOKING_ID,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY
        )).thenThrow(new DataIntegrityViolationException(
                "Duplicate idempotency key"));

        Payment returnedPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY);

        assertThat(returnedPayment).isSameAs(concurrentlyCreatedPayment);

        verify(paymentRepository, times(2))
                .findByIdempotencyKey(IDEMPOTENCY_KEY);
    }

    @Test
    void initiatePaymentForBooking_shouldRejectRequest_whenConcurrentInsertHasDifferentBooking() {
        Payment concurrentlyCreatedPayment = createPendingPayment(ANOTHER_BOOKING_ID);

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(concurrentlyCreatedPayment));

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenReturn(createPayableBookingDetails());

        when(paymentCreationTransactionService.createPendingPayment(
                BOOKING_ID,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY
        )).thenThrow(new DataIntegrityViolationException(
                "Duplicate idempotency key"));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        BOOKING_ID,
                        IDEMPOTENCY_KEY)
        ).isInstanceOf(PaymentIdempotencyConflictException.class);
    }

    @Test
    void initiatePaymentForBooking_shouldRejectRequest_whenConcurrentInsertHasDifferentPrice() {
        Payment concurrentlyCreatedPayment = Payment.initiateForBooking(
                BOOKING_ID,
                new BigDecimal("59.80"),
                CURRENCY_CODE,
                IDEMPOTENCY_KEY,
                INITIATED_AT);

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(),
                        Optional.of(concurrentlyCreatedPayment));

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenReturn(createPayableBookingDetails());

        when(paymentCreationTransactionService.createPendingPayment(
                BOOKING_ID,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY
        )).thenThrow(new DataIntegrityViolationException(
                "Duplicate idempotency key"));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        BOOKING_ID,
                        IDEMPOTENCY_KEY)
        ).isInstanceOf(PaymentIdempotencyConflictException.class);
    }

    @Test
    void initiatePaymentForBooking_shouldPropagateDatabaseError_whenPaymentCannotBeRecovered() {
        DataIntegrityViolationException databaseException = new DataIntegrityViolationException("Unrelated database error");

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenReturn(createPayableBookingDetails());

        when(paymentCreationTransactionService.createPendingPayment(
                BOOKING_ID,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY
        )).thenThrow(databaseException);

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        BOOKING_ID,
                        IDEMPOTENCY_KEY)
        ).isSameAs(databaseException);
    }

    private Payment createPendingPayment(UUID bookingId) {
        return Payment.initiateForBooking(
                bookingId,
                TOTAL_PRICE,
                CURRENCY_CODE,
                IDEMPOTENCY_KEY,
                INITIATED_AT);
    }

    private BookingPaymentDetailsResponse createPayableBookingDetails() {
        return new BookingPaymentDetailsResponse(
                BOOKING_ID,
                null,
                "HELD",
                INITIATED_AT.plusSeconds(300),
                TOTAL_PRICE,
                CURRENCY_CODE
        );
    }
}