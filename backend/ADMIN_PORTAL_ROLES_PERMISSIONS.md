# mPay Admin Portal Roles, Login and Permissions

## Development portal accounts

The repository contains local/development seed accounts. Change their credentials before production deployment.

## Portal login

- ADMIN and MANAGER can authenticate through the dedicated portal login endpoints.
- GET /api/v1/auth/portal-roles exposes portal roles which have PORTAL_LOGIN.

## Current permissions

### ADMIN

- PORTAL_LOGIN
- VIEW_DASHBOARD
- VIEW_USERS
- VIEW_USER_DETAIL
- MANAGE_USER_STATUS
- VIEW_FINANCIAL_OPERATIONS
- MANAGE_RECHARGE_OPERATIONS
- MANAGE_VENDORS
- MANAGE_RENTAL_OPERATIONS
- MANAGE_COMMISSION_RATES

### MANAGER

- PORTAL_LOGIN
- VIEW_DASHBOARD
- VIEW_USERS
- VIEW_USER_DETAIL
- VIEW_FINANCIAL_OPERATIONS

The financial permission is intentionally visibility-only for MANAGER unless additional mutation permissions are explicitly granted in a future migration.

## Visibility hierarchy

role_hierarchy controls which target roles a viewer may inspect:

- ADMIN -> ADMIN
- ADMIN -> MANAGER
- ADMIN -> CLIENT
- MANAGER -> CLIENT

Financial operation queries use this same hierarchy when selecting recharge, withdrawal and wallet-ledger records. UI hiding is not used as an authorization mechanism.

## Rental permission split

Rental administration is intentionally divided:

- MANAGE_VENDORS -> vendor/vehicle submission review, approval/rejection and availability inspection.
- MANAGE_RENTAL_OPERATIONS -> rental dashboard, booking operations and eligible booking completion/settlement.

This prevents vendor onboarding permissions from silently becoming booking-operations permissions.

## Future roles

A new role can be introduced with database rows in role_permissions and role_hierarchy. The web portal discovers available login roles through the API; backend authorization remains data-driven.