CREATE TABLE rental_vehicle_unavailability (
    id BIGSERIAL PRIMARY KEY,
    car_id BIGINT NOT NULL REFERENCES rental_cars(id),
    vendor_id BIGINT NOT NULL REFERENCES rental_vendors(id),
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    reason_note VARCHAR(300),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rental_vehicle_unavailability_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_rental_unavailability_car_dates
    ON rental_vehicle_unavailability(car_id, start_date, end_date);

CREATE INDEX idx_rental_unavailability_vendor_status
    ON rental_vehicle_unavailability(vendor_id, status, start_date);
