
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.BookingPaymentValidationService;
import com.atlascinema.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/*
 * Verifies concurrent payment initiation using real PostgreSQL transactions
 * Both requests reach payment creation before either can insert a record
 */
class PaymentConcurrentInitiationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant PAYMENT_INITIATED_AT = Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant BOOKING_EXPIRES_AT = PAYMENT_INITIATED_AT.plusSeconds(300);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private BookingPaymentValidationService bookingPaymentValidationService;

    @Test
    void initiatePaymentForBooking_shouldReuseSinglePayment_whenRequestsRunConcurrently()
            throws Exception {

        UUID bookingId = UUID.randomUUID();
        String idempotencyKey = "concurrent-payment-" + UUID.randomUUID();

        BookingPaymentDetailsResponse payableBookingDetails = new BookingPaymentDetailsResponse(
                        bookingId,
                        null,
                        "HELD",
                        BOOKING_EXPIRES_AT,
                        PAYMENT_AMOUNT,
                        CURRENCY_CODE);

        // both requests receive the same verified booking information
        when(bookingPaymentValidationService.getPayableBookingDetails(bookingId))
                .thenReturn(payableBookingDetails);

        // both threads call the real payment service and PostgreSQL
        try (ExecutorService paymentRequestExecutor = Executors.newFixedThreadPool(2)) {

            Future<Payment> firstPaymentResult = paymentRequestExecutor.submit(() ->
                            paymentService.initiatePaymentForBooking(
                                    bookingId,
                                    idempotencyKey));

            Future<Payment> secondPaymentResult = paymentRequestExecutor.submit(() -> paymentService.initiatePaymentForBooking(
                                    bookingId,
                                    idempotencyKey));

            // wait until both service operations have completed
            Payment firstPayment = firstPaymentResult.get(30, TimeUnit.SECONDS);

            Payment secondPayment = secondPaymentResult.get(30, TimeUnit.SECONDS);

            assertThat(firstPayment.getId()).isEqualTo(secondPayment.getId());

            assertThat(firstPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            assertThat(secondPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            // only one payment must exist despite two concurrent requests
            assertThat(paymentRepository.count()).isEqualTo(1);

            Payment persistedPayment = paymentRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();

            assertThat(persistedPayment.getId()).isEqualTo(firstPayment.getId());

            assertThat(persistedPayment.getBookingId()).isEqualTo(bookingId);

            assertThat(persistedPayment.getAmount()).isEqualByComparingTo(PAYMENT_AMOUNT);

            assertThat(persistedPayment.getCurrencyCode()).isEqualTo(CURRENCY_CODE);
        }
    }

    /*
     * Replaces the production clock only in this test context
     * Synchronizes both requests immediately before payment insertion
     */
    @TestConfiguration
    static class ConcurrentPaymentTestConfiguration {

        @Bean
        @Primary
        Clock concurrentPaymentClock() {
            return new CoordinatedPaymentClock(
                    Clock.fixed(PAYMENT_INITIATED_AT, ZoneOffset.UTC),
                    new CyclicBarrier(2));
        }
    }

    /*
     * Forces both payment creation attempts to reach the same point
     * Each thread waits until the other thread reaches Clock.instant()
     */
    private static class CoordinatedPaymentClock extends Clock {

        private final Clock delegateClock;
        private final CyclicBarrier paymentCreationBarrier;

        private CoordinatedPaymentClock(
                Clock delegateClock,
                CyclicBarrier paymentCreationBarrier
        ) {
            this.delegateClock = delegateClock;
            this.paymentCreationBarrier = paymentCreationBarrier;
        }

        @Override
        public ZoneId getZone() {
            return delegateClock.getZone();
        }

        @Override
        public Clock withZone(ZoneId zoneId) {
            return new CoordinatedPaymentClock(
                    delegateClock.withZone(zoneId),
                    paymentCreationBarrier);
        }

        @Override
        public Instant instant() {
            try {
                paymentCreationBarrier.await(20, TimeUnit.SECONDS);

                return delegateClock.instant();

            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "Payment creation synchronization was interrupted",
                        interruptedException);

            } catch (BrokenBarrierException | TimeoutException synchronizationFailure) {
                throw new IllegalStateException(
                        "Concurrent payment requests did not reach creation together",
                        synchronizationFailure);
            }
        }
    }
}
