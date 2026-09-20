-- Seed one initial ADMIN account directly in PostgreSQL.
-- This intentionally avoids application-level/bootstrap environment credentials.
-- Demo credentials for the first local admin:
-- mobile:   9999999999
-- password: ChangeMeImmediately!

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_user_id BIGINT;
    v_role VARCHAR(30);
BEGIN
    SELECT id, role
      INTO v_user_id, v_role
      FROM users
     WHERE mobile = '9999999999';

    IF v_user_id IS NULL THEN
        INSERT INTO users (
            public_id,
            mobile,
            name,
            email,
            password_hash,
            role,
            active,
            created_at
        )
        VALUES (
            gen_random_uuid()::text,
            '9999999999',
            'System Admin',
            'admin@mpay.local',
            crypt('ChangeMeImmediately!', gen_salt('bf', 12)),
            'ADMIN',
            TRUE,
            CURRENT_TIMESTAMP
        )
        RETURNING id INTO v_user_id;
    ELSE
        IF UPPER(COALESCE(v_role, '')) <> 'ADMIN' THEN
            RAISE EXCEPTION
                'Cannot seed initial ADMIN: mobile 9999999999 already belongs to role %', v_role;
        END IF;
    END IF;

    INSERT INTO wallets (user_id, balance, reserved_balance, version)
    VALUES (v_user_id, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;
END $$;
