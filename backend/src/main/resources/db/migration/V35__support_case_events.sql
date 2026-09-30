-- Append-only Customer Care audit trail.

CREATE TABLE IF NOT EXISTS support_case_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(40) NOT NULL UNIQUE,
    case_id BIGINT NOT NULL,
    conversation_id BIGINT NULL,
    customer_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    event_type VARCHAR(60) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    channel VARCHAR(30) NULL,
    summary VARCHAR(500) NOT NULL,
    metadata VARCHAR(5000) NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_support_case_events_customer_created
    ON support_case_events(customer_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_case_events_case_created
    ON support_case_events(case_id, created_at DESC);
