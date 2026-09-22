CREATE TABLE rental_payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(40) NOT NULL UNIQUE,
    booking_id VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount NUMERIC(19,2) NOT NULL,
    method VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(150),
    client_request_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    wallet_ledger_ref VARCHAR(150),
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rental_payment_user_request UNIQUE (user_id, client_request_id)
);

CREATE INDEX idx_rental_payment_user_created
    ON rental_payments(user_id, created_at DESC);

CREATE INDEX idx_rental_payment_booking
    ON rental_payments(booking_id);

ALTER TABLE rental_bookings
    ADD COLUMN payment_id BIGINT REFERENCES rental_payments(id);

INSERT INTO rental_payments (
    payment_id, booking_id, user_id, amount, method, provider,
    provider_transaction_id, client_request_id, status, wallet_ledger_ref,
    created_at, updated_at
)
SELECT
    'RNP-L-' || md5(b.booking_id),
    b.booking_id,
    b.user_id,
    b.total_amount,
    b.payment_method,
    'INTERNAL_WALLET',
    b.wallet_ledger_ref,
    'LEGACY:' || b.booking_id,
    CASE WHEN b.status = 'CANCELLED' THEN 'REFUNDED' ELSE 'PAID' END,
    b.wallet_ledger_ref,
    b.created_at,
    b.updated_at
FROM rental_bookings b
WHERE NOT EXISTS (
    SELECT 1 FROM rental_payments p WHERE p.booking_id = b.booking_id
);

UPDATE rental_bookings b
SET payment_id = p.id
FROM rental_payments p
WHERE p.booking_id = b.booking_id
  AND b.payment_id IS NULL;

CREATE UNIQUE INDEX uq_rental_booking_payment
    ON rental_bookings(payment_id)
    WHERE payment_id IS NOT NULL;
