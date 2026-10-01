-- Manual mobile verification is an explicit privileged admin action.
-- It is intentionally separate from account blocking because verification state
-- affects verification-gated customer capabilities and represents an identity decision.
INSERT INTO role_permissions(role, permission)
VALUES ('ADMIN', 'MANAGE_USER_MOBILE_VERIFICATION')
ON CONFLICT (role, permission) DO NOTHING;
