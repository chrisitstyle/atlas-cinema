-- store refund requests received from booking-service
CREATE TABLE payment_refund_inbox_events
(
    event_id                UUID PRIMARY KEY,
    payment_id              UUID NOT NULL,
    booking_id              UUID NOT NULL,
    provider_transaction_id VARCHAR(128) NOT NULL,
    reason                  VARCHAR(64) NOT NULL,
    payload                 TEXT NOT NULL,
    occurred_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    received_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_at            TIMESTAMP(6) WITH TIME ZONE
);

-- support later refund processing and payment-level idempotency
CREATE INDEX idx_payment_refund_inbox_payment_id
    ON payment_refund_inbox_events (payment_id);

CREATE INDEX idx_payment_refund_inbox_unprocessed
    ON payment_refund_inbox_events (received_at, event_id)
    WHERE processed_at IS NULL;