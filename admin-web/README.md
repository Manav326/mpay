# mPay Admin Web 0.4.0

Next.js admin portal aligned to the current mPay backend and Android visual language.

## Portal login

The portal starts by loading `GET /api/v1/auth/portal-roles`. The role dropdown is therefore data-driven. Current seeded roles are `ADMIN` and `MANAGER`; future roles that receive the `PORTAL_LOGIN` permission automatically become eligible portal roles.

Selected role login endpoints:
- `POST /api/v1/auth/admin-login`
- `POST /api/v1/auth/manager-login`
- generic future-role endpoint: `POST /api/v1/auth/portal-login` with `{ mobile, password, portalRole }`

Client accounts do not use the admin portal. Normal `/api/v1/auth/login` is reserved for CLIENT accounts; ADMIN/MANAGER accounts use portal-specific endpoints.

## Role hierarchy and permissions

The backend is the source of truth for permissions and visibility. The web UI only reflects the permissions in the JWT login response. Current hierarchy:
- ADMIN -> ADMIN, MANAGER, CLIENT
- MANAGER -> CLIENT

## Current UI capabilities
- Company/manager dashboard with today/monthly metrics.
- User list filtered by all visible roles.
- Sort by today's or monthly highest/lowest earnings.
- Read-only user detail drawer.
- Vendor/service management only when `MANAGE_VENDORS` is granted.
- Existing forgot-password flow for portal accounts.

## Configuration

Set:
`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`
`NEXT_PUBLIC_ADMIN_DEMO_MODE=false`

All access control is enforced server-side. Never rely on the browser to enforce permissions.
