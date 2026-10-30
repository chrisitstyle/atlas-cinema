-- Record successfully completed payment refunds
CREATE TABLE payment_refund_confirmations
(
    event_id                       UUID PRIMARY KEY,
    payment_id                     UUID NOT NULL UNIQUE,
    booking_id                     UUID NOT NULL,
    provider_transaction_id        VARCHAR(128) NOT NULL,
    provider_refund_transaction_id VARCHAR(128) NOT NULL UNIQUE,
    amount                         NUMERIC(12, 2) NOT NULL,
    currency_code                  VARCHAR(3) NOT NULL,
    payload                        TEXT NOT NULL,
    received_at                    TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_payment_refund_confirmation_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_payment_refund_confirmations_booking_id
    ON payment_refund_confirmations (booking_id);