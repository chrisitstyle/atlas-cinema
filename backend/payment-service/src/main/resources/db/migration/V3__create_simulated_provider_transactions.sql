-- persist simulated provider results independently from payment status
CREATE TABLE simulated_provider_transactions
(
    payment_id              UUID PRIMARY KEY,
    amount                  NUMERIC(12, 2) NOT NULL,
    currency_code           VARCHAR(3) NOT NULL,
    outcome                 VARCHAR(16) NOT NULL,
    provider_transaction_id VARCHAR(128) NOT NULL UNIQUE,

    CONSTRAINT chk_simulated_provider_amount
        CHECK (amount > 0),

    CONSTRAINT chk_simulated_provider_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_simulated_provider_outcome
        CHECK (outcome IN ('APPROVED', 'DECLINED'))
);