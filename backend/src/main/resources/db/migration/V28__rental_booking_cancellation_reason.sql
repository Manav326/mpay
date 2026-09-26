ALTER TABLE rental_bookings
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);
