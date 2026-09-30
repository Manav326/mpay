-- End-to-end customer voice calling.
-- Voice is never recorded by this feature. The database stores call state only.

CREATE TABLE IF NOT EXISTS voice_calls (
    id BIGSERIAL PRIMARY KEY,
    call_id VARCHAR(40) NOT NULL UNIQUE,
    caller_user_id BIGINT NOT NULL,
    callee_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    ringing_expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    connected_at TIMESTAMPTZ NULL,
    ended_at TIMESTAMPTZ NULL,
    ended_by_user_id BIGINT NULL,
    ended_reason VARCHAR(80) NULL
);

CREATE INDEX IF NOT EXISTS idx_voice_calls_status_expires
    ON voice_calls(status, ringing_expires_at);
CREATE INDEX IF NOT EXISTS idx_voice_calls_caller_created
    ON voice_calls(caller_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_voice_calls_callee_created
    ON voice_calls(callee_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS voice_call_participants (
    id BIGSERIAL PRIMARY KEY,
    call_id VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_voice_call_participants_call
    ON voice_call_participants(call_id);

CREATE TABLE IF NOT EXISTS call_push_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(2048) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_call_push_devices_user_active
    ON call_push_devices(user_id, active);

CREATE TABLE IF NOT EXISTS user_permission_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission VARCHAR(80) NOT NULL,
    allowed BOOLEAN NOT NULL,
    granted_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_user_permission_override UNIQUE(user_id, permission)
);

INSERT INTO role_permissions(role, permission) VALUES
    ('ADMIN', 'CALL_CUSTOMER'),
    ('ADMIN', 'MANAGE_CALL_ACCESS')
ON CONFLICT (role, permission) DO NOTHING;
