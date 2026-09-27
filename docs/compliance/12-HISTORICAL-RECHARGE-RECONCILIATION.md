# 12 — Historical Recharge Reconciliation Runbook

## Purpose

This document records the safe reconciliation method for historical recharge transactions that may be `PENDING` with wallet funds still reserved.

The runbook is deliberately **read-only first**. No historical transaction, wallet balance, reservation, or ledger row should be changed until the evidence for that transaction is established.

## Verified historical failure mechanism

Before the recharge hardening work, the execution flow was:

1. Resolve the cached recharge plan.
2. Resolve the execution provider.
3. Reserve the customer's wallet amount.
4. Call the execution provider.
5. If the provider call throws, mark the recharge `PENDING`.

The PayU provider also performed biller/operator validation before its HTTP submission. When that validation failed, the exception propagated back to `RechargeService` and the old flow marked the already-reserved transaction `PENDING` with no provider reference.

This created a particularly unsafe state:

- wallet reservation exists;
- recharge is `PENDING`;
- `providerReference` can be null;
- there is no PayU reference that can safely be queried;
- the `providerOrderId` on a recharge plan may belong to the plan/catalogue provider (for example Way2API), so it must not be used as a PayU status reference.

The hardened flow now performs provider preparation and validation **before** wallet reservation. It also only performs provider status reconciliation when an actual provider reference exists.

Relevant code milestones:

- `6130e57b3bdbdd905d3a94184e877982cdc89177` — pre-reservation provider validation.
- `97a895cee8ea17dfc4a271203aee5a8f02c687c9` — PayU biller-directory resolution.
- `275e0d8c3875445d5f05246bb89caf644547de04` — final pre-submission authentication/biller hardening verified by CI.

## Evidence hierarchy

For each historical `PENDING` transaction, inspect the following in order:

### 1. Recharge transaction

~~~sql
SELECT
    transaction_id,
    client_request_id,
    user_id,
    mobile_number,
    operator,
    circle,
    plan_id,
    status,
    provider_name,
    provider_reference,
    provider_order_id,
    wallet_ledger_ref,
    wallet_debit_amount,
    message,
    created_at,
    updated_at,
    completed_at
FROM recharge_transactions
WHERE transaction_id = '<TRANSACTION_ID>';
~~~

### 2. Related offer-cache row

Use the recharge `plan_id` as `offer_id`:

~~~sql
SELECT
    offer_id,
    mobile_number,
    operator,
    circle,
    provider_reference,
    provider_order_id,
    provider_log_description,
    provider_metadata,
    fetched_at,
    expires_at
FROM recharge_offer_cache
WHERE offer_id = '<PLAN_ID>';
~~~

### 3. Wallet ledger

~~~sql
SELECT
    id,
    external_ref,
    user_id,
    type,
    amount,
    status,
    reference_type,
    reference_id,
    description,
    created_at
FROM wallet_transactions
WHERE user_id = <USER_ID>
  AND (
      external_ref = '<TRANSACTION_ID>'
      OR reference_id = '<TRANSACTION_ID>'
  )
ORDER BY created_at DESC;
~~~

A recharge that was only reserved should **not** have a posted recharge debit ledger entry. The reservation is maintained on the wallet row separately.

### 4. Wallet snapshot

~~~sql
SELECT
    user_id,
    balance,
    reserved_balance,
    version
FROM wallets
WHERE user_id = <USER_ID>;
~~~

## Safe classification rules

### Class A — Confirmed pre-submission failure

A historical transaction may be classified as a pre-submission failure only when the stored evidence establishes all applicable conditions:

- recharge status is `PENDING`;
- `provider_name = 'payu'`;
- `provider_reference` is null/blank;
- no posted wallet debit exists for the recharge transaction;
- the related offer/cache evidence records a provider prerequisite failure or missing biller metadata;
- there is no independent evidence of a PayU submission/reference.

For this class, the transaction should be treated as a local failure that occurred before external submission.

### Class B — External submission cannot be ruled out

Examples:

- a PayU reference exists;
- external-provider response metadata exists;
- there is evidence that an external call was attempted but its outcome is unknown;
- the local transaction message does not establish a pre-submission failure.

These transactions must remain subject to provider reconciliation. Do **not** release the wallet reservation solely because the transaction is old.

### Class C — Already finalized

Any transaction with `SUCCESS` or `FAILED` must not be reprocessed by this historical procedure.

## What must not be used as proof

Do not infer external submission from:

- transaction age;
- `provider_order_id` alone;
- a generic `PENDING` status;
- the existence of a wallet reservation;
- a missing cache row after the fact.

In particular, a Way2API catalogue/order identifier is not a PayU transaction reference.

## Controlled resolution

Only after a transaction is independently classified as **Class A** should a state-changing reconciliation action be considered.

That action must be atomic with respect to the wallet reservation and transaction state:

1. lock the wallet row;
2. verify the expected reservation is still present;
3. release exactly the recorded `wallet_debit_amount`;
4. mark the recharge `FAILED`;
5. preserve the original transaction/provider evidence in the message/audit trail;
6. do not create a debit ledger entry.

No bulk release should be implemented until the per-transaction evidence has been reviewed.

## Production reconciliation record — 2026-09-27

The evidence review identified seven historical `PENDING` PayU recharge transactions as confirmed pre-submission failures. The live PostgreSQL database used by the `main` deployment was then reconciled directly, without changing the Git `main` branch.

The seven reconciled transaction IDs were:

- `RTX-1790000559805-825c86b5` — wallet debit reservation ₹25.74
- `RTX-1790000971996-097236b1` — ₹395.01
- `RTX-1790003344483-8ecb0af1` — ₹1,088.01
- `RTX-1790063934370-c5704b1b` — ₹3,959.01
- `RTX-1790131035110-a8f2b493` — ₹1,088.01
- `RTX-1790140439073-4f00cbfe` — ₹3,959.01
- `RTX-1790184651662-6ad33616` — ₹3,959.01

Total reservation released: **₹14,473.80**.

Production verification after the reconciliation showed:

- all seven transactions have status `FAILED`;
- all seven have no `provider_reference`;
- all seven have no `wallet_ledger_ref`;
- total wallet `reserved_balance` is **₹0.00**;
- zero wallets have a positive reservation.

No recharge debit ledger entry was created for these seven transactions because the evidence established that the failures occurred before external submission.

The reconciliation was performed directly against the production PostgreSQL database. It did **not** deploy or execute the new reconciliation endpoint from `compliance/regulatory-readiness`, and it did **not** modify the Git `main` branch.

The failed command tail that attempted to query a temporary table after `COMMIT` produced a PostgreSQL relation-not-found error; this did not roll back the transaction. The subsequent independent production queries confirmed the seven `FAILED` states and zero remaining reservations.

## Current implementation state

The new forward path prevents this failure class from being created again:

`provider preparation -> provider validation -> wallet reservation -> external submission`

The historical rows are intentionally left untouched until the evidence-based reconciliation procedure is run.
