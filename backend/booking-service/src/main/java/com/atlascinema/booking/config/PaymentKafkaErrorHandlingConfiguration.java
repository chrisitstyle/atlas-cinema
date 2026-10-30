package com.atlascinema.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/*
 * Keeps failed payment events eligible for redelivery
 * Failed messages are not silently discarded after a retry limit
 */
@Configuration
public class PaymentKafkaErrorHandlingConfiguration {

    @Bean
    public DefaultErrorHandler paymentKafkaErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
