CREATE TABLE cinemas (
 id UUID PRIMARY KEY,
 name VARCHAR(255) NOT NULL,
 city VARCHAR(120) NOT NULL,
 address VARCHAR(500) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE halls (
id UUID PRIMARY KEY,
cinema_id UUID NOT NULL,
name VARCHAR(100) NOT NULL,

CONSTRAINT fk_halls_cinema
   FOREIGN KEY (cinema_id)
       REFERENCES cinemas(id),

CONSTRAINT uk_halls_cinema_name
   UNIQUE (cinema_id, name)
);

CREATE INDEX idx_halls_cinema_id
    ON halls(cinema_id);


CREATE TABLE seats (
id UUID PRIMARY KEY,
hall_id UUID NOT NULL,
seat_row VARCHAR(10) NOT NULL,
seat_number INTEGER NOT NULL,
type VARCHAR(32) NOT NULL,

CONSTRAINT fk_seats_hall
   FOREIGN KEY (hall_id)
       REFERENCES halls(id),

CONSTRAINT uk_seats_hall_row_number
   UNIQUE (hall_id, seat_row, seat_number),

CONSTRAINT chk_seats_number_positive
   CHECK (seat_number > 0)
);

CREATE INDEX idx_seats_hall_id
    ON seats(hall_id);