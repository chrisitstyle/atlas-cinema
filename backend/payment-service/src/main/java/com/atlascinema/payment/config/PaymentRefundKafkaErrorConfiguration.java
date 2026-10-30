package com.atlascinema.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/*
 * Keeps failed refund messages eligible for redelivery
 * Database failures must not silently discard refund requests
 */
@Configuration
public class PaymentRefundKafkaErrorConfiguration {

    @Bean
    public DefaultErrorHandler paymentRefundKafkaErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}