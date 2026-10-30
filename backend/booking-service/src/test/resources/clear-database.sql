TRUNCATE TABLE
    payment_refund_confirmations,
    payment_inbox_events,
    confirmed_seats,
    booking_seats,
    bookings,
    outbox_events
RESTART IDENTITY CASCADE;