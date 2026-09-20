ALTER TABLE users
    ADD COLUMN public_id VARCHAR(36),
    ADD COLUMN name VARCHAR(120),
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN profile_image_key VARCHAR(255),
    ADD COLUMN profile_image_content_type VARCHAR(100),
    ADD COLUMN profile_image_updated_at TIMESTAMPTZ;

UPDATE users
SET public_id =
    substr(md5(random()::text || clock_timestamp()::text || id::text), 1, 8) || '-' ||
    substr(md5(random()::text || clock_timestamp()::text || id::text), 9, 4) || '-' ||
    substr(md5(random()::text || clock_timestamp()::text || id::text), 13, 4) || '-' ||
    substr(md5(random()::text || clock_timestamp()::text || id::text), 17, 4) || '-' ||
    substr(md5(random()::text || clock_timestamp()::text || id::text), 21, 12)
WHERE public_id IS NULL;

ALTER TABLE users
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX uq_users_public_id ON users(public_id);
CREATE UNIQUE INDEX uq_users_email_ci ON users(lower(email)) WHERE email IS NOT NULL;
