package com.atlascinema.payment.integration.client;

import com.atlascinema.payment.client.BookingClient;
import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import feign.FeignException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies real HTTP requests and JSON mapping through BookingClient
 * Replaces booking-service with a local HTTP server during tests
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingClientIntegrationTest extends AbstractIntegrationTest {

    private static final UUID BOOKING_ID = UUID.fromString("f5fd3520-c844-4d16-bde3-360e87648dd2");

    private static final Instant BOOKING_EXPIRES_AT = Instant.parse("2026-10-29T12:00:00Z");

    private static final MockWebServer BOOKING_HTTP_SERVER = startBookingHttpServer();

    @Autowired
    private BookingClient bookingClient;

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
    void getBookingPaymentDetails_shouldReturnBookingDetails_whenBookingExists()
            throws InterruptedException {

        BOOKING_HTTP_SERVER.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(createBookingDetailsJson("HELD")));

        BookingPaymentDetailsResponse bookingDetails = bookingClient.getBookingPaymentDetails(BOOKING_ID);

        assertThat(bookingDetails.bookingId()).isEqualTo(BOOKING_ID);

        assertThat(bookingDetails.customerId()).isNull();

        assertThat(bookingDetails.status()).isEqualTo("HELD");

        assertThat(bookingDetails.expiresAt()).isEqualTo(BOOKING_EXPIRES_AT);

        assertThat(bookingDetails.totalPrice())
                .isEqualByComparingTo("59.80");

        assertThat(bookingDetails.currencyCode())
                .isEqualTo("PLN");

        // verify the actual HTTP method and path sent by Feign
        RecordedRequest recordedBookingRequest = BOOKING_HTTP_SERVER.takeRequest(5, TimeUnit.SECONDS);

        assertThat(recordedBookingRequest).isNotNull();

        assertThat(recordedBookingRequest.getMethod()).isEqualTo("GET");

        assertThat(recordedBookingRequest.getPath())
                .isEqualTo("/internal/bookings/" + BOOKING_ID + "/payment-details");
    }

    @Test
    void getBookingPaymentDetails_shouldReturnExpiredStatus_whenBookingIsExpired() {
        BOOKING_HTTP_SERVER.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(createBookingDetailsJson("EXPIRED")));

        BookingPaymentDetailsResponse bookingDetails = bookingClient.getBookingPaymentDetails(BOOKING_ID);

        assertThat(bookingDetails.status()).isEqualTo("EXPIRED");
    }

    @Test
    void getBookingPaymentDetails_shouldThrowNotFound_whenBookingDoesNotExist() {
        BOOKING_HTTP_SERVER.enqueue(
                new MockResponse()
                        .setResponseCode(404)
                        .addHeader("Content-Type", "application/json")
                        .setBody("""
                                {
                                  "message": "Booking not found"
                                }
                                """));

        assertThatThrownBy(() -> bookingClient.getBookingPaymentDetails(BOOKING_ID)
        ).isInstanceOf(FeignException.NotFound.class);
    }

    @Test
    void getBookingPaymentDetails_shouldThrowServerError_whenBookingServiceFails() {
        BOOKING_HTTP_SERVER.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .addHeader("Content-Type", "application/json")
                        .setBody("""
                                {
                                  "message": "Internal server error"
                                }
                                """));

        assertThatThrownBy(() ->
                bookingClient.getBookingPaymentDetails(BOOKING_ID)
        ).isInstanceOf(FeignException.InternalServerError.class);
    }

    private static MockWebServer startBookingHttpServer() {
        MockWebServer bookingHttpServer = new MockWebServer();

        try {
            bookingHttpServer.start();
        } catch (IOException serverStartupFailure) {
            throw new IllegalStateException(
                    "Could not start fake booking-service HTTP server",
                    serverStartupFailure);
        }

        return bookingHttpServer;
    }

    private String createBookingDetailsJson(String bookingStatus) {
        return """
                {
                  "bookingId": "%s",
                  "customerId": null,
                  "status": "%s",
                  "expiresAt": "2026-10-29T12:00:00Z",
                  "totalPrice": 59.80,
                  "currencyCode": "PLN"
                }
                """.formatted(BOOKING_ID, bookingStatus);
    }
}
