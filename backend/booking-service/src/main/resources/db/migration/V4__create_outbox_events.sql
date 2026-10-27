CREATE TABLE outbox_events (
-- unique id of this event
   id UUID PRIMARY KEY,

-- Id of the aggregate this event belongs to, here it is the booking id
   aggregate_id UUID NOT NULL,

-- logical event name such as BookingCancelled
   event_type VARCHAR(100) NOT NULL,

-- serialized event body that will later be sent to Kafka
   payload TEXT NOT NULL,

-- moment when the event was created
   created_at TIMESTAMP NOT NULL,

-- Null means the event is still waiting to be published
   published_at TIMESTAMP
);

-- helps the publisher quickly find events that still need to be sent
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;