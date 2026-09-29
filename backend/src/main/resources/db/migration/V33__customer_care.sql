-- Modular customer-care foundation.
-- Ticket state and conversation history are authoritative support records.
-- Attachments, automation and knowledge-base content can be layered on later
-- without changing the ticket/message contract.

CREATE TABLE IF NOT EXISTS support_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_id VARCHAR(40) NOT NULL UNIQUE,
    customer_user_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    assigned_agent_user_id BIGINT NULL,
    subject VARCHAR(180) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ NULL,
    closed_at TIMESTAMPTZ NULL,
    last_customer_reply_at TIMESTAMPTZ NULL,
    last_agent_reply_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_support_tickets_customer_updated
    ON support_tickets(customer_user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_support_tickets_queue
    ON support_tickets(status, priority, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_support_tickets_assignee
    ON support_tickets(assigned_agent_user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS support_ticket_messages (
    id BIGSERIAL PRIMARY KEY,
    ticket_id VARCHAR(40) NOT NULL,
    sender_user_id BIGINT NOT NULL,
    sender_role VARCHAR(30) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_messages_ticket_created
    ON support_ticket_messages(ticket_id, created_at ASC);

INSERT INTO role_permissions(role, permission)
VALUES
    ('ADMIN', 'VIEW_CUSTOMER_CARE'),
    ('ADMIN', 'MANAGE_CUSTOMER_CARE'),
    ('MANAGER', 'VIEW_CUSTOMER_CARE'),
    ('MANAGER', 'MANAGE_CUSTOMER_CARE')
ON CONFLICT (role, permission) DO NOTHING;
