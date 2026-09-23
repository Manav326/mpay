-- Admin operational permissions for safe user lifecycle and financial oversight.
INSERT INTO role_permissions(role, permission) VALUES
    ('ADMIN', 'MANAGE_USER_STATUS'),
    ('ADMIN', 'MANAGE_RECHARGE_OPERATIONS'),
    ('ADMIN', 'MANAGE_RENTAL_OPERATIONS'),
    ('ADMIN', 'VIEW_FINANCIAL_OPERATIONS'),
    ('MANAGER', 'VIEW_FINANCIAL_OPERATIONS')
ON CONFLICT (role, permission) DO NOTHING;
