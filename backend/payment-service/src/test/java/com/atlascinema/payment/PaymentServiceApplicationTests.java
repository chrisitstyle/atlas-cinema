package com.atlascinema.payment;

import com.atlascinema.payment.client.BookingClient;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies that payment-service starts with its real database configuration
 */
class PaymentServiceApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private BookingClient bookingClient;

	@Test
	void contextLoads_shouldStartApplication_whenPostgresIsAvailable() {
	}

	@Test
	void contextLoads_shouldRegisterBookingClient_whenApplicationStarts() {
		assertThat(bookingClient).isNotNull();
	}
}
