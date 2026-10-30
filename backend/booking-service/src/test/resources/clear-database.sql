TRUNCATE TABLE
    payment_inbox_events,
    booking_seats,
    bookings,
    outbox_events
RESTART IDENTITY CASCADE;