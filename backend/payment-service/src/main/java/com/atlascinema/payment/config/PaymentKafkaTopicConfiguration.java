
package com.atlascinema.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/*
 * Declares the payment events topic for Kafka administration
 */
@Configuration
public class PaymentKafkaTopicConfiguration {

    @Bean
    public NewTopic paymentEventsTopic(
            @Value("${payment.outbox.topic:payment-events}")
            String paymentEventsTopicName) {
        return TopicBuilder.name(paymentEventsTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
