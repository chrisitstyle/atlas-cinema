package com.atlascinema.booking.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // reads the oldest unpublished events in small batches
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    // Verify that this service previously requested the reported refund
    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM outbox_events
            WHERE aggregate_id = :bookingId
              AND event_type = 'PaymentRefundRequested'
              AND CAST(payload AS jsonb)
                    -> 'payload' ->> 'paymentId'
                    = CAST(:paymentId AS text)
              AND CAST(payload AS jsonb)
                    -> 'payload' ->> 'providerTransactionId'
                    = :providerTransactionId
        )
        """, nativeQuery = true)
    boolean existsMatchingRefundRequest(
            @Param("bookingId") UUID bookingId,
            @Param("paymentId") UUID paymentId,
            @Param("providerTransactionId") String providerTransactionId
    );
}
