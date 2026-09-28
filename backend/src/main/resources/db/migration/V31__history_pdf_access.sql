-- Per-user access control for sensitive history PDF exports.
CREATE TABLE IF NOT EXISTS history_pdf_access_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    feature_key VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_reason VARCHAR(1000) NOT NULL,
    review_note VARCHAR(1000),
    reviewed_by BIGINT REFERENCES users(id),
    requested_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_history_pdf_access_user_status
    ON history_pdf_access_requests(user_id, status);

CREATE INDEX IF NOT EXISTS idx_history_pdf_access_status_requested
    ON history_pdf_access_requests(status, requested_at);

INSERT INTO role_permissions(role, permission)
VALUES ('ADMIN', 'MANAGE_HISTORY_PDF_ACCESS')
ON CONFLICT (role, permission) DO NOTHING;
