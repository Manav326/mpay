-- Add the customer-care employee role using the existing role/permission/hierarchy model.
-- Staff accounts remain rows in users; no separate employee table is required.
INSERT INTO role_permissions(role, permission) VALUES
    ('CUSTOMER_SUPPORT', 'PORTAL_LOGIN'),
    ('CUSTOMER_SUPPORT', 'SUPPORT_VIEW'),
    ('CUSTOMER_SUPPORT', 'SUPPORT_MANAGE'),
    ('CUSTOMER_SUPPORT', 'CALL_CUSTOMER')
ON CONFLICT (role, permission) DO NOTHING;

INSERT INTO role_hierarchy(viewer_role, target_role) VALUES
    ('ADMIN', 'CUSTOMER_SUPPORT'),
    ('CUSTOMER_SUPPORT', 'CLIENT')
ON CONFLICT (viewer_role, target_role) DO NOTHING;
