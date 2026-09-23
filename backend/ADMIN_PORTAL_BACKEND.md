# mPay Admin Portal Backend Integration

The Admin Portal is an operations surface over the existing wallet, recharge and chauffeur-driven car-rental state machines. Administrative actions are permission-gated on the backend; the web UI is not the security boundary.

## Authentication and profile

POST /api/v1/auth/admin-login
POST /api/v1/auth/manager-login
POST /api/v1/auth/portal-login
GET /api/v1/auth/portal-roles

The authenticated admin user can manage their own profile through the existing profile APIs:

- GET /api/v1/profile
- PATCH /api/v1/profile
- GET /api/v1/profile/image
- PUT /api/v1/profile/image
- DELETE /api/v1/profile/image

## Dashboard and users

GET /api/v1/admin/dashboard

Dashboard metrics are scoped to the roles visible to the authenticated portal user.

GET /api/v1/admin/users?role=ALL&sort=today-high
GET /api/v1/admin/users/{publicId}
POST /api/v1/admin/users/{publicId}/status

User visibility is enforced server-side through role_hierarchy. Account status changes require MANAGE_USER_STATUS; administrators cannot deactivate their own account or another ADMIN account.

## Financial operations

The portal has a dedicated financial operations workspace:

- GET /api/v1/admin/financial/recharges
- POST /api/v1/admin/financial/recharges/{transactionId}/refresh
- GET /api/v1/admin/financial/withdrawals
- GET /api/v1/admin/financial/wallet-history

These views are filtered by the authenticated user's role hierarchy. Financial records for users outside that hierarchy are not returned.

Recharge refresh re-reads the current persisted provider workflow state. It does not allow an administrator to manufacture SUCCESS or FAILED states.

Withdrawal operations remain owned by the withdrawal/provider workflow. The portal is intentionally read-only for payout state transitions.

## Rental partner review

The supported rental administration workflow uses the real rental domain:

- GET /api/v1/car-rental/admin/vendors
- GET /api/v1/car-rental/admin/vendors/{vendorId}/vehicles
- POST /api/v1/car-rental/admin/vendors/{vendorId}/approve
- POST /api/v1/car-rental/admin/vendors/{vendorId}/reject
- POST /api/v1/car-rental/admin/vehicles/{carId}/approve
- POST /api/v1/car-rental/admin/vehicles/{carId}/reject
- GET /api/v1/car-rental/admin/vehicle-unavailability

These endpoints operate on rental_vendors, rental_cars, review history and vehicle-availability records.

The old generic admin_vendors catalogue is no longer part of the live Admin Portal API. Its Flyway migration remains immutable for deployed-database upgrade safety.

## Rental operations

Operational rental endpoints use the dedicated MANAGE_RENTAL_OPERATIONS permission:

- GET /api/v1/car-rental/admin/dashboard
- GET /api/v1/car-rental/admin/bookings
- POST /api/v1/car-rental/admin/bookings/{bookingId}/complete

Completing an eligible booking invokes the existing rental settlement workflow and vendor payout logic. Arbitrary booking cancellation or manual payout success/failure controls are deliberately not exposed because the existing domain does not define a separate safe administrator override state machine.

## Commission rules

- GET /api/v1/admin/commission-roles
- PUT /api/v1/admin/commission-roles/{role}

These require MANAGE_COMMISSION_RATES.

## Design rule

Money state is always changed by its domain workflow:

- recharge -> reservation -> provider outcome -> finalize/release
- withdrawal -> reservation -> provider outcome/webhook -> settle/release
- rental payment -> wallet ledger -> booking lifecycle -> vendor payout settlement

The Admin Portal can inspect, filter, refresh supported provider state and perform explicitly modeled lifecycle transitions, but it must not bypass those domain workflows.