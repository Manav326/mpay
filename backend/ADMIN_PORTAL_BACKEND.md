# mPay Admin Portal Backend Integration

Admin portal endpoints added on top of the existing client APIs.

## Authentication

`POST /api/v1/auth/admin-login`

Accepts the normal mobile/password payload but only returns a token for `ADMIN` or `MANAGER` users. `CLIENT` accounts receive `403`.

Existing forgot/reset password endpoints remain:

- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

## Dashboard

`GET /api/v1/admin/dashboard`

- ADMIN: company-wide successful recharge volume and company commission.
- MANAGER: aggregate metrics scoped to visible CLIENT accounts.

## Users

`GET /api/v1/admin/users?role=ALL&sort=today-high`

Sort values:

- `today-high`
- `today-low`
- `month-high`
- `month-low`

Visibility is enforced server-side:

- ADMIN -> ADMIN, MANAGER, CLIENT
- MANAGER -> CLIENT
- CLIENT -> denied

`GET /api/v1/admin/users/{publicId}` returns read-only detail data. No admin endpoint in this layer can recharge, add money, or withdraw from a user's wallet.

## Vendors

Admin-only:

- `GET /api/v1/admin/vendors`
- `POST /api/v1/admin/vendors`

Persisted in PostgreSQL through Flyway migration `V10__admin_portal.sql`.

Client-facing catalog for future Android service quick actions:

`GET /api/v1/services/vendors?category=CAR_RENT&city=Patna`

Only active vendors are returned.
