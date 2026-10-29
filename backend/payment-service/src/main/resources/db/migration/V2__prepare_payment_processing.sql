-- store the external provider transaction identifier
ALTER TABLE payments
    ADD COLUMN provider_transaction_id VARCHAR(128);

-- allow the new PROCESSING payment status
ALTER TABLE payments
DROP CONSTRAINT chk_payments_status;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_status
        CHECK (status IN (
                          'PENDING',
                          'PROCESSING',
                          'SUCCEEDED',
                          'FAILED',
                          'REFUND_PENDING',
                          'REFUNDED'
                                    ));

-- prevent different payments from storing the same provider transaction
CREATE UNIQUE INDEX idx_payments_provider_transaction_id
    ON payments (provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;