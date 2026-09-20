# mPay Admin Database Setup

The Admin portal uses the same `/api/v1/auth/login` endpoint and the same JWT access/refresh-token flow as the Android client. There is no application bootstrap account mechanism.

## Initial local admin

Flyway migration `V11__seed_initial_admin.sql` creates the first local admin only when the migration runs:

- Mobile: `9999999999`
- Password: `ChangeMeImmediately!`
- Name: `System Admin`
- Email: `admin@mpay.local`
- Role: `ADMIN`

Change the password immediately through the normal password-reset flow or by an authenticated account-management mechanism before production use.

## Create another ADMIN directly in PostgreSQL

Use this pattern. Replace the mobile, name, email and password values before executing.

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_user_id BIGINT;
BEGIN
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
        '9876543210',
        'Another Admin',
        'another.admin@mpay.local',
        crypt('ReplaceWithAStrongPassword!', gen_salt('bf', 12)),
        'ADMIN',
        TRUE,
        CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_user_id;

    INSERT INTO wallets (user_id, balance, reserved_balance, version)
    VALUES (v_user_id, 0, 0, 0);
END $$;
```

## Create a MANAGER directly

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_user_id BIGINT;
BEGIN
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
        '9876543211',
        'Manager User',
        'manager@mpay.local',
        crypt('ReplaceWithAStrongPassword!', gen_salt('bf', 12)),
        'MANAGER',
        TRUE,
        CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_user_id;

    INSERT INTO wallets (user_id, balance, reserved_balance, version)
    VALUES (v_user_id, 0, 0, 0);
END $$;
```

## Disable an account

```sql
UPDATE users
SET active = FALSE
WHERE mobile = '9876543210';
```

## Change an admin password directly

```sql
UPDATE users
SET password_hash = crypt('NewStrongPassword!', gen_salt('bf', 12))
WHERE mobile = '9999999999'
  AND role = 'ADMIN';
```

## Important

Do not put plaintext production passwords into version control. Run the SQL directly against the secured database and then clear the SQL history/console output where appropriate.
