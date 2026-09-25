ALTER TABLE rental_cars
    ADD COLUMN IF NOT EXISTS pickup_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_place_id VARCHAR(255);

ALTER TABLE rental_bookings
    ADD COLUMN IF NOT EXISTS pickup_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_place_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS drop_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS drop_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS drop_place_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_rental_cars_pickup_geo
    ON rental_cars(pickup_latitude, pickup_longitude);

CREATE INDEX IF NOT EXISTS idx_rental_bookings_pickup_geo
    ON rental_bookings(pickup_latitude, pickup_longitude);

CREATE INDEX IF NOT EXISTS idx_rental_bookings_drop_geo
    ON rental_bookings(drop_latitude, drop_longitude);

ALTER TABLE rental_cars
    ADD CONSTRAINT chk_rental_car_pickup_latitude
        CHECK (pickup_latitude IS NULL OR (pickup_latitude BETWEEN -90 AND 90)),
    ADD CONSTRAINT chk_rental_car_pickup_longitude
        CHECK (pickup_longitude IS NULL OR (pickup_longitude BETWEEN -180 AND 180));

ALTER TABLE rental_bookings
    ADD CONSTRAINT chk_rental_booking_pickup_latitude
        CHECK (pickup_latitude IS NULL OR (pickup_latitude BETWEEN -90 AND 90)),
    ADD CONSTRAINT chk_rental_booking_pickup_longitude
        CHECK (pickup_longitude IS NULL OR (pickup_longitude BETWEEN -180 AND 180)),
    ADD CONSTRAINT chk_rental_booking_drop_latitude
        CHECK (drop_latitude IS NULL OR (drop_latitude BETWEEN -90 AND 90)),
    ADD CONSTRAINT chk_rental_booking_drop_longitude
        CHECK (drop_longitude IS NULL OR (drop_longitude BETWEEN -180 AND 180));
