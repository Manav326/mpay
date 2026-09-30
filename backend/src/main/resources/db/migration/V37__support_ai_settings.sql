-- Admin-controlled Customer Care AI availability.
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(120) PRIMARY KEY,
    boolean_value BOOLEAN NOT NULL,
    updated_by_user_id BIGINT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO app_settings(setting_key, boolean_value, updated_by_user_id, updated_at)
VALUES ('SUPPORT_AI_ENABLED', FALSE, NULL, NOW())
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO role_permissions(role, permission)
SELECT 'ADMIN', 'MANAGE_SUPPORT_AI'
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permissions
    WHERE UPPER(role) = 'ADMIN'
      AND UPPER(permission) = 'MANAGE_SUPPORT_AI'
);
