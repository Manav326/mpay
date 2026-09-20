CREATE TABLE password_reset_otps (
    id BIGSERIAL PRIMARY KEY,
    mobile VARCHAR(10) NOT NULL UNIQUE,
    otp_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_password_reset_otps_expires_at
    ON password_reset_otps(expires_at);
