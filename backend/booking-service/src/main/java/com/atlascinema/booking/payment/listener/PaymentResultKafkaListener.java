package com.atlascinema.booking.payment.listener;

import com.atlascinema.booking.payment.event.PaymentResultEvent;
import com.atlascinema.booking.payment.service.PaymentInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/*
 * Receives payment result messages from Kafka
 * Persists events before allowing their Kafka offsets to be acknowledged
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultKafkaListener {

    private final PaymentInboxService paymentInboxService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${booking.payment-events.topic}",
            groupId = "${booking.payment-events.group-id}")
    public void onPaymentResult(
            ConsumerRecord<String, String> kafkaRecord
    ) {
        String eventPayloadJson = kafkaRecord.value();

        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Kafka payment event payload must not be blank");
        }

        PaymentResultEvent paymentResultEvent = jsonMapper.readValue(
                eventPayloadJson,
                PaymentResultEvent.class);

        // Kafka key must identify the same booking as the event payload
        if (paymentResultEvent.bookingId() == null
                || !Objects.equals(
                kafkaRecord.key(),
                paymentResultEvent.bookingId().toString()
        )) {
            throw new IllegalArgumentException("Kafka payment event key does not match booking id");
        }

        boolean newlyReceived = paymentInboxService.recordPaymentResult(
                paymentResultEvent,
                eventPayloadJson);

        if (newlyReceived) {
            log.info("Received payment event {} for booking {}",
                    paymentResultEvent.eventId(),
                    paymentResultEvent.bookingId());
        }
    }
}
