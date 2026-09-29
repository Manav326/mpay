-- Customer Care callback claiming and measurable handling times.

ALTER TABLE support_call_requests
    ADD COLUMN IF NOT EXISTS assigned_user_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(60) NULL,
    ADD COLUMN IF NOT EXISTS outcome_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_support_call_requests_assigned_status
    ON support_call_requests(assigned_user_id, status, requested_at);

ALTER TABLE support_interactions
    ADD COLUMN IF NOT EXISTS ring_duration_seconds BIGINT NULL,
    ADD COLUMN IF NOT EXISTS handling_duration_seconds BIGINT NULL,
    ADD COLUMN IF NOT EXISTS wrap_up_completed_at TIMESTAMPTZ NULL;
