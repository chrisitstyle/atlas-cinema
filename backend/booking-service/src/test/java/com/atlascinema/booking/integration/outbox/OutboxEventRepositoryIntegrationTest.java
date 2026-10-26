package com.atlascinema.booking.integration.outbox;

import com.atlascinema.booking.integration.BaseTestEnvironment;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryIntegrationTest
        extends BaseTestEnvironment {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldPersistUnpublishedEvent_whenEventIsCreated() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        LocalDateTime createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        String payload = """
                {
                  "eventId": "%s",
                  "bookingId": "%s"
                }
                """.formatted(
                eventId,
                bookingId
        );

        OutboxEvent event = OutboxEvent.create(
                        eventId,
                        bookingId,
                        "BookingCancelled",
                        payload,
                        createdAt);

        outboxEventRepository.saveAndFlush(event);

        // force the next read to come from PostgreSQL instead of the persistence context
        entityManager.clear();

        OutboxEvent found = outboxEventRepository
                        .findById(eventId)
                        .orElseThrow();

        assertThat(found.getId()).isEqualTo(eventId);

        assertThat(found.getAggregateId()).isEqualTo(bookingId);

        assertThat(found.getEventType()).isEqualTo("BookingCancelled");

        assertThat(found.getPayload()).isEqualTo(payload);

        assertThat(found.getCreatedAt()).isEqualTo(createdAt);

        assertThat(found.getPublishedAt()).isNull();
    }

    @Test
    void findUnpublished_shouldReturnOnlyUnpublishedEvents_orderedByCreatedAt() {
        LocalDateTime baseTime = LocalDateTime.of(2030, 1, 10, 12, 0);

        OutboxEvent oldestEvent = createEvent(baseTime.minusMinutes(2));

        OutboxEvent newestEvent = createEvent(baseTime.minusMinutes(1));

        OutboxEvent publishedEvent = createEvent(baseTime.minusMinutes(3));

        publishedEvent.markAsPublished(baseTime);

        outboxEventRepository.saveAll(List.of(newestEvent, publishedEvent, oldestEvent));

        outboxEventRepository.flush();

        entityManager.clear();

        List<OutboxEvent> events = outboxEventRepository
                        .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        assertThat(events).hasSize(2);

        assertThat(events)
                .extracting(OutboxEvent::getId)
                .containsExactly(oldestEvent.getId(), newestEvent.getId());
    }

    @Test
    void markAsPublished_shouldPersistPublishedAt_whenEventIsManaged() {
        LocalDateTime createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        LocalDateTime publishedAt = createdAt.plusSeconds(5);

        OutboxEvent event = createEvent(createdAt);

        // save may return a different managed instance when the entity already has an id
        OutboxEvent managedEvent = outboxEventRepository.saveAndFlush(event);

        managedEvent.markAsPublished(publishedAt);

        // hibernate detects the change on the managed entity and writes it to PostgreSQL
        outboxEventRepository.flush();

        entityManager.clear();

        OutboxEvent found = outboxEventRepository
                        .findById(managedEvent.getId())
                        .orElseThrow();

        assertThat(found.getPublishedAt()).isEqualTo(publishedAt);
    }

    private OutboxEvent createEvent(
            LocalDateTime createdAt
    ) {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String payload = """
                {
                  "eventId": "%s",
                  "bookingId": "%s"
                }
                """.formatted(
                eventId,
                bookingId);

        return OutboxEvent.create(
                eventId,
                bookingId,
                "BookingCancelled",
                payload,
                createdAt);
    }
}
