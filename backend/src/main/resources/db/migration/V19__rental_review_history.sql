CREATE TABLE rental_vendor_review_history (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES rental_vendors(id),
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rental_car_review_history (
    id BIGSERIAL PRIMARY KEY,
    car_id BIGINT NOT NULL REFERENCES rental_cars(id),
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rental_vendor_review_history_vendor
    ON rental_vendor_review_history(vendor_id, created_at DESC);

CREATE INDEX idx_rental_car_review_history_car
    ON rental_car_review_history(car_id, created_at DESC);
