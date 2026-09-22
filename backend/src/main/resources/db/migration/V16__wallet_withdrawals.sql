CREATE TABLE wallet_withdrawals (
    id BIGSERIAL PRIMARY KEY,
    withdrawal_id VARCHAR(40) NOT NULL UNIQUE,
    client_request_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount NUMERIC(19,2) NOT NULL,
    upi_id VARCHAR(254) NOT NULL,
    provider_name VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(150),
    provider_status VARCHAR(50),
    failure_reason VARCHAR(500),
    wallet_ledger_ref VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_wallet_withdrawal_user_request UNIQUE (user_id, client_request_id),
    CONSTRAINT chk_wallet_withdrawal_amount CHECK (amount >= 1.00)
);

CREATE INDEX idx_wallet_withdrawal_user_created
    ON wallet_withdrawals(user_id, created_at DESC);

CREATE INDEX idx_wallet_withdrawal_provider_ref
    ON wallet_withdrawals(provider_name, provider_reference);
