package com.atlascinema.payment.repository;

import com.atlascinema.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);
}
