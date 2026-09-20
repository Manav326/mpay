-- Scalable portal access model.
-- Portal roles log in through the same users/JWT authentication used by clients.
-- Permissions and visibility are data-driven so future roles can be added without code changes.

CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    permission VARCHAR(80) NOT NULL,
    CONSTRAINT uq_role_permissions_role_permission UNIQUE (role, permission)
);

CREATE TABLE IF NOT EXISTS role_hierarchy (
    id BIGSERIAL PRIMARY KEY,
    viewer_role VARCHAR(50) NOT NULL,
    target_role VARCHAR(50) NOT NULL,
    CONSTRAINT uq_role_hierarchy_viewer_target UNIQUE (viewer_role, target_role)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role);
CREATE INDEX IF NOT EXISTS idx_role_hierarchy_viewer ON role_hierarchy(viewer_role);

-- Portal login permissions.
INSERT INTO role_permissions(role, permission) VALUES
    ('ADMIN', 'PORTAL_LOGIN'),
    ('ADMIN', 'VIEW_DASHBOARD'),
    ('ADMIN', 'VIEW_USERS'),
    ('ADMIN', 'VIEW_USER_DETAIL'),
    ('ADMIN', 'MANAGE_VENDORS'),
    ('ADMIN', 'MANAGE_COMMISSION_RATES'),
    ('MANAGER', 'PORTAL_LOGIN'),
    ('MANAGER', 'VIEW_DASHBOARD'),
    ('MANAGER', 'VIEW_USERS'),
    ('MANAGER', 'VIEW_USER_DETAIL')
ON CONFLICT (role, permission) DO NOTHING;

-- Visibility hierarchy. Admin can see all portal roles; manager can see clients only.
INSERT INTO role_hierarchy(viewer_role, target_role) VALUES
    ('ADMIN', 'ADMIN'),
    ('ADMIN', 'MANAGER'),
    ('ADMIN', 'CLIENT'),
    ('MANAGER', 'CLIENT')
ON CONFLICT (viewer_role, target_role) DO NOTHING;

-- Update the existing initial admin to the user's requested local credentials.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_user_id BIGINT;
    v_role VARCHAR(50);
BEGIN
    SELECT id, role INTO v_user_id, v_role FROM users WHERE mobile = '9999999999';
    IF v_user_id IS NULL THEN
        INSERT INTO users(public_id, mobile, name, email, password_hash, role, active, created_at)
        VALUES (gen_random_uuid()::text, '9999999999', 'System Admin', 'admin@mpay.local', crypt('Admin@123', gen_salt('bf', 12)), 'ADMIN', TRUE, CURRENT_TIMESTAMP)
        RETURNING id INTO v_user_id;
    ELSE
        IF UPPER(COALESCE(v_role, '')) <> 'ADMIN' THEN
            RAISE EXCEPTION 'Cannot seed ADMIN 9999999999 because the existing role is %', v_role;
        END IF;
        UPDATE users
           SET name = 'System Admin',
               email = 'admin@mpay.local',
               password_hash = crypt('Admin@123', gen_salt('bf', 12)),
               active = TRUE
         WHERE id = v_user_id;
    END IF;

    INSERT INTO wallets(user_id, balance, reserved_balance, version)
    VALUES (v_user_id, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;
END $$;

-- Initial local MANAGER account for portal testing.
-- Mobile: 9999999998
-- Password: Manager@123
DO $$
DECLARE
    v_user_id BIGINT;
    v_role VARCHAR(50);
BEGIN
    SELECT id, role INTO v_user_id, v_role FROM users WHERE mobile = '9999999998';
    IF v_user_id IS NULL THEN
        INSERT INTO users(public_id, mobile, name, email, password_hash, role, active, created_at)
        VALUES (gen_random_uuid()::text, '9999999998', 'System Manager', 'manager@mpay.local', crypt('Manager@123', gen_salt('bf', 12)), 'MANAGER', TRUE, CURRENT_TIMESTAMP)
        RETURNING id INTO v_user_id;
    ELSE
        IF UPPER(COALESCE(v_role, '')) <> 'MANAGER' THEN
            RAISE EXCEPTION 'Cannot seed MANAGER 9999999998 because the existing role is %', v_role;
        END IF;
        UPDATE users
           SET name = 'System Manager',
               email = 'manager@mpay.local',
               password_hash = crypt('Manager@123', gen_salt('bf', 12)),
               active = TRUE
         WHERE id = v_user_id;
    END IF;

    INSERT INTO wallets(user_id, balance, reserved_balance, version)
    VALUES (v_user_id, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;
END $$;
