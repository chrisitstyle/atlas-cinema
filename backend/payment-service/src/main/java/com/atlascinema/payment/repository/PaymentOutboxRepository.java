
package com.atlascinema.payment.repository;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * Provides persistence operations for payment outbox events
 * Supports finding unpublished events and locking publication updates
 */
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, UUID> {

    List<PaymentOutboxEvent> findAllByPaymentId(UUID paymentId);

    // fetch a bounded set of unpublished event identifiers
    @Query("""
            SELECT outboxEvent.id
            FROM PaymentOutboxEvent outboxEvent
            WHERE outboxEvent.publishedAt IS NULL
            ORDER BY outboxEvent.occurredAt ASC, outboxEvent.id ASC
            """)
    List<UUID> findUnpublishedEventIds(Pageable pageable);

    // lock the event while marking its publication as completed
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outboxEvent
            FROM PaymentOutboxEvent outboxEvent
            WHERE outboxEvent.id = :eventId
            """)
    Optional<PaymentOutboxEvent> findByIdForUpdate(
            @Param("eventId") UUID eventId
    );
}
