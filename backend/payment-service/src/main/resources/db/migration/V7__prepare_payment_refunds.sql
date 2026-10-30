-- keep the provider refund reference for audit and reconciliation
ALTER TABLE payments
    ADD COLUMN provider_refund_transaction_id VARCHAR(128);

CREATE UNIQUE INDEX uk_payments_provider_refund_transaction_id
    ON payments (provider_refund_transaction_id)
    WHERE provider_refund_transaction_id IS NOT NULL;

-- simulate a payment provider's durable refund history
CREATE TABLE simulated_provider_refunds
(
    payment_id                     UUID PRIMARY KEY,
    provider_transaction_id        VARCHAR(128) NOT NULL,
    amount                         NUMERIC(12, 2) NOT NULL,
    currency_code                  VARCHAR(3) NOT NULL,
    provider_refund_transaction_id VARCHAR(128) NOT NULL UNIQUE,
    created_at                     TIMESTAMP(6) WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_simulated_refund_amount
        CHECK (amount > 0),

    CONSTRAINT chk_simulated_refund_currency
        CHECK (currency_code ~ '^[A-Z]{3}$')
    );

-- allow failed refund processing to be retried without starving newer events
ALTER TABLE payment_refund_inbox_events
    ADD COLUMN last_attempt_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_payment_refund_inbox_retry
    ON payment_refund_inbox_events (last_attempt_at, received_at)
    WHERE processed_at IS NULL;