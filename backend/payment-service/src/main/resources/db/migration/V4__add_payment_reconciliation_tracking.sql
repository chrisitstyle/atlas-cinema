-- track reconciliation attempts independently from payment status changes
ALTER TABLE payments
    ADD COLUMN last_reconciliation_attempt_at TIMESTAMP(6) WITH TIME ZONE;

-- help find payments waiting for reconciliation
CREATE INDEX idx_payments_reconciliation_candidates
    ON payments (updated_at, last_reconciliation_attempt_at)
    WHERE status = 'PROCESSING';