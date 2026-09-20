# Recharge Android Client — Recharge Flow v0.4.0

This project is the current Android client baseline with the working authentication, JWT refresh, wallet, Razorpay wallet funding, and mobile recharge flow.

## Recharge flow

1. Enter the recharge target mobile number.
2. Detect operator and circle through the backend/Way2API integration.
3. Load current personalized R-Offers through the backend.
4. Select an offer.
5. Refresh and validate the authenticated user's wallet balance.
6. Confirm the recharge.
7. Submit `/api/v1/recharge` with a unique client request ID.
8. Show SUCCESS / PENDING / FAILED states.
9. Poll `/api/v1/recharge/{transactionId}` when the backend reports PENDING.
10. Refresh the wallet after the provider result is finalized.

The Android app does not contain any Way2API or recharge-provider credentials. Provider selection remains a backend concern so the execution provider can be replaced without changing the Android API contract.

## Backend endpoints used by this version

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/me`
- `GET /api/v1/wallet`
- `POST /api/v1/payments/orders`
- `POST /api/v1/payments/verify`
- `POST /api/v1/recharge/operator`
- `GET /api/v1/recharge/plans`
- `POST /api/v1/recharge`
- `GET /api/v1/recharge/{transactionId}`

## Notes

- Recharge state is cleared when leaving the Recharge screen.
- Bottom navigation uses top-level destination state restoration.
- Wallet funding remains Razorpay-based and is independent from recharge execution.
- Wallet debit is authoritative on the backend; the Android app never deducts the wallet locally.
