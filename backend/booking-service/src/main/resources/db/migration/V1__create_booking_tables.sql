CREATE TABLE bookings (
id UUID PRIMARY KEY,

customer_id UUID,
contact_email VARCHAR(255) NOT NULL,

screening_id UUID NOT NULL,

status VARCHAR(32) NOT NULL,

created_at TIMESTAMP NOT NULL,
expires_at TIMESTAMP,

total_price NUMERIC(10, 2) NOT NULL,

    CONSTRAINT chk_booking_total_price
      CHECK (total_price >= 0)
);


CREATE TABLE booking_seats (
id UUID PRIMARY KEY,

booking_id UUID NOT NULL,
screening_id UUID NOT NULL,
seat_id UUID NOT NULL,

price NUMERIC(10, 2) NOT NULL,

    CONSTRAINT fk_booking_seat_booking
       FOREIGN KEY (booking_id)
           REFERENCES bookings(id),

    CONSTRAINT uq_booking_screening_seat
       UNIQUE (screening_id, seat_id),

    CONSTRAINT chk_booking_seat_price
       CHECK (price >= 0)
);


CREATE INDEX idx_bookings_screening_id
    ON bookings(screening_id);

CREATE INDEX idx_bookings_customer_id
    ON bookings(customer_id);

CREATE INDEX idx_booking_seats_booking_id
    ON booking_seats(booking_id);