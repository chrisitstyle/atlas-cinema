ALTER TABLE booking_seats
DROP CONSTRAINT uq_booking_screening_seat;

ALTER TABLE booking_seats
DROP COLUMN screening_id;

CREATE TABLE seat_holds
(
    id           UUID PRIMARY KEY,
    booking_id   UUID      NOT NULL,
    screening_id UUID      NOT NULL,
    seat_id      UUID      NOT NULL,
    expires_at   TIMESTAMP NOT NULL,

    CONSTRAINT fk_seat_hold_booking
        FOREIGN KEY (booking_id)
            REFERENCES bookings (id),

    CONSTRAINT uq_seat_hold_screening_seat
        UNIQUE (screening_id, seat_id));

CREATE INDEX idx_seat_holds_booking_id
    ON seat_holds(booking_id);

CREATE INDEX idx_seat_holds_expires_at
    ON seat_holds(expires_at);