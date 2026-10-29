package com.atlascinema.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/*
 * Provides the time source used by payment operations
 * Allows tests to replace the system clock with a fixed clock
 */
@Configuration
public class PaymentTimeConfiguration {

    @Bean
    public Clock paymentClock() {
        return Clock.systemUTC();
    }
}
