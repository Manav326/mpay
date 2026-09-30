-- Separate portal staff from customer accounts.
-- Existing seeded portal accounts are copied into employees with an independent
-- employee id sequence. Their users rows remain ordinary customer accounts.

CREATE TABLE IF NOT EXISTS employees (
    id BIGSERIAL PRIMARY KEY,
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
    CONSTRAINT uq_employee_permission_override UNIQUE(employee_id, permission),
    CONSTRAINT fk_employee_permission_override_employee
        FOREIGN KEY(employee_id) REFERENCES employees(id),
    CONSTRAINT fk_employee_permission_override_changed_by
        FOREIGN KEY(changed_by_employee_id) REFERENCES employees(id)
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
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_activity_employee
        FOREIGN KEY(employee_id) REFERENCES employees(id)
);

CREATE INDEX IF NOT EXISTS idx_employee_activity_employee_time
    ON employee_activity(employee_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_employee_activity_subject_time
    ON employee_activity(subject_type, subject_id, occurred_at DESC);

-- The application previously seeded the portal Admin and Manager in users.
-- Move those portal accounts to employees while preserving their ids so existing
-- operational/audit references remain stable. Abort rather than silently moving
-- unexpected portal accounts.
DO $$
DECLARE
    v_count INTEGER;
    v_user_id BIGINT;
    v_employee_id BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE UPPER(role) NOT IN ('CLIENT', 'DELETED')
          AND mobile NOT IN ('9999999999', '9999999998')
    ) THEN
        RAISE EXCEPTION
            'Portal employee migration stopped: users contains an unexpected non-client/non-deleted account outside the seeded Admin/Manager accounts.';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM users
     WHERE mobile IN ('9999999999', '9999999998')
       AND UPPER(role) IN ('ADMIN', 'MANAGER');

    IF v_count <> 2 THEN
        RAISE EXCEPTION
            'Portal employee migration stopped: expected both seeded Admin and Manager accounts.';
    END IF;

    FOR v_user_id IN
        SELECT id
          FROM users
         WHERE mobile IN ('9999999999', '9999999998')
           AND UPPER(role) IN ('ADMIN', 'MANAGER')
         ORDER BY id
    LOOP
        INSERT INTO employees (
            public_id, mobile, name, email, password_hash, role, active,
            profile_image_key, profile_image_content_type,
            profile_image_updated_at, profile_updated_at, last_login_at,
            created_at, updated_at
        )
        SELECT
            gen_random_uuid()::text,
            u.mobile, u.name, u.email, u.password_hash, u.role, TRUE,
            u.profile_image_key, u.profile_image_content_type,
            u.profile_image_updated_at, u.profile_updated_at, u.last_login_at,
            u.created_at, CURRENT_TIMESTAMP
        FROM users u
        WHERE u.id = v_user_id;
    END LOOP;

    FOR v_user_id IN
        SELECT id
          FROM users
         WHERE mobile IN ('9999999999', '9999999998')
           AND UPPER(role) IN ('ADMIN', 'MANAGER')
         ORDER BY id
    LOOP
        SELECT e.id
          INTO v_employee_id
          FROM employees e
         WHERE e.mobile = (SELECT u.mobile FROM users u WHERE u.id = v_user_id);

        IF v_employee_id IS NULL THEN
            RAISE EXCEPTION
                'Portal employee migration stopped: copied employee not found for legacy user %.', v_user_id;
        END IF;

        IF EXISTS (
            SELECT 1
              FROM user_permission_overrides o
              LEFT JOIN users granter ON granter.id = o.granted_by_user_id
             WHERE o.user_id = v_user_id
               AND (
                   granter.id IS NULL
                   OR granter.mobile NOT IN ('9999999999', '9999999998')
                   OR UPPER(granter.role) NOT IN ('ADMIN', 'MANAGER')
               )
        ) THEN
            RAISE EXCEPTION
                'Portal employee migration stopped: a legacy staff permission override has a non-staff grantor.';
        END IF;

        INSERT INTO employee_permission_overrides (
            employee_id, permission, allowed, changed_by_employee_id, created_at, updated_at
        )
        SELECT
            v_employee_id,
            o.permission,
            o.allowed,
            granter_employee.id,
            o.created_at,
            o.updated_at
        FROM user_permission_overrides o
        JOIN users granter_user ON granter_user.id = o.granted_by_user_id
        JOIN employees granter_employee ON granter_employee.mobile = granter_user.mobile
        WHERE o.user_id = v_user_id
        ON CONFLICT (employee_id, permission) DO NOTHING;

        INSERT INTO employee_activity(employee_id, action, subject_type, subject_id, summary)
        VALUES (
            v_employee_id,
            'ACCOUNT_MIGRATED',
            'EMPLOYEE',
            v_employee_id::text,
            'Legacy portal account copied into the employee account store.'
        );
    END LOOP;

    UPDATE users
       SET role = 'CLIENT',
           active = TRUE
     WHERE mobile IN ('9999999999', '9999999998')
       AND UPPER(role) IN ('ADMIN', 'MANAGER');

    DELETE FROM user_permission_overrides
     WHERE user_id IN (
         SELECT id FROM users
         WHERE mobile IN ('9999999999', '9999999998')
     );
END $$;