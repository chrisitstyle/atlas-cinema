-- Confirmed seats remain reserved after Redis holds expire
CREATE TABLE confirmed_seats (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    screening_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    confirmed_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_confirmed_seats_screening_seat UNIQUE (screening_id, seat_id),
    CONSTRAINT uq_confirmed_seats_booking_seat UNIQUE (booking_id, seat_id)
);

CREATE INDEX idx_confirmed_seats_booking_id
    ON confirmed_seats (booking_id);
