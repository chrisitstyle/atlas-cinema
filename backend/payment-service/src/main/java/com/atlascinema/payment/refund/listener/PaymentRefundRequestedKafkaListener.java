
package com.atlascinema.payment.refund.listener;

import com.atlascinema.payment.refund.event.BookingEventEnvelope;
import com.atlascinema.payment.refund.event.PaymentRefundRequestedPayload;
import com.atlascinema.payment.refund.service.PaymentRefundInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/*
 * Receives booking events and stores refund requests durably
 * Other booking event types are ignored
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundRequestedKafkaListener {

    private static final String REFUND_REQUESTED_EVENT_TYPE =
            "PaymentRefundRequested";

    private final PaymentRefundInboxService paymentRefundInboxService;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            topics = "${payment.refund.consumer.topic:booking-events}",
            groupId = "${payment.refund.consumer.group-id:payment-service-refunds}",
            autoStartup = "${payment.refund.consumer.enabled:true}"
    )
    public void onBookingEvent(ConsumerRecord<String, String> kafkaRecord) {
        String eventPayloadJson = kafkaRecord.value();

        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Booking event payload must not be blank");
        }

        BookingEventEnvelope event = jsonMapper.readValue(
                eventPayloadJson,
                BookingEventEnvelope.class);

        if (event == null || event.eventType() == null) {
            throw new IllegalArgumentException("Booking event type is required");
        }

        // this consumer group also receives unrelated booking events
        if (!REFUND_REQUESTED_EVENT_TYPE.equals(event.eventType())) {
            return;
        }

        if (event.bookingId() == null
                || !Objects.equals(
                kafkaRecord.key(),
                event.bookingId().toString()
        )) {
            throw new IllegalArgumentException("Kafka key does not match booking id");
        }

        if (event.payload() == null || event.payload().isNull()) {
            throw new IllegalArgumentException("Refund request payload is required");
        }

        PaymentRefundRequestedPayload refundRequest = jsonMapper.treeToValue(
                        event.payload(),
                        PaymentRefundRequestedPayload.class
                );
        boolean newlyReceived = paymentRefundInboxService.recordRefundRequest(
                event,
                refundRequest,
                eventPayloadJson);

        if (newlyReceived) {
            log.info(
                    "Received refund request for bookingId={}, paymentId={}, eventId={}",
                    event.bookingId(),
                    refundRequest.paymentId(),
                    event.eventId());
        }
    }
}
