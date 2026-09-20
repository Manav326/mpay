CREATE TABLE admin_vendors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(40) NOT NULL,
    city VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    commission_rate NUMERIC(7,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_vendors_active_created ON admin_vendors(active, created_at DESC);
CREATE INDEX idx_users_role_created ON users(role, created_at DESC);
