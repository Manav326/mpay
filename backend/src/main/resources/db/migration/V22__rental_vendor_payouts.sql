CREATE TABLE rental_payouts (
    id BIGSERIAL PRIMARY KEY,
    payout_id VARCHAR(40) NOT NULL UNIQUE,
    booking_id VARCHAR(40) NOT NULL UNIQUE,
    vendor_id BIGINT NOT NULL REFERENCES rental_vendors(id),
    vendor_user_id BIGINT NOT NULL REFERENCES users(id),
    gross_amount NUMERIC(19,2) NOT NULL,
    platform_fee_percent NUMERIC(7,4) NOT NULL,
    platform_fee_amount NUMERIC(19,2) NOT NULL,
    vendor_net_amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    wallet_ledger_ref VARCHAR(150),
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMPTZ
);

CREATE INDEX idx_rental_payout_vendor_created
    ON rental_payouts(vendor_id, created_at DESC);

CREATE INDEX idx_rental_payout_user_created
    ON rental_payouts(vendor_user_id, created_at DESC);
