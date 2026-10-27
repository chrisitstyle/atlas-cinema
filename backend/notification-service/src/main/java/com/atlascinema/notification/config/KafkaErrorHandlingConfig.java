package com.atlascinema.notification.config;

import com.atlascinema.notification.exception.InvalidBookingEventPayloadException;
import com.atlascinema.notification.exception.UnsupportedBookingEventTypeException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

/*
 * Configures retry and dead letter handling for booking events consumed from Kafka
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Duration DLT_PUBLISH_TIMEOUT =
            Duration.ofSeconds(5);

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            @Qualifier("dltKafkaTemplate")
            KafkaTemplate<String, Object> dltKafkaTemplate,
            NotificationKafkaProperties notificationKafkaProperties
    ) {
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate,
                        (consumerRecord, exception) ->
                                new TopicPartition(
                                        notificationKafkaProperties.bookingEventsDltTopic(),
                                        consumerRecord.partition()));

        /*
         * Do not consider recovery successful when Kafka
         * failed to store the record in the dead letter topic
         */
        deadLetterPublishingRecoverer.setFailIfSendResultIsError(true);

        /*
         * Do not wait indefinitely for Kafka to acknowledge
         * the record published to the dead letter topic
         */
        deadLetterPublishingRecoverer.setWaitForSendResultTimeout(DLT_PUBLISH_TIMEOUT);

        FixedBackOff bookingEventRetryBackOff = new FixedBackOff(
                        notificationKafkaProperties
                                .retryInterval()
                                .toMillis(),
                        notificationKafkaProperties.maxRetries());

        DefaultErrorHandler bookingEventErrorHandler = new DefaultErrorHandler(
                        deadLetterPublishingRecoverer,
                        bookingEventRetryBackOff);

        /*
         * Retrying cannot fix an unsupported event type
         * or an invalid event-specific payload
         */
        bookingEventErrorHandler.addNotRetryableExceptions(
                UnsupportedBookingEventTypeException.class,
                InvalidBookingEventPayloadException.class);

        return bookingEventErrorHandler;
    }
}