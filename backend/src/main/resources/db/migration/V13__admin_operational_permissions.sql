-- Admin operational permissions for safe user lifecycle and financial oversight.
INSERT INTO role_permissions(role, permission) VALUES
    ('ADMIN', 'MANAGE_USER_STATUS'),
    ('ADMIN', 'VIEW_FINANCIAL_OPERATIONS'),
    ('MANAGER', 'VIEW_FINANCIAL_OPERATIONS')
ON CONFLICT (role, permission) DO NOTHING;
