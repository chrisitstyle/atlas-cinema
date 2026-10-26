package com.atlascinema.booking;

import com.atlascinema.booking.config.BookingKafkaProperties;
import com.atlascinema.booking.config.BookingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({BookingProperties.class, BookingKafkaProperties.class})
@EnableFeignClients
@SpringBootApplication
public class BookingServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

}
