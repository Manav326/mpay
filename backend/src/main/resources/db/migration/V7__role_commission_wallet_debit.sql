ALTER TABLE recharge_transactions
    ADD COLUMN wallet_debit_amount NUMERIC(19,2);

UPDATE recharge_transactions
SET wallet_debit_amount = GREATEST(amount - client_commission, 0);

ALTER TABLE recharge_transactions
    ALTER COLUMN wallet_debit_amount SET NOT NULL;

CREATE TABLE role_commission_rates (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(30) NOT NULL UNIQUE,
    commission_percent NUMERIC(7,4) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO role_commission_rates(role, commission_percent, active) VALUES
    ('CLIENT', 1.0000, TRUE),
    ('MANAGER', 1.0000, TRUE),
    ('ADMIN', 0.0000, TRUE)
ON CONFLICT (role) DO NOTHING;
