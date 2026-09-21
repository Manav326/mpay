# Recharge execution phase

Current production-shaped flow:

1. Way2API operator/circle detection.
2. Way2API R-Offer plan discovery for Airtel/VI.
3. Client selects an offer.
4. Backend re-fetches the live R-Offer catalogue and validates the selected plan.
5. Wallet funds are reserved (balance is not reduced yet).
6. Selected execution provider runs.
7. SUCCESS -> reserved funds become a real wallet DEBIT.
8. FAILED -> reservation is released.
9. PENDING/provider timeout -> reservation remains and transaction can be reconciled later.
10. GET /api/v1/recharge/{transactionId} exposes status.

Current execution provider:
- MOCK
- configurable with app.recharge.execution-provider
- app.recharge.mock-execution-status: SUCCESS | PENDING | FAILED

Current plan provider:
- WAY2API R-OFFER
- configurable with app.recharge.plan-provider

Client APIs:
POST /api/v1/recharge/operator
GET  /api/v1/recharge/plans?mobile=&operator=&circle=
POST /api/v1/recharge
GET  /api/v1/recharge/{transactionId}

Recharge POST body:
{
  "mobileNumber": "7070107483",
  "operator": "AIRTEL",
  "circle": "Bihar and Jharkhand",
  "planId": "WAY2-ROFFER-...",
  "clientRequestId": "unique-client-reference"
}

The backend never trusts a client-supplied amount; it re-fetches the current provider offers and resolves planId server-side.

## Operator detection reliability

The operator/circle lookup is a real Way2API call:

POST /api/v1/recharge/operator -> POST https://app.way2api.com/api/v1/operator-circle/check.

The backend now uses explicit upstream connect/read timeouts and exposes provider failures as gateway/provider errors instead of allowing an unbounded upstream wait.

Current local defaults:
- Way2API connect timeout: 5 seconds
- Way2API read timeout: 45 seconds
- Android client read timeout: 60 seconds

The provider response lifecycle is also handled explicitly:
- SUCCESS with operator/circle -> continue to plan lookup.
- PENDING / ACCEPTED / PROVIDER_NO_RESPONSE -> Android shows a pending message and does not call the plans API with incomplete operator data.
- Upstream timeout -> HTTP 504 from our backend.
- Provider errors such as 429, 503, 401, 402, 403, and 422 are surfaced without automatic retry.

Automatic retry is intentionally not used for operator detection because Way2API documents that some accepted/pending requests are billable; retrying blindly after a timeout could duplicate a billable lookup.
