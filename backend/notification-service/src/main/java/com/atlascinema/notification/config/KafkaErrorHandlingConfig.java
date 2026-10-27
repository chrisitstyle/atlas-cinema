package com.atlascinema.notification.config;

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

@Configuration
public class KafkaErrorHandlingConfig {

    private static final Duration DLT_PUBLISH_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            @Qualifier("dltKafkaTemplate")
            KafkaTemplate<String, Object> kafkaTemplate,
            NotificationKafkaProperties kafkaProperties
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        kafkaProperties.bookingEventsDltTopic(),
                                        record.partition()));

        /*
         * dont consider recovery successful when Kafka
         * failed to store the record in the dead letter topic
         */
        recoverer.setFailIfSendResultIsError(true);

        // dont wait indefinitely for Kafka to acknowledge the DLT record
        recoverer.setWaitForSendResultTimeout(DLT_PUBLISH_TIMEOUT);

        FixedBackOff backOff = new FixedBackOff(kafkaProperties
                                .retryInterval()
                                .toMillis(),
                        kafkaProperties.maxRetries());

        /*
         * Retry the failed record and send it to the DLT
         * when all configured attempts are exhausted
         */
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
