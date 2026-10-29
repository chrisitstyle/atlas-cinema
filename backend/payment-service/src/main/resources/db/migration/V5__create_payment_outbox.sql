-- store payment events until they are successfully published
CREATE TABLE payment_outbox_events
(
    id           UUID PRIMARY KEY,
    payment_id   UUID NOT NULL,
    event_type   VARCHAR(32) NOT NULL,
    payload      TEXT NOT NULL,
    occurred_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT uk_payment_outbox_payment_type
        UNIQUE (payment_id, event_type),

    CONSTRAINT chk_payment_outbox_event_type
        CHECK (event_type IN (
                  'PAYMENT_SUCCEEDED',
                  'PAYMENT_FAILED'))
);

-- support fetching unpublished events in chronological order
CREATE INDEX idx_payment_outbox_unpublished
    ON payment_outbox_events (occurred_at, id)
    WHERE published_at IS NULL;