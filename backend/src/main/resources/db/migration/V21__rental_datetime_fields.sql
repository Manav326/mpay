ALTER TABLE rental_bookings
    ALTER COLUMN start_date TYPE TIMESTAMP WITHOUT TIME ZONE
    USING start_date::timestamp,
    ALTER COLUMN end_date TYPE TIMESTAMP WITHOUT TIME ZONE
    USING end_date::timestamp;

ALTER TABLE rental_drivers
    ALTER COLUMN license_expiry TYPE TIMESTAMP WITHOUT TIME ZONE
    USING license_expiry::timestamp;
