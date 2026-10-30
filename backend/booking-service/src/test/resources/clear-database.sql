TRUNCATE TABLE
    payment_inbox_events,
    confirmed_seats,
    booking_seats,
    bookings,
    outbox_events
RESTART IDENTITY CASCADE;