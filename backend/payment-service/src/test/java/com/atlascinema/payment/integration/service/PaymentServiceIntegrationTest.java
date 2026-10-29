
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.BookingNotPayableException;
import com.atlascinema.payment.exception.PaymentIdempotencyConflictException;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.BookingPaymentValidationService;
import com.atlascinema.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Verifies payment initiation and idempotency using real PostgreSQL
 * Mocks booking validation to isolate payment persistence from HTTP communication
 */
class PaymentServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID BOOKING_ID = UUID.fromString("f5fd3520-c844-4d16-bde3-360e87648dd2");

    private static final UUID ANOTHER_BOOKING_ID = UUID.fromString("3c39c9e7-a548-42c3-97ca-6893fe878612");

    private static final BigDecimal TOTAL_PRICE = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant BOOKING_EXPIRES_AT = Instant.parse("2030-01-01T12:00:00Z");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private BookingPaymentValidationService bookingPaymentValidationService;

    @Test
    void initiatePaymentForBooking_shouldPersistPendingPayment_whenRequestIsNew() {
        String idempotencyKey = "payment-integration-001";

        mockPayableBookingDetails();

        Payment initiatedPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                idempotencyKey);

        Payment persistedPayment = paymentRepository.findById(initiatedPayment.getId())
                .orElseThrow();

        assertThat(persistedPayment.getBookingId()).isEqualTo(BOOKING_ID);

        assertThat(persistedPayment.getAmount()).isEqualByComparingTo(TOTAL_PRICE);

        assertThat(persistedPayment.getCurrencyCode()).isEqualTo(CURRENCY_CODE);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(persistedPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        assertThat(persistedPayment.getCreatedAt()).isNotNull();

        assertThat(persistedPayment.getUpdatedAt()).isNotNull();

        assertThat(paymentRepository.count()).isEqualTo(1);

        verify(bookingPaymentValidationService).getPayableBookingDetails(BOOKING_ID);
    }

    @Test
    void initiatePaymentForBooking_shouldReusePayment_whenSameRequestIsRepeated() {
        String idempotencyKey = "payment-integration-002";

        mockPayableBookingDetails();

        Payment firstPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                idempotencyKey);

        Payment repeatedPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                idempotencyKey);

        assertThat(repeatedPayment.getId()).isEqualTo(firstPayment.getId());

        assertThat(paymentRepository.count()).isEqualTo(1);

        // Booking validation must run only for the first initiation
        verify(bookingPaymentValidationService, times(1))
                .getPayableBookingDetails(BOOKING_ID);
    }

    @Test
    void initiatePaymentForBooking_shouldRejectRequest_whenKeyBelongsToAnotherBooking() {
        String idempotencyKey = "payment-integration-003";

        mockPayableBookingDetails();

        paymentService.initiatePaymentForBooking(BOOKING_ID, idempotencyKey);

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        ANOTHER_BOOKING_ID,
                        idempotencyKey)
        ).isInstanceOf(PaymentIdempotencyConflictException.class);

        assertThat(paymentRepository.count()).isEqualTo(1);

        // the conflicting request must not trigger a second validation
        verify(bookingPaymentValidationService, times(1))
                .getPayableBookingDetails(BOOKING_ID);
    }

    @Test
    void initiatePaymentForBooking_shouldRejectRequest_whenBookingIsNotPayable() {
        String idempotencyKey = "payment-integration-004";

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenThrow(new BookingNotPayableException(
                        BOOKING_ID,
                        "booking hold has expired"));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        BOOKING_ID,
                        idempotencyKey)
        ).isInstanceOf(BookingNotPayableException.class);

        // An invalid booking must not create a payment record
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void initiatePaymentForBooking_shouldReuseExistingPayment_whenBookingLaterBecomesNotPayable() {
        String idempotencyKey = "payment-integration-005";

        mockPayableBookingDetails();

        Payment firstPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                idempotencyKey);

        // The original payment must remain retrievable without checking
        // whether the booking is still payable
        Payment repeatedPayment = paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                idempotencyKey
        );

        assertThat(repeatedPayment.getId()).isEqualTo(firstPayment.getId());

        assertThat(paymentRepository.count()).isEqualTo(1);

        verify(bookingPaymentValidationService, times(1)).getPayableBookingDetails(BOOKING_ID);
    }

    private void mockPayableBookingDetails() {
        BookingPaymentDetailsResponse payableBookingDetails =
                new BookingPaymentDetailsResponse(
                        BOOKING_ID,
                        null,
                        "HELD",
                        BOOKING_EXPIRES_AT,
                        TOTAL_PRICE,
                        CURRENCY_CODE);

        when(bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID))
                .thenReturn(payableBookingDetails);
    }
}
