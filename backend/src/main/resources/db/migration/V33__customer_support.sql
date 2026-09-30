-- Customer Care foundation: cases, conversations, interactions and callback requests.
-- Voice audio is never persisted. voice_calls remains the technical real-time call source of truth.

CREATE TABLE IF NOT EXISTS support_cases (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(40) NOT NULL UNIQUE,
    customer_user_id BIGINT NOT NULL,
    subject VARCHAR(240) NOT NULL,
    category VARCHAR(80) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    source VARCHAR(40) NOT NULL,
    assigned_user_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ NULL,
    resolution_code VARCHAR(100) NULL,
    resolution_note VARCHAR(1200) NULL
);

CREATE INDEX IF NOT EXISTS idx_support_cases_customer_created
    ON support_cases(customer_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_cases_status_updated
    ON support_cases(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS support_conversations (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(40) NOT NULL UNIQUE,
    case_id BIGINT NULL,
    customer_user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    started_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_support_conversations_customer_activity
    ON support_conversations(customer_user_id, last_activity_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_conversations_case
    ON support_conversations(case_id);

CREATE TABLE IF NOT EXISTS support_interactions (
    id BIGSERIAL PRIMARY KEY,
    interaction_id VARCHAR(40) NOT NULL UNIQUE,
    conversation_id BIGINT NOT NULL,
    case_id BIGINT NULL,
    customer_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    channel VARCHAR(30) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NULL,
    duration_seconds BIGINT NULL,
    outcome VARCHAR(120) NULL,
    voice_call_id VARCHAR(40) NULL,
    chat_thread_id VARCHAR(80) NULL,
    metadata TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_support_interactions_voice_call
    ON support_interactions(voice_call_id)
    WHERE voice_call_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_support_interactions_customer_started
    ON support_interactions(customer_user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_interactions_conversation_started
    ON support_interactions(conversation_id, started_at DESC);

CREATE TABLE IF NOT EXISTS support_notes (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NULL,
    conversation_id BIGINT NULL,
    customer_user_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    note VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_support_notes_customer_created
    ON support_notes(customer_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_notes_case_created
    ON support_notes(case_id, created_at DESC);

CREATE TABLE IF NOT EXISTS support_call_requests (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(40) NOT NULL UNIQUE,
    customer_user_id BIGINT NOT NULL,
    case_id BIGINT NULL,
    conversation_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500) NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at TIMESTAMPTZ NULL,
    review_note VARCHAR(1000) NULL,
    voice_call_id VARCHAR(40) NULL
);

CREATE INDEX IF NOT EXISTS idx_support_call_requests_status_requested
    ON support_call_requests(status, requested_at ASC);
CREATE INDEX IF NOT EXISTS idx_support_call_requests_customer_requested
    ON support_call_requests(customer_user_id, requested_at DESC);

INSERT INTO role_permissions(role, permission) VALUES
    ('ADMIN', 'SUPPORT_VIEW'),
    ('ADMIN', 'SUPPORT_MANAGE'),
    ('MANAGER', 'SUPPORT_VIEW'),
    ('MANAGER', 'SUPPORT_MANAGE')
ON CONFLICT (role, permission) DO NOTHING;

-- Clients do not receive callback permission by default.
-- Admin grants REQUEST_SUPPORT_CALL per customer through Customer Care / Voice & Access.
