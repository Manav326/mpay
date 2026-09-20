ALTER TABLE users
    ADD COLUMN profile_updated_at TIMESTAMPTZ;

UPDATE users
SET profile_updated_at = COALESCE(profile_image_updated_at, created_at)
WHERE profile_updated_at IS NULL;
