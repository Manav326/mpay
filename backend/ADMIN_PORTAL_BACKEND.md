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

## Rental partners

The Admin Portal uses the real car-rental onboarding/review workflow:

- `GET /api/v1/car-rental/admin/vendors`
- `GET /api/v1/car-rental/admin/vendors/{vendorId}/vehicles`
- `POST /api/v1/car-rental/admin/vendors/{vendorId}/approve`
- `POST /api/v1/car-rental/admin/vendors/{vendorId}/reject`
- `POST /api/v1/car-rental/admin/vehicles/{carId}/approve`
- `POST /api/v1/car-rental/admin/vehicles/{carId}/reject`

These endpoints use `MANAGE_VENDORS`. Generic legacy admin vendor CRUD is intentionally no longer exposed.

## Financial operations

Protected operational visibility:

- `GET /api/v1/admin/financial/recharges`
- `POST /api/v1/admin/financial/recharges/{transactionId}/refresh`
- `GET /api/v1/admin/financial/withdrawals`
- `GET /api/v1/admin/financial/wallet-history`

`VIEW_FINANCIAL_OPERATIONS` grants read access. `MANAGE_RECHARGE_OPERATIONS` additionally permits recharge-status refresh. There are deliberately no manual “success/fail/refund” buttons because wallet reservations and provider settlement must remain authoritative.

## User lifecycle

- `POST /api/v1/admin/users/{publicId}/status`

This requires `MANAGE_USER_STATUS` and cannot be used to deactivate the acting account or an ADMIN account.

## Rental operations

- `GET /api/v1/car-rental/admin/dashboard`
- `GET /api/v1/car-rental/admin/bookings`
- `POST /api/v1/car-rental/admin/bookings/{bookingId}/complete`

These use `MANAGE_RENTAL_OPERATIONS`. Completion invokes the existing rental payout state machine; the portal does not bypass it.

## Shared service catalog note

The historical `admin_vendors` table and `/api/v1/services/vendors` backend catalog are retained temporarily because they are separate shared infrastructure. They are not used by the Admin Portal rental workflow, Android rental flow, or the current customer web portal.
