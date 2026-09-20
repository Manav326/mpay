ALTER TABLE wallet_transactions
    ADD COLUMN reference_type VARCHAR(40),
    ADD COLUMN reference_id VARCHAR(150),
    ADD COLUMN description VARCHAR(300);

ALTER TABLE recharge_transactions
    ADD COLUMN plan_description VARCHAR(1000),
    ADD COLUMN plan_validity VARCHAR(100),
    ADD COLUMN provider_order_id VARCHAR(150),
    ADD COLUMN wallet_ledger_ref VARCHAR(150);

ALTER TABLE recharge_transactions
    ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE recharge_transactions
SET completed_at = updated_at
WHERE status IN ('SUCCESS', 'FAILED')
  AND completed_at IS NULL;

CREATE INDEX idx_recharge_user_created_status
    ON recharge_transactions(user_id, created_at DESC, status);

CREATE INDEX idx_recharge_user_commission_created
    ON recharge_transactions(user_id, created_at DESC, client_commission);

CREATE TABLE recharge_offer_cache (
    id BIGSERIAL PRIMARY KEY,
    cache_key VARCHAR(220) NOT NULL,
    offer_id VARCHAR(150) NOT NULL,
    mobile_number VARCHAR(15) NOT NULL,
    operator VARCHAR(30) NOT NULL,
    circle VARCHAR(100) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    validity VARCHAR(100),
    description VARCHAR(1000),
    provider_reference VARCHAR(150),
    provider_order_id VARCHAR(150),
    provider_log_description VARCHAR(1500),
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_offer_cache_key_offer UNIQUE(cache_key, offer_id)
);

CREATE INDEX idx_offer_cache_key_expiry
    ON recharge_offer_cache(cache_key, expires_at);

CREATE INDEX idx_offer_cache_offer_expiry
    ON recharge_offer_cache(offer_id, expires_at);
