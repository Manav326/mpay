CREATE TABLE payment_orders (
    id BIGSERIAL PRIMARY KEY,
    client_request_id VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    razorpay_order_id VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL,
    razorpay_payment_id VARCHAR(100) UNIQUE,
    razorpay_signature VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMPTZ,
    version BIGINT
);

CREATE INDEX idx_payment_orders_user_created ON payment_orders(user_id, created_at DESC);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);
