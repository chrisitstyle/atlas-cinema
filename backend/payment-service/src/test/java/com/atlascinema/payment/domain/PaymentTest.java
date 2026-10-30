
package com.atlascinema.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies payment creation and allowed status transitions
 * Tests business rules without starting Spring or a database
 */
class PaymentTest {

    private static final UUID BOOKING_ID = UUID.fromString("f5fd3520-c844-4d16-bde3-360e87648dd2");

    private static final Instant INITIATED_AT = Instant.parse("2026-10-27T10:00:00Z");

    private static final Instant PROCESSING_STARTED_AT = Instant.parse("2026-10-27T10:01:00Z");

    private static final Instant PAYMENT_COMPLETED_AT = Instant.parse("2026-10-27T10:02:00Z");

    private static final Instant REFUND_REQUESTED_AT = Instant.parse("2026-10-27T10:03:00Z");

    private static final Instant REFUND_COMPLETED_AT = Instant.parse("2026-10-27T10:04:00Z");

    private static final String IDEMPOTENCY_KEY = "payment-request-123";

    @Test
    void initiateForBooking_shouldCreatePendingPayment_whenDataIsValid() {
        Payment payment = createPendingPayment();

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(payment.getAmount()).isEqualByComparingTo("29.90");
        assertThat(payment.getCurrencyCode()).isEqualTo("PLN");
        assertThat(payment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProviderTransactionId()).isNull();
        assertThat(payment.getCreatedAt()).isEqualTo(INITIATED_AT);
        assertThat(payment.getUpdatedAt()).isEqualTo(INITIATED_AT);
    }

    @Test
    void initiateForBooking_shouldRejectPayment_whenAmountIsZero() {
        assertThatThrownBy(() -> Payment.initiateForBooking(
                BOOKING_ID,
                BigDecimal.ZERO,
                "PLN",
                IDEMPOTENCY_KEY,
                INITIATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiateForBooking_shouldRejectPayment_whenAmountHasTooManyDecimalPlaces() {
        assertThatThrownBy(() -> Payment.initiateForBooking(
                BOOKING_ID,
                new BigDecimal("29.999"),
                "PLN",
                IDEMPOTENCY_KEY,
                INITIATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiateForBooking_shouldRejectPayment_whenCurrencyCodeIsInvalid() {
        assertThatThrownBy(() -> Payment.initiateForBooking(
                BOOKING_ID,
                new BigDecimal("29.90"),
                "pln",
                IDEMPOTENCY_KEY,
                INITIATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initiateForBooking_shouldRejectPayment_whenIdempotencyKeyIsBlank() {
        assertThatThrownBy(() -> Payment.initiateForBooking(
                BOOKING_ID,
                new BigDecimal("29.90"),
                "PLN",
                " ",
                INITIATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markAsProcessing_shouldChangeStatus_whenPaymentIsPending() {
        Payment payment = createPendingPayment();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(payment.getUpdatedAt()).isEqualTo(PROCESSING_STARTED_AT);

        assertThat(payment.getProviderTransactionId()).isNull();
    }

    @Test
    void markAsSucceeded_shouldChangeStatus_whenPaymentIsProcessing() {
        Payment payment = createPendingPayment();
        String providerTransactionId = "SIM-" + payment.getId();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        payment.markAsSucceeded(providerTransactionId, PAYMENT_COMPLETED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(payment.getProviderTransactionId()).isEqualTo(providerTransactionId);

        assertThat(payment.getUpdatedAt()).isEqualTo(PAYMENT_COMPLETED_AT);

        assertThat(payment.getCreatedAt()).isEqualTo(INITIATED_AT);
    }

    @Test
    void markAsFailed_shouldChangeStatus_whenPaymentIsProcessing() {
        Payment payment = createPendingPayment();
        String providerTransactionId = "SIM-" + payment.getId();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        payment.markAsFailed(providerTransactionId, PAYMENT_COMPLETED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getProviderTransactionId()).isEqualTo(providerTransactionId);

        assertThat(payment.getUpdatedAt()).isEqualTo(PAYMENT_COMPLETED_AT);
    }

    @Test
    void requestRefund_shouldChangeStatus_whenPaymentSucceeded() {
        Payment payment = createPendingPayment();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        payment.markAsSucceeded("SIM-" + payment.getId(),
                PAYMENT_COMPLETED_AT);

        payment.requestRefund(REFUND_REQUESTED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        assertThat(payment.getUpdatedAt()).isEqualTo(REFUND_REQUESTED_AT);
    }

    @Test
    void markAsRefunded_shouldChangeStatus_whenRefundIsPending() {
        Payment payment = createPendingPayment();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        payment.markAsSucceeded("SIM-" + payment.getId(),
                PAYMENT_COMPLETED_AT);

        payment.requestRefund(REFUND_REQUESTED_AT);

        payment.markAsRefunded("SIM-REF-" + payment.getId(), REFUND_COMPLETED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        assertThat(payment.getUpdatedAt()).isEqualTo(REFUND_COMPLETED_AT);

        assertThat(payment.getProviderRefundTransactionId())
                .isEqualTo("SIM-REF-" + payment.getId());
    }

    @Test
    void markAsSucceeded_shouldRejectTransition_whenPaymentAlreadyFailed() {
        Payment payment = createPendingPayment();
        String providerTransactionId = "SIM-" + payment.getId();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        payment.markAsFailed(providerTransactionId, PAYMENT_COMPLETED_AT);

        assertThatThrownBy(() -> payment.markAsSucceeded(
                providerTransactionId,
                REFUND_REQUESTED_AT
        )).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getUpdatedAt()).isEqualTo(PAYMENT_COMPLETED_AT);

        assertThat(payment.getProviderTransactionId()).isEqualTo(providerTransactionId);
    }

    @Test
    void requestRefund_shouldRejectTransition_whenPaymentIsPending() {
        Payment payment = createPendingPayment();

        assertThatThrownBy(() ->
                payment.requestRefund(REFUND_REQUESTED_AT)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(payment.getUpdatedAt()).isEqualTo(INITIATED_AT);
    }

    @Test
    void markAsSucceeded_shouldRejectTransition_whenPaymentIsPending() {
        Payment payment = createPendingPayment();

        assertThatThrownBy(() -> payment.markAsSucceeded(
                "SIM-" + payment.getId(),
                PAYMENT_COMPLETED_AT
        )).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(payment.getProviderTransactionId()).isNull();

        assertThat(payment.getUpdatedAt()).isEqualTo(INITIATED_AT);
    }

    @Test
    void markAsFailed_shouldRejectTransition_whenPaymentIsPending() {
        Payment payment = createPendingPayment();

        assertThatThrownBy(() -> payment.markAsFailed(
                "SIM-" + payment.getId(),
                PAYMENT_COMPLETED_AT
        )).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(payment.getProviderTransactionId()).isNull();
    }

    @Test
    void markAsSucceeded_shouldRejectResult_whenProviderTransactionIdIsBlank() {
        Payment payment = createPendingPayment();

        payment.markAsProcessing(PROCESSING_STARTED_AT);

        assertThatThrownBy(() -> payment.markAsSucceeded(
                " ",
                PAYMENT_COMPLETED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(payment.getProviderTransactionId()).isNull();

        assertThat(payment.getUpdatedAt()).isEqualTo(PROCESSING_STARTED_AT);
    }

    private Payment createPendingPayment() {
        return Payment.initiateForBooking(
                BOOKING_ID,
                new BigDecimal("29.90"),
                "PLN",
                IDEMPOTENCY_KEY,
                INITIATED_AT);
    }
}
