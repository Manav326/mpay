# mPay Admin Portal Roles, Login and Permissions

## Initial portal accounts

**ADMIN**
- Mobile: `9999999999`
- Password: `Admin@123`
- Name: `System Admin`
- Email: `admin@mpay.local`

**MANAGER**
- Mobile: `9999999998`
- Password: `Manager@123`
- Name: `System Manager`
- Email: `manager@mpay.local`

These are local/development credentials. Change them before production use.

## How role is determined

The role is stored on `users.role`. Normal login authenticates the user with the same BCrypt/JWT flow already used by clients. The JWT filter loads the current user from the database and sets `ROLE_<role>` in Spring Security. Portal login additionally requires that the selected portal role matches the user's database role.

## Portal login endpoints

- `POST /api/v1/auth/admin-login`
- `POST /api/v1/auth/manager-login`
- `POST /api/v1/auth/portal-login` with `{ mobile, password, portalRole }`
- `GET /api/v1/auth/portal-roles` returns roles that have the `PORTAL_LOGIN` permission.

The Admin Web displays a role dropdown and calls the matching endpoint. Future portal roles can be added by inserting `PORTAL_LOGIN` plus their other permissions; the dropdown can then discover the new role automatically.

## Permissions

Current seeded permissions:

ADMIN:
- `PORTAL_LOGIN`
- `VIEW_DASHBOARD`
- `VIEW_USERS`
- `VIEW_USER_DETAIL`
- `MANAGE_VENDORS` — approve/reject rental partners and vehicles
- `MANAGE_RENTAL_OPERATIONS` — rental bookings and settlement lifecycle
- `MANAGE_RECHARGE_OPERATIONS` — refresh pending/processing recharge status
- `MANAGE_USER_STATUS` — block/unblock non-admin accounts
- `VIEW_FINANCIAL_OPERATIONS` — recharge, withdrawal and wallet-ledger oversight
- `MANAGE_COMMISSION_RATES` — role commission rules

MANAGER:
- `PORTAL_LOGIN`
- `VIEW_DASHBOARD`
- `VIEW_USERS`
- `VIEW_USER_DETAIL`
- `VIEW_FINANCIAL_OPERATIONS` — read-only financial oversight

## Visibility hierarchy

`role_hierarchy` controls which target roles a viewer may inspect:

- ADMIN -> ADMIN
- ADMIN -> MANAGER
- ADMIN -> CLIENT
- MANAGER -> CLIENT

Add future roles by inserting rows into `role_permissions` and `role_hierarchy`. Server-side authorization uses these tables; the web UI is only a presentation layer. The Admin Portal intentionally avoids manual wallet mutation controls and delegates provider money state to the existing recharge/withdrawal/rental state machines.

## Example: add a new supervisor role

```sql
INSERT INTO role_permissions(role, permission) VALUES
  ('SUPERVISOR', 'PORTAL_LOGIN'),
  ('SUPERVISOR', 'VIEW_DASHBOARD'),
  ('SUPERVISOR', 'VIEW_USERS'),
  ('SUPERVISOR', 'VIEW_USER_DETAIL')
ON CONFLICT DO NOTHING;

INSERT INTO role_hierarchy(viewer_role, target_role) VALUES
  ('SUPERVISOR', 'CLIENT')
ON CONFLICT DO NOTHING;
```

Then create a `users` row with `role='SUPERVISOR'`. No Java/Kotlin code change is needed for the role to appear in `/api/v1/auth/portal-roles`.
