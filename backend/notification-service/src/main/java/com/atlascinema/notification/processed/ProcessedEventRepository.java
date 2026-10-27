package com.atlascinema.notification.processed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO processed_events (
                        event_id,
                        event_type,
                        processed_at
                    )
                    VALUES (
                        :eventId,
                        :eventType,
                        :processedAt
                    )
                    ON CONFLICT (event_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("processedAt") LocalDateTime processedAt
    );
}
