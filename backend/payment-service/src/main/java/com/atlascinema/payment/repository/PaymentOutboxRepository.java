package com.atlascinema.payment.repository;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/*
 * Provides persistence operations for payment outbox events
 */
@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, UUID> {

    List<PaymentOutboxEvent> findAllByPaymentId(UUID paymentId);
}