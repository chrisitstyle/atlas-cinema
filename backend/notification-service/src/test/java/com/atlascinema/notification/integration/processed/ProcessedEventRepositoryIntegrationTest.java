package com.atlascinema.notification.integration.processed;

import com.atlascinema.notification.integration.BaseTestEnvironment;
import com.atlascinema.notification.processed.ProcessedEvent;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessedEventRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void insertIfAbsent_shouldInsertEvent_whenEventWasNotProcessedBefore() {
        UUID eventId = UUID.randomUUID();

        LocalDateTime processedAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        int inserted = processedEventRepository.insertIfAbsent(eventId, "BookingCancelled", processedAt);

        assertThat(inserted).isEqualTo(1);

        ProcessedEvent storedEvent = processedEventRepository
                        .findById(eventId)
                        .orElseThrow();

        assertThat(storedEvent.getEventId()).isEqualTo(eventId);

        assertThat(storedEvent.getEventType()).isEqualTo("BookingCancelled");

        assertThat(storedEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    void insertIfAbsent_shouldNotInsertDuplicate_whenEventWasAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();

        LocalDateTime firstProcessedAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        LocalDateTime duplicateProcessedAt = firstProcessedAt.plusSeconds(5);

        int firstInsert = processedEventRepository.insertIfAbsent(eventId, "BookingCancelled", firstProcessedAt);

        int duplicateInsert = processedEventRepository.insertIfAbsent(eventId, "BookingCancelled", duplicateProcessedAt);

        assertThat(firstInsert).isEqualTo(1);

        assertThat(duplicateInsert).isZero();

        assertThat(processedEventRepository.count())
                .isEqualTo(1);

        ProcessedEvent storedEvent = processedEventRepository
                        .findById(eventId)
                        .orElseThrow();

        // the duplicate must not overwrite the original processing information
        assertThat(storedEvent.getProcessedAt())
                .isEqualTo(firstProcessedAt);
    }

    @Test
    void insertIfAbsent_shouldInsertBothEvents_whenEventIdsAreDifferent() {
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();

        LocalDateTime processedAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        int firstInsert = processedEventRepository.insertIfAbsent(
                        firstEventId,
                        "BookingCancelled",
                        processedAt);

        int secondInsert = processedEventRepository.insertIfAbsent(
                        secondEventId,
                        "BookingCancelled",
                        processedAt);

        assertThat(firstInsert).isEqualTo(1);

        assertThat(secondInsert).isEqualTo(1);

        assertThat(processedEventRepository.count()).isEqualTo(2);
    }
}
