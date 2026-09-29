-- Two-way customer/support chat messages.
-- Messages are retained as support records; no voice audio is stored.

CREATE TABLE IF NOT EXISTS support_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(40) NOT NULL UNIQUE,
    conversation_id BIGINT NOT NULL,
    case_id BIGINT NULL,
    customer_user_id BIGINT NOT NULL,
    sender_user_id BIGINT NULL,
    sender_type VARCHAR(20) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    customer_read_at TIMESTAMPTZ NULL,
    staff_read_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_support_messages_conversation_created
    ON support_messages(conversation_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_support_messages_customer_created
    ON support_messages(customer_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_support_messages_customer_staff_read
    ON support_messages(customer_user_id, staff_read_at);
