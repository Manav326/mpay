-- Customer Care: unified chat timeline, support ETA metadata and message-intake support.
ALTER TABLE support_cases
    ADD COLUMN IF NOT EXISTS last_meaningful_update_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS expected_resolution_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS eta_source VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

CREATE INDEX IF NOT EXISTS idx_support_cases_customer_meaningful_update
    ON support_cases(customer_user_id, last_meaningful_update_at DESC);