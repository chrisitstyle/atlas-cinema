
package com.atlascinema.booking.payment.listener;

import com.atlascinema.booking.payment.event.PaymentRefundedEvent;
import com.atlascinema.booking.payment.service.PaymentRefundConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/*
 * Receives refund completion events from payment-service
 * A Kafka record succeeds only after the database confirmation commits
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundedKafkaListener {

    private final PaymentRefundConfirmationService confirmationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            topics = "${booking.payment-refunds.topic:payment-refunds}",
            groupId = "${booking.payment-refunds.group-id:booking-service-payment-refunds}",
            autoStartup = "${booking.payment-refunds.consumer.enabled:true}"
    )
    public void onPaymentRefunded(
            ConsumerRecord<String, String> kafkaRecord
    ) {
        String eventPayloadJson = kafkaRecord.value();

        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Refund event payload must not be blank");
        }

        PaymentRefundedEvent event = jsonMapper.readValue(
                eventPayloadJson,
                PaymentRefundedEvent.class);

        if (event == null || event.bookingId() == null
                || !Objects.equals(
                kafkaRecord.key(),
                event.bookingId().toString()
        )) {
            throw new IllegalArgumentException("Kafka refund event key does not match booking id");
        }

        boolean newlyConfirmed = confirmationService.recordRefundConfirmation(event, eventPayloadJson);

        if (newlyConfirmed) {
            log.info(
                    "Refund confirmed for bookingId={}, paymentId={}, refundReference={}",
                    event.bookingId(),
                    event.paymentId(),
                    event.providerRefundTransactionId());
        }
    }
}
