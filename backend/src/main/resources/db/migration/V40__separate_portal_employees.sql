-- Separate portal staff from customer accounts.
-- Customer records remain in users; all portal staff live in employees.
-- Employee ids intentionally share the users_id_seq sequence so existing support/
-- voice/account references can be migrated without id collisions.

CREATE TABLE IF NOT EXISTS employees (
    id BIGINT PRIMARY KEY DEFAULT nextval('users_id_seq'),
    public_id VARCHAR(36) NOT NULL UNIQUE,
    mobile VARCHAR(15) NOT NULL UNIQUE,
    name VARCHAR(120),
    email VARCHAR(254) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    profile_image_key VARCHAR(255),
    profile_image_content_type VARCHAR(100),
    profile_image_updated_at TIMESTAMPTZ NULL,
    profile_updated_at TIMESTAMPTZ NULL,
    last_login_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_employees_role_active
    ON employees(role, active);
CREATE INDEX IF NOT EXISTS idx_employees_created
    ON employees(created_at DESC);

CREATE TABLE IF NOT EXISTS employee_permission_overrides (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    permission VARCHAR(80) NOT NULL,
    allowed BOOLEAN NOT NULL,
    changed_by_employee_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_employee_permission_override UNIQUE(employee_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_employee_permission_overrides_employee
    ON employee_permission_overrides(employee_id);

CREATE TABLE IF NOT EXISTS employee_activity (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    subject_type VARCHAR(50),
    subject_id VARCHAR(120),
    summary VARCHAR(500) NOT NULL,
    metadata_json TEXT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_employee_activity_employee_time
    ON employee_activity(employee_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_employee_activity_subject_time
    ON employee_activity(subject_type, subject_id, occurred_at DESC);

-- The application previously seeded the portal Admin and Manager in users.
-- Move those portal accounts to employees while preserving their ids so existing
-- operational/audit references remain stable. Abort rather than silently moving
-- unexpected non-customer accounts.
DO $$
DECLARE
    v_user_id BIGINT;
    v_role VARCHAR(50);
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE UPPER(role) <> 'CLIENT'
          AND mobile NOT IN ('9999999999', '9999999998')
    ) THEN
        RAISE EXCEPTION
            'Portal employee migration stopped: users contains a non-client account other than the seeded Admin/Manager. Review this account before migration.';
    END IF;

    FOR v_user_id, v_role IN
        SELECT id, role
        FROM users
        WHERE mobile IN ('9999999999', '9999999998')
          AND UPPER(role) IN ('ADMIN', 'MANAGER')
    LOOP
        INSERT INTO employees (
            id, public_id, mobile, name, email, password_hash, role, active, created_at, updated_at
        )
        SELECT
            id, public_id, mobile, name, email, password_hash, role, active, created_at, CURRENT_TIMESTAMP
        FROM users
        WHERE id = v_user_id
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO employee_permission_overrides(
            employee_id, permission, allowed, changed_by_employee_id, created_at, updated_at
        )
        SELECT
            o.user_id, o.permission, o.allowed, o.granted_by_user_id, o.created_at, o.updated_at
        FROM user_permission_overrides o
        WHERE o.user_id = v_user_id
        ON CONFLICT (employee_id, permission) DO NOTHING;

        INSERT INTO employee_activity(employee_id, action, subject_type, subject_id, summary)
        VALUES (
            v_user_id,
            'ACCOUNT_MIGRATED',
            'EMPLOYEE',
            v_user_id::text,
            'Existing portal account moved from the customer account store to the employee account store.'
        );

        DELETE FROM wallets WHERE user_id = v_user_id;
        DELETE FROM users WHERE id = v_user_id;
    END LOOP;

    PERFORM setval(
        'users_id_seq',
        GREATEST(
            COALESCE((SELECT MAX(id) FROM users), 0),
            COALESCE((SELECT MAX(id) FROM employees), 0)
        )
    );
END $$;
