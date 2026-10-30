-- Remember which payment successfully confirmed a booking
ALTER TABLE bookings
    ADD COLUMN confirmed_payment_id UUID;

-- One successful payment must not confirm two different bookings
CREATE UNIQUE INDEX uq_bookings_confirmed_payment_id
    ON bookings (confirmed_payment_id)
    WHERE confirmed_payment_id IS NOT NULL;