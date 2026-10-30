-- Store payment results received from Kafka
CREATE TABLE payment_inbox_events
(
    event_id     UUID PRIMARY KEY,
    payment_id   UUID NOT NULL UNIQUE,
    booking_id   UUID NOT NULL,
    event_type   VARCHAR(32) NOT NULL,
    payload      TEXT NOT NULL,
    received_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT chk_payment_inbox_event_type
        CHECK (event_type IN (
                              'PAYMENT_SUCCEEDED',
                              'PAYMENT_FAILED'
                                            )));

-- find events waiting for booking-side business processing
CREATE INDEX idx_payment_inbox_unprocessed
    ON payment_inbox_events (received_at, event_id)
    WHERE processed_at IS NULL;