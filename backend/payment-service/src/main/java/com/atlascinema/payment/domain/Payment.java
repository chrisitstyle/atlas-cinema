
package com.atlascinema.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/*
 * Represents a single payment attempt for a booking
 * Owns payment state and protects valid status transitions
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_idempotency_key",
                columnNames = "idempotency_key")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // Booking belongs to another microservice and is referenced only by its id
    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    // BigDecimal avoids floating-point rounding errors
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    // repeated requests with the same key must not create separate payments
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    // identifies the transaction reported by the payment provider
    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Identifies the refund recorded by the payment provider
    @Column(name = "provider_refund_transaction_id", length = 128)
    private String providerRefundTransactionId;

    // tracks the most recent attempt to recover an unknown provider result
    @Column(name = "last_reconciliation_attempt_at")
    private Instant lastReconciliationAttemptAt;

    public static Payment initiateForBooking(
            UUID bookingId,
            BigDecimal amount,
            String currencyCode,
            String idempotencyKey,
            Instant initiatedAt
    ) {
        Objects.requireNonNull(bookingId, "Booking id is required");
        Objects.requireNonNull(amount, "Payment amount is required");
        Objects.requireNonNull(initiatedAt, "Initiation time is required");

        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive and have at most two decimal places");
        }

        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency code must contain three uppercase letters");
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException(
                    "Payment idempotency key must contain between 1 and 128 characters");
        }

        Payment initiatedPayment = new Payment();

        initiatedPayment.id = UUID.randomUUID();
        initiatedPayment.bookingId = bookingId;
        initiatedPayment.amount = amount;
        initiatedPayment.currencyCode = currencyCode;
        initiatedPayment.idempotencyKey = idempotencyKey;

        initiatedPayment.status = PaymentStatus.PENDING;
        initiatedPayment.createdAt = initiatedAt;
        initiatedPayment.updatedAt = initiatedAt;

        return initiatedPayment;
    }

    // claim the payment before contacting the payment provider
    public void markAsProcessing(Instant processingStartedAt) {
        changeStatus(
                PaymentStatus.PENDING,
                PaymentStatus.PROCESSING,
                processingStartedAt);
    }

    // store the successful provider result
    public void markAsSucceeded(
            String providerTransactionId,
            Instant completedAt
    ) {
        finishProcessing(
                PaymentStatus.SUCCEEDED,
                providerTransactionId,
                completedAt);
    }

    // store the definitively declined provider result
    public void markAsFailed(
            String providerTransactionId,
            Instant completedAt
    ) {
        finishProcessing(PaymentStatus.FAILED,
                providerTransactionId,
                completedAt);
    }

    public void requestRefund(Instant refundRequestedAt) {
        changeStatus(PaymentStatus.SUCCEEDED,
                PaymentStatus.REFUND_PENDING,
                refundRequestedAt);
    }

    // Record the completed provider refund and its reference
    public void markAsRefunded(
            String providerRefundTransactionId,
            Instant refundCompletedAt
    ) {
        if (providerRefundTransactionId == null
                || providerRefundTransactionId.isBlank()
                || providerRefundTransactionId.length() > 128) {
            throw new IllegalArgumentException("Provider refund transaction id must contain between 1 and 128 characters");
        }

        changeStatus(
                PaymentStatus.REFUND_PENDING,
                PaymentStatus.REFUNDED,
                refundCompletedAt);

        this.providerRefundTransactionId = providerRefundTransactionId;
    }

    // update the provider reference and final payment status together
    private void finishProcessing(
            PaymentStatus finalStatus,
            String providerTransactionId,
            Instant completedAt
    ) {
        if (providerTransactionId == null
                || providerTransactionId.isBlank()
                || providerTransactionId.length() > 128) {
            throw new IllegalArgumentException(
                    "Provider transaction id must contain between 1 and 128 characters");
        }

        changeStatus(PaymentStatus.PROCESSING,
                finalStatus,
                completedAt);

        this.providerTransactionId = providerTransactionId;
    }

    private void changeStatus(
            PaymentStatus expectedCurrentStatus,
            PaymentStatus newStatus,
            Instant statusChangedAt
    ) {
        Objects.requireNonNull(statusChangedAt, "Status change time is required");

        if (status != expectedCurrentStatus) {
            throw new IllegalStateException("Cannot change payment %s from %s to %s"
                            .formatted(id, status, newStatus));
        }

        status = newStatus;
        updatedAt = statusChangedAt;
    }
}
