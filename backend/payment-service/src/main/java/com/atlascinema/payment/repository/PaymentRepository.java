package com.atlascinema.payment.repository;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * Provides database access to payments
 * Supports pessimistic locking during payment state transitions
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // find the original payment when the client repeats the same request
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // a booking can have more than one payment attempt
    List<Payment> findAllByBookingId(UUID bookingId);

    // Lock the payment row until the current transaction completes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT payment
            FROM Payment payment
            WHERE payment.id = :paymentId
            """)
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") UUID paymentId
    );

    /*
     * Find older processing payments eligible for another provider lookup
     * Page size limits how many payments one scheduler run can inspect
     */
    @Query("""
            SELECT payment.id
            FROM Payment payment
            WHERE payment.status = :processingStatus
              AND payment.updatedAt <= :processingStartedBefore
              AND (
                  payment.lastReconciliationAttemptAt IS NULL
                  OR payment.lastReconciliationAttemptAt <= :retryEligibleBefore
              )
            ORDER BY
                COALESCE(
                    payment.lastReconciliationAttemptAt,
                    payment.updatedAt
                ) ASC,
                payment.id ASC
            """)
    List<UUID> findReconciliationCandidateIds(
            @Param("processingStatus") PaymentStatus processingStatus,
            @Param("processingStartedBefore") Instant processingStartedBefore,
            @Param("retryEligibleBefore") Instant retryEligibleBefore,
            Pageable pageable
    );

    /*
     * Claim one payment atomically before contacting the provider
     * Only one scheduler instance can claim the same eligible payment
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE Payment payment
            SET payment.lastReconciliationAttemptAt = :attemptedAt
            WHERE payment.id = :paymentId
              AND payment.status = :processingStatus
              AND payment.updatedAt <= :processingStartedBefore
              AND (
                  payment.lastReconciliationAttemptAt IS NULL
                  OR payment.lastReconciliationAttemptAt <= :retryEligibleBefore
              )
            """)
    int claimForReconciliation(
            @Param("paymentId") UUID paymentId,
            @Param("processingStatus") PaymentStatus processingStatus,
            @Param("processingStartedBefore") Instant processingStartedBefore,
            @Param("retryEligibleBefore") Instant retryEligibleBefore,
            @Param("attemptedAt") Instant attemptedAt
    );

    // find a limited batch of payments waiting to be processed
    @Query("""
        SELECT payment.id
        FROM Payment payment
        WHERE payment.status = :pendingStatus
        ORDER BY payment.createdAt ASC, payment.id ASC
        """)
    List<UUID> findPendingPaymentIds(
            @Param("pendingStatus") PaymentStatus pendingStatus,
            Pageable pageable
    );
}
