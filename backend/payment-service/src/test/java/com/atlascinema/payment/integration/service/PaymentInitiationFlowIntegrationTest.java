package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.BookingNotPayableException;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PaymentService;
import feign.FeignException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * verifies payment initiation through real Feign communication and PostgreSQL
 * only the external booking-service HTTP response is simulated
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentInitiationFlowIntegrationTest extends AbstractIntegrationTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant VALID_EXPIRATION = CURRENT_TIME.plusSeconds(300);

    private static final MockWebServer BOOKING_HTTP_SERVER = startBookingHttpServer();

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @DynamicPropertySource
    static void configureBookingClientUrl(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.openfeign.client.config.booking-service.url",
                () -> BOOKING_HTTP_SERVER.url("/").toString());
    }

    @AfterAll
    static void stopBookingHttpServer() throws IOException {
        BOOKING_HTTP_SERVER.shutdown();
    }

    @Test
    void initiatePaymentForBooking_shouldPersistPayment_whenBookingIsPayable()
            throws InterruptedException {

        UUID bookingId = UUID.randomUUID();
        String idempotencyKey = "payment-flow-" + UUID.randomUUID();

        enqueueBookingDetailsResponse(
                bookingId,
                "HELD",
                VALID_EXPIRATION);

        Payment initiatedPayment = paymentService.initiatePaymentForBooking(
                bookingId,
                idempotencyKey);

        Payment persistedPayment = paymentRepository
                .findById(initiatedPayment.getId())
                .orElseThrow();

        assertThat(persistedPayment.getBookingId()).isEqualTo(bookingId);

        assertThat(persistedPayment.getAmount()).isEqualByComparingTo("59.80");

        assertThat(persistedPayment.getCurrencyCode()).isEqualTo("PLN");

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(persistedPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        assertThat(paymentRepository.count()).isEqualTo(1);

        RecordedRequest bookingRequest = BOOKING_HTTP_SERVER.takeRequest(5, TimeUnit.SECONDS);

        assertThat(bookingRequest).isNotNull();

        assertThat(bookingRequest.getMethod()).isEqualTo("GET");

        assertThat(bookingRequest.getPath())
                .isEqualTo("/internal/bookings/" + bookingId + "/payment-details");
    }

    @Test
    void initiatePaymentForBooking_shouldReusePayment_withoutSecondBookingRequest()
            throws InterruptedException {

        UUID bookingId = UUID.randomUUID();
        String idempotencyKey = "payment-flow-" + UUID.randomUUID();

        enqueueBookingDetailsResponse(
                bookingId,
                "HELD",
                VALID_EXPIRATION);

        Payment firstPayment = paymentService.initiatePaymentForBooking(
                bookingId,
                idempotencyKey);

        // a repeated request must use the original payment
        Payment repeatedPayment = paymentService.initiatePaymentForBooking(
                bookingId,
                idempotencyKey);

        assertThat(repeatedPayment.getId()).isEqualTo(firstPayment.getId());

        assertThat(paymentRepository.count()).isEqualTo(1);

        RecordedRequest firstBookingRequest = BOOKING_HTTP_SERVER.takeRequest(5, TimeUnit.SECONDS);

        assertThat(firstBookingRequest).isNotNull();

        // no additional HTTP request should be sent for the repeated payment
        RecordedRequest unexpectedSecondBookingRequest =
                BOOKING_HTTP_SERVER.takeRequest(200, TimeUnit.MILLISECONDS);

        assertThat(unexpectedSecondBookingRequest).isNull();
    }

    @Test
    void initiatePaymentForBooking_shouldRejectPayment_whenBookingIsExpired()
            throws InterruptedException {

        UUID bookingId = UUID.randomUUID();
        String idempotencyKey = "payment-flow-" + UUID.randomUUID();

        enqueueBookingDetailsResponse(
                bookingId,
                "EXPIRED",
                CURRENT_TIME.minusSeconds(60));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        bookingId,
                        idempotencyKey)
        ).isInstanceOf(BookingNotPayableException.class);

        assertThat(paymentRepository.count()).isZero();

        RecordedRequest bookingRequest = BOOKING_HTTP_SERVER.takeRequest(5, TimeUnit.SECONDS);

        assertThat(bookingRequest).isNotNull();
    }

    @Test
    void initiatePaymentForBooking_shouldNotCreatePayment_whenBookingDoesNotExist()
            throws InterruptedException {

        UUID bookingId = UUID.randomUUID();
        String idempotencyKey = "payment-flow-" + UUID.randomUUID();

        BOOKING_HTTP_SERVER.enqueue(
                new MockResponse()
                        .setResponseCode(404)
                        .addHeader("Content-Type", "application/json")
                        .setBody("""
                                {
                                  "message": "Booking not found"
                                }
                                """));

        assertThatThrownBy(() -> paymentService.initiatePaymentForBooking(
                        bookingId,
                        idempotencyKey)
        ).isInstanceOf(FeignException.NotFound.class);

        assertThat(paymentRepository.count()).isZero();

        RecordedRequest bookingRequest = BOOKING_HTTP_SERVER.takeRequest(5, TimeUnit.SECONDS);

        assertThat(bookingRequest).isNotNull();
    }

    private void enqueueBookingDetailsResponse(
            UUID bookingId,
            String bookingStatus,
            Instant bookingExpiresAt
    ) {
        String bookingDetailsJson = """
                {
                  "bookingId": "%s",
                  "customerId": null,
                  "status": "%s",
                  "expiresAt": "%s",
                  "totalPrice": 59.80,
                  "currencyCode": "PLN"
                }
                """.formatted(
                bookingId,
                bookingStatus,
                bookingExpiresAt);

        BOOKING_HTTP_SERVER.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(bookingDetailsJson));
    }

    private static MockWebServer startBookingHttpServer() {
        MockWebServer bookingHttpServer = new MockWebServer();

        try {
            bookingHttpServer.start();
        } catch (IOException serverStartupFailure) {
            throw new IllegalStateException(
                    "Could not start fake booking-service HTTP server",
                    serverStartupFailure
            );
        }

        return bookingHttpServer;
    }

    /*
     * uses a fixed clock so booking expiration checks are deterministic
     */
    @TestConfiguration
    static class PaymentFlowTestConfiguration {

        @Bean
        @Primary
        Clock paymentFlowClock() {
            return Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);
        }
    }
}
