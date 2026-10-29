
CREATE TABLE payments (
id UUID PRIMARY KEY,

-- Booking belongs to booking-service, so no cross-service foreign key
booking_id UUID NOT NULL,

-- store monetary values with exact decimal precision
amount NUMERIC(12, 2) NOT NULL,

-- ISO currency code such as PLN or EUR
currency_code VARCHAR(3) NOT NULL,

-- store enum names instead of numeric positions
status VARCHAR(30) NOT NULL,

-- prevent duplicate payment attempts with the same request key
idempotency_key VARCHAR(128) NOT NULL,

-- Store absolute timestamps independently of the server time zone
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

  CONSTRAINT uk_payments_idempotency_key
      UNIQUE (idempotency_key),

  CONSTRAINT chk_payments_amount_positive
      CHECK (amount > 0),

  CONSTRAINT chk_payments_currency_code
      CHECK (currency_code ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_payments_status
        CHECK (status IN (
            'PENDING',
            'SUCCEEDED',
            'FAILED',
            'REFUND_PENDING',
            'REFUNDED'))
);

-- a booking can have multiple payment attempts
CREATE INDEX idx_payments_booking_id
    ON payments (booking_id);
