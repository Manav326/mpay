CREATE TABLE rental_vendors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    vendor_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    full_name VARCHAR(120) NOT NULL,
    business_name VARCHAR(160),
    address VARCHAR(300) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pin_code VARCHAR(10) NOT NULL,
    pan_number VARCHAR(20),
    payout_upi_id VARCHAR(254),
    bank_account_number VARCHAR(64),
    bank_ifsc VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rental_drivers (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES rental_vendors(id),
    full_name VARCHAR(120) NOT NULL,
    mobile VARCHAR(20) NOT NULL,
    license_number VARCHAR(64) NOT NULL,
    license_expiry DATE NOT NULL,
    address VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE rental_cars
    ADD COLUMN vendor_id BIGINT REFERENCES rental_vendors(id),
    ADD COLUMN driver_id BIGINT REFERENCES rental_drivers(id),
    ADD COLUMN registration_number VARCHAR(32),
    ADD COLUMN make VARCHAR(80),
    ADD COLUMN model VARCHAR(80),
    ADD COLUMN variant VARCHAR(80),
    ADD COLUMN manufacturing_year INTEGER,
    ADD COLUMN fuel_type VARCHAR(30),
    ADD COLUMN registration_year INTEGER,
    ADD COLUMN pickup_address VARCHAR(300),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state VARCHAR(100),
    ADD COLUMN image_url VARCHAR(500),
    ADD COLUMN approval_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT';

UPDATE rental_cars
SET active = FALSE, approval_status = 'LEGACY_DISABLED';

CREATE UNIQUE INDEX uq_rental_cars_registration
    ON rental_cars(registration_number)
    WHERE registration_number IS NOT NULL;

CREATE INDEX idx_rental_vendors_user ON rental_vendors(user_id);
CREATE INDEX idx_rental_vendors_status ON rental_vendors(status);
CREATE INDEX idx_rental_cars_vendor ON rental_cars(vendor_id);
CREATE INDEX idx_rental_cars_marketplace ON rental_cars(active, approval_status, city);
CREATE INDEX idx_rental_drivers_vendor ON rental_drivers(vendor_id);
