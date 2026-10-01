DELETE FROM role_commission_rates
WHERE UPPER(role) IN ('MANAGER', 'ADMIN');

INSERT INTO role_commission_rates(role, commission_percent, active)
VALUES ('CLIENT', 1.0000, TRUE)
ON CONFLICT (role) DO NOTHING;

CREATE TABLE client_referral_links (
    id BIGSERIAL PRIMARY KEY,
    parent_user_id BIGINT NOT NULL REFERENCES users(id),
    child_user_id BIGINT NOT NULL REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_client_referral_child UNIQUE (child_user_id),
    CONSTRAINT ck_client_referral_not_self CHECK (parent_user_id <> child_user_id)
);

CREATE INDEX idx_client_referral_parent
    ON client_referral_links(parent_user_id);

CREATE TABLE client_upstream_commissions (
    id BIGSERIAL PRIMARY KEY,
    parent_user_id BIGINT NOT NULL REFERENCES users(id),
    child_user_id BIGINT NOT NULL REFERENCES users(id),
    recharge_transaction_id VARCHAR(255) NOT NULL REFERENCES recharge_transactions(transaction_id),
    recharge_amount NUMERIC(19,2) NOT NULL,
    commission_percent NUMERIC(7,4) NOT NULL,
    commission_amount NUMERIC(19,2) NOT NULL,
    wallet_ledger_ref VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_client_upstream_recharge UNIQUE (recharge_transaction_id),
    CONSTRAINT ck_client_upstream_amounts CHECK (recharge_amount > 0 AND commission_percent >= 0 AND commission_amount > 0)
);

CREATE INDEX idx_client_upstream_parent_created
    ON client_upstream_commissions(parent_user_id, created_at DESC);

CREATE INDEX idx_client_upstream_child_created
    ON client_upstream_commissions(child_user_id, created_at DESC);

CREATE TABLE client_commission_settings (
    id BIGINT PRIMARY KEY,
    level2_direct_client_threshold INTEGER NOT NULL DEFAULT 5,
    upstream_commission_percent NUMERIC(7,4) NOT NULL DEFAULT 0.1000,
    upstream_commission_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_client_commission_settings_threshold CHECK (level2_direct_client_threshold > 0),
    CONSTRAINT ck_client_commission_settings_percent CHECK (upstream_commission_percent >= 0 AND upstream_commission_percent < 100)
);

INSERT INTO client_commission_settings(
    id,
    level2_direct_client_threshold,
    upstream_commission_percent,
    upstream_commission_active
) VALUES (1, 5, 0.1000, TRUE)
ON CONFLICT (id) DO NOTHING;
