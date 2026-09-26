ALTER TABLE password_reset_otps
    DROP CONSTRAINT IF EXISTS password_reset_otps_mobile_key;

ALTER TABLE password_reset_otps
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(32) NOT NULL DEFAULT 'PASSWORD_RESET',
    ADD COLUMN IF NOT EXISTS last_sent_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS send_window_started_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS send_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS verification_token_hash VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS verification_token_expires_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_password_reset_otps_mobile_purpose
    ON password_reset_otps(mobile, purpose);

CREATE INDEX IF NOT EXISTS idx_password_reset_otps_mobile_purpose
    ON password_reset_otps(mobile, purpose);

CREATE INDEX IF NOT EXISTS idx_password_reset_otps_token_expiry
    ON password_reset_otps(verification_token_expires_at);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mobile_verified_at TIMESTAMPTZ NULL;
