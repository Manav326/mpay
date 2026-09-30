-- Customer Care access administration and restricted customer-context capability.
-- MANAGE_SUPPORT_ACCESS is intentionally ADMIN-only; it controls who may grant/revoke
-- Customer Care capabilities to portal staff.
INSERT INTO role_permissions(role, permission) VALUES
    ('ADMIN', 'MANAGE_SUPPORT_ACCESS'),
    ('ADMIN', 'SUPPORT_VIEW_CUSTOMER_CONTEXT')
ON CONFLICT (role, permission) DO NOTHING;
