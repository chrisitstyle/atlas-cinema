ALTER TABLE bookings
    ADD COLUMN guest_payment_token_hash VARCHAR(64);

-- only anonymous bookings may have a guest payment token
ALTER TABLE bookings
    ADD CONSTRAINT chk_guest_payment_token_owner
        CHECK (
            guest_payment_token_hash IS NULL
                OR customer_id IS NULL);
