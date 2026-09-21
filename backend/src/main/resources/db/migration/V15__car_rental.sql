CREATE TABLE rental_cars (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(50) NOT NULL,
    seats INTEGER NOT NULL,
    transmission VARCHAR(30) NOT NULL,
    price_per_day NUMERIC(19,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE rental_bookings (
    id BIGSERIAL PRIMARY KEY,
    booking_id VARCHAR(40) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    car_id BIGINT NOT NULL REFERENCES rental_cars(id),
    pickup_location VARCHAR(300) NOT NULL,
    drop_location VARCHAR(300) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    wallet_ledger_ref VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rental_dates CHECK (end_date > start_date),
    CONSTRAINT chk_rental_amount CHECK (total_amount >= 0)
);

CREATE INDEX idx_rental_bookings_user_created ON rental_bookings(user_id, created_at DESC);
CREATE INDEX idx_rental_bookings_car_dates ON rental_bookings(car_id, start_date, end_date, status);

INSERT INTO rental_cars (name, category, seats, transmission, price_per_day, active)
VALUES
    ('City Compact', 'Hatchback', 5, 'Manual', 1499.00, TRUE),
    ('Urban Sedan', 'Sedan', 5, 'Automatic', 2199.00, TRUE),
    ('Family SUV', 'SUV', 7, 'Automatic', 3299.00, TRUE);
