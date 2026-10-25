CREATE TABLE screenings (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL,
    hall_id UUID NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,

    CONSTRAINT chk_screening_time
        CHECK (ends_at > starts_at),

    CONSTRAINT chk_screening_base_price
        CHECK (base_price > 0)
);

CREATE INDEX idx_screenings_hall_time
    ON screenings(hall_id, starts_at, ends_at);