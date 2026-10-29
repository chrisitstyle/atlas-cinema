
package com.atlascinema.payment.integration.repository;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.integration.BaseTestEnvironment;
import com.atlascinema.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies payment persistence against a real PostgreSQL database
 * Each test runs in a transaction that is rolled back afterward
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryIntegrationTest extends BaseTestEnvironment {

    private static final Instant INITIATED_AT = Instant.parse("2026-10-27T18:00:00Z");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFlush_shouldPersistPendingPayment_whenPaymentIsInitiated() {
        UUID bookingId = UUID.randomUUID();

        Payment initiatedPayment = createPendingPayment(bookingId,
                "payment-request-001");

        paymentRepository.saveAndFlush(initiatedPayment);

        // force the next lookup to read the entity from PostgreSQL
        entityManager.clear();

        Optional<Payment> paymentFromDatabase = paymentRepository.findById(initiatedPayment.getId());

        assertThat(paymentFromDatabase).isPresent();

        Payment persistedPayment = paymentFromDatabase.orElseThrow();

        assertThat(persistedPayment.getId()).isEqualTo(initiatedPayment.getId());

        assertThat(persistedPayment.getBookingId()).isEqualTo(bookingId);

        assertThat(persistedPayment.getAmount()).isEqualByComparingTo("29.90");

        assertThat(persistedPayment.getCurrencyCode()).isEqualTo("PLN");

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(persistedPayment.getIdempotencyKey()).isEqualTo("payment-request-001");

        assertThat(persistedPayment.getCreatedAt()).isEqualTo(INITIATED_AT);

        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(INITIATED_AT);
    }

    @Test
    void findByIdempotencyKey_shouldReturnExistingPayment_whenKeyMatches() {
        Payment initiatedPayment = createPendingPayment(
                UUID.randomUUID(),
                "payment-request-002");

        paymentRepository.saveAndFlush(initiatedPayment);
        entityManager.clear();

        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey("payment-request-002");

        assertThat(existingPayment).isPresent();

        assertThat(existingPayment.orElseThrow().getId()).isEqualTo(initiatedPayment.getId());
    }

    @Test
    void findAllByBookingId_shouldReturnMatchingPayments_whenMultipleBookingsExist() {
        UUID firstBookingId = UUID.randomUUID();
        UUID secondBookingId = UUID.randomUUID();

        Payment firstBookingInitialPayment = createPendingPayment(
                firstBookingId,
                "payment-request-003");

        Payment firstBookingAnotherPayment = createPendingPayment(
                firstBookingId,
                "payment-request-004");

        Payment secondBookingPayment = createPendingPayment(
                secondBookingId,
                "payment-request-005");

        paymentRepository.saveAllAndFlush(List.of(
                firstBookingInitialPayment,
                firstBookingAnotherPayment,
                secondBookingPayment));

        entityManager.clear();

        List<Payment> firstBookingPayments = paymentRepository.findAllByBookingId(firstBookingId);

        assertThat(firstBookingPayments)
                .extracting(Payment::getId)
                .containsExactlyInAnyOrder(
                        firstBookingInitialPayment.getId(),
                        firstBookingAnotherPayment.getId());
    }

    @Test
    void saveAndFlush_shouldRejectDuplicateIdempotencyKey_whenKeyAlreadyExists() {
        Payment firstPayment = createPendingPayment(
                UUID.randomUUID(),
                "payment-request-006");

        Payment anotherPaymentWithSameKey = createPendingPayment(
                UUID.randomUUID(),
                "payment-request-006");

        paymentRepository.saveAndFlush(firstPayment);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(anotherPaymentWithSameKey)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveAndFlush_shouldPersistProviderResult_whenPaymentSucceeds() {
        Payment payment = createPendingPayment(UUID.randomUUID(),"payment-provider-result-001");

        Instant processingStartedAt = INITIATED_AT.plusSeconds(10);

        Instant paymentCompletedAt = INITIATED_AT.plusSeconds(20);

        String providerTransactionId = "SIM-" + payment.getId();

        payment.markAsProcessing(processingStartedAt);

        payment.markAsSucceeded(providerTransactionId, paymentCompletedAt);

        paymentRepository.saveAndFlush(payment);

        entityManager.clear();

        Payment persistedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo(providerTransactionId);

        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(paymentCompletedAt);
    }

    private Payment createPendingPayment(
            UUID bookingId,
            String idempotencyKey) {
        return Payment.initiateForBooking(
                bookingId,
                new BigDecimal("29.90"),
                "PLN",
                idempotencyKey,
                INITIATED_AT);
    }
}
