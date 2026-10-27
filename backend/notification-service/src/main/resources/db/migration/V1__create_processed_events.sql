CREATE TABLE processed_events (
-- unique Kafka event id used for duplicate detection
event_id UUID PRIMARY KEY,

-- logical event type such as BookingCancelled
event_type VARCHAR(100) NOT NULL,

-- moment when this consumer successfully processed the event
processed_at TIMESTAMP NOT NULL
);