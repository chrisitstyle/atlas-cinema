-- allow payment refund completion events in the existing outbox
ALTER TABLE payment_outbox_events
DROP CONSTRAINT chk_payment_outbox_event_type;

ALTER TABLE payment_outbox_events
    ADD CONSTRAINT chk_payment_outbox_event_type
        CHECK (event_type IN (
          'PAYMENT_SUCCEEDED',
          'PAYMENT_FAILED',
          'PAYMENT_REFUNDED'));