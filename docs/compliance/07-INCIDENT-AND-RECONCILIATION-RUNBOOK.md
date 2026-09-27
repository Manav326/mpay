# mPay Incident & Reconciliation Runbook

Date: 2026-09-27
Status: Target operational control document

## 1. Purpose

This runbook defines how mPay must handle financial events that do not reach a clean final state. It is intended for payment, wallet, recharge, rental and withdrawal flows.

The objective is not merely to retry failures. The objective is to preserve customer value, prevent duplicate financial effects, reconstruct the event history and reconcile mPay's records with the authoritative external provider.

## 2. Core rule

A database transaction cannot roll back an external provider action.

Therefore every external financial operation must have:

- a unique internal transaction reference;
- provider reference where available;
- request timestamp;
- current state;
- last provider-observed state;
- idempotency key/client request ID where supported;
- amount and currency;
- linked customer/service record;
- reconciliation status;
- audit history.

## 3. State categories

Every financial transaction must end in one of:

- SUCCESS — provider outcome and internal accounting are consistent.
- FAILED — provider confirms failure and any reservation/temporary hold is released or compensated.
- REFUNDED/REVERSED — the original effect was compensated through a linked transaction.
- PENDING — an outcome is legitimately still awaiting provider confirmation.
- UNKNOWN — provider outcome cannot currently be established; it must remain unresolved until reconciliation.

UNKNOWN must never be silently converted to SUCCESS or FAILED.

## 4. Payment/add-money incident matrix

### A. Customer payment failed

Expected action:
1. Keep wallet/service value unchanged.
2. Record provider failure.
3. Show failure/pending according to provider state.
4. Do not create customer-value credit.

### B. Provider reports success but mPay did not credit value

Expected action:
1. Verify server-side provider status.
2. Match merchant/order/reference, amount and customer.
3. Create the financial credit exactly once.
4. Mark settlement complete.
5. Preserve provider evidence.

### C. mPay credited value but provider later indicates failure/refund

Expected action:
1. Do not overwrite the original ledger entry.
2. Create a linked compensating debit/reversal only after authoritative evidence.
3. Prevent the customer from receiving duplicate value.
4. Record reason and provider reference.
5. Reconcile the resulting balance.

## 5. Recharge incident matrix

### Wallet debit succeeds, recharge provider times out

State: UNKNOWN.

Do not immediately issue a second recharge.

First:
1. Query provider status using the original reference.
2. If provider confirms success, complete the recharge transaction.
3. If provider confirms failure, reverse/release the customer value according to the approved policy.
4. If still unknown, keep the transaction unresolved and continue scheduled reconciliation.

### Recharge succeeds but mPay records failure

Reconcile the provider result before refunding or retrying. External success must not be duplicated.

### Recharge fails after customer value was debited

Create a compensating wallet transaction/refund according to the approved customer policy and provider contract.

## 6. Rental incident matrix

### Wallet payment succeeds but booking creation fails

The payment and booking states must remain separate.

The system must either:
- complete the booking using the original payment reference, or
- compensate/refund the customer through the approved route.

Never create a second customer debit merely because the first booking attempt failed.

### Booking cancelled after payment

Apply the published cancellation/refund rules and create linked refund/adjustment records.

### Vendor service cannot be fulfilled

Record vendor/service failure separately from payment success and execute the approved refund/alternative-service process.

## 7. Withdrawal incident matrix

### Withdrawal requested

Reserve customer value before initiating the payout where the approved wallet architecture permits reservation.

### Provider timeout

Keep the transaction PENDING/UNKNOWN. Do not release the reservation merely because the HTTP request timed out.

First reconcile provider status.

### Provider success

Finalise the reservation into a debit exactly once and record provider payout reference.

### Provider failure

Release/reverse the reservation only after authoritative failure or approved timeout-resolution rules.

## 8. Reconciliation jobs

The production design should include scheduled reconciliation for:

- payment orders;
- payment webhooks/callbacks;
- wallet settlement;
- recharge provider transactions;
- rental payment/booking records;
- withdrawal/payout records;
- provider settlement reports;
- bank/regulated-partner settlement reports.

Each reconciliation run must record:
- run ID;
- provider;
- period;
- records inspected;
- matched records;
- mismatches;
- unresolved records;
- corrective actions;
- operator/system identity;
- completion timestamp.

## 9. Mismatch classes

At minimum:

1. Provider success / mPay missing.
2. mPay success / provider missing.
3. Amount mismatch.
4. Currency mismatch.
5. Duplicate provider reference.
6. Duplicate internal reference.
7. Customer mismatch.
8. Service mismatch.
9. Settlement mismatch.
10. Refund/reversal mismatch.
11. Vendor payable mismatch.
12. Platform-fee mismatch.

## 10. Operational controls

Financial corrections must be:
- attributable to an authenticated operator or automated reconciliation job;
- reason-coded;
- linked to the original transaction;
- non-destructive;
- auditable.

No administrator should directly edit a historical financial amount or balance to "fix" a mismatch.

## 11. Incident severity

### Critical
Customer funds/value may be duplicated, lost, misdirected, or unavailable at scale.

### High
A confirmed financial mismatch affects one or more customers/vendors and cannot be automatically reconciled.

### Medium
A provider/service failure is isolated and customer value remains protected.

### Low
Reporting, display or non-financial reconciliation discrepancy with no customer-value impact.

Critical and High incidents require documented owner, evidence, remediation and closure review.

## 12. Evidence retention

For each incident retain, subject to the applicable retention/privacy policy:

- internal transaction IDs;
- provider references;
- request/response metadata;
- webhook evidence;
- ledger entries;
- reconciliation output;
- operator actions;
- customer communication;
- refund/reversal evidence;
- final disposition.

## 13. Production gate

Before production, test at minimum:

- duplicate callback;
- callback before redirect;
- redirect without callback;
- provider timeout;
- provider success after timeout;
- mPay crash after provider success;
- mPay crash after wallet debit;
- recharge success after local failure;
- recharge failure after local success;
- withdrawal timeout;
- withdrawal success after timeout;
- duplicate withdrawal request;
- refund after service failure.

