package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.BookingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "booking.outbox.publisher.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxPublisher {

    private static final Duration KAFKA_ACK_TIMEOUT = Duration.ofSeconds(5);

    private final OutboxEventRepository outboxEventRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${booking.outbox.publish-interval:1s}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingOutboxEvents = outboxEventRepository
                        .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent outboxEvent : pendingOutboxEvents) {
            boolean publishedSuccessfully = publishOutboxEvent(outboxEvent);

            /*
             * Stop the batch after a failure so newer events
             * are not published before an older event
             */
            if (!publishedSuccessfully) {
                break;
            }
        }
    }

    private boolean publishOutboxEvent(
            OutboxEvent outboxEvent
    ) {
        try {
            BookingEventMessage bookingEventMessage = deserializeBookingEventMessage(outboxEvent);

            /*
             * Wait for Kafka acknowledgement before marking
             * the outbox event as published
             */
            bookingEventPublisher
                    .publish(bookingEventMessage)
                    .get(KAFKA_ACK_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS);

            outboxEvent.markAsPublished(
                    LocalDateTime.now(clock)
            );

            /*
             * The entity may be detached because the repository read
             * finished before the Kafka call
             */
            outboxEventRepository.saveAndFlush(
                    outboxEvent
            );

            return true;

        } catch (InterruptedException exception) {
            /*
             * Restore the interrupt flag so higher-level code
             * can see that this thread was interrupted
             */
            Thread.currentThread().interrupt();

            log.error("Outbox event publishing interrupted for event {}",
                    outboxEvent.getId(),
                    exception);

            return false;

        } catch (ExecutionException
                 | TimeoutException
                 | JacksonException exception) {

            log.error("Failed to publish outbox event {}",
                    outboxEvent.getId(),
                    exception);

            return false;
        }
    }

    private BookingEventMessage deserializeBookingEventMessage(
            OutboxEvent outboxEvent) throws JacksonException {

        return jsonMapper.readValue(
                outboxEvent.getPayload(),
                BookingEventMessage.class
        );
    }
}