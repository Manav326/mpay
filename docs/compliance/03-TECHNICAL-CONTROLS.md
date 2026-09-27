# mPay Technical Compliance Controls

## 1. Control objective

Every rupee and every service transaction must be traceable from initiation to final state without relying on client-side claims or non-idempotent callbacks.

## 2. Required canonical transaction states

### Payment collection

INITIATED -> CREATED -> PENDING -> AUTHORIZED/CAPTURED -> SETTLED

Failure paths:
PENDING -> FAILED
CAPTURED -> REFUND_PENDING -> REFUNDED
CAPTURED -> DISPUTED/CHARGEBACK

### Wallet/stored value

If a regulated wallet structure is retained:
UNAVAILABLE -> AVAILABLE -> RESERVED -> DEBITED

Compensation:
RESERVED -> RELEASED
DEBITED -> CREDITED_BY_REVERSAL

Every adjustment must reference the original transaction.

### Recharge

REQUESTED -> PROVIDER_PENDING -> SUCCESS
or
REQUESTED -> PROVIDER_FAILED
or
PROVIDER_PENDING -> UNKNOWN -> RECONCILED_SUCCESS/RECONCILED_FAILED

### Rental

BOOKING_CREATED -> PAYMENT_PENDING -> PAID -> SERVICE_CONFIRMED

Cancellation/refund must be a separate state machine linked to the original booking/payment.

### Withdrawal

REQUESTED -> RESERVED -> PENDING -> PROCESSING -> SUCCESS

Failure:
PROCESSING -> FAILED/REVERSED -> RESERVATION_RELEASED

## 3. Idempotency

Mandatory idempotency keys:

- payment order creation;
- payment verification/settlement;
- provider webhook;
- recharge request;
- recharge reconciliation;
- rental payment;
- refund;
- withdrawal request;
- withdrawal webhook;
- wallet adjustment.

A repeated event must return the existing authoritative result rather than create another financial effect.

## 4. External transaction is never part of a DB transaction

A Spring transaction boundary can protect mPay database changes, but it cannot roll back:

- a telecom recharge;
- a Razorpay/PayU capture;
- a payout;
- a vendor settlement.

Therefore every external operation requires an explicit compensation/reconciliation mechanism.

## 5. Critical current design issue

The current recharge payment settlement flow can:

1. credit the internal wallet;
2. invoke the recharge service;
3. rely on a database transaction for internal atomicity.

That is not sufficient as a financial correctness model because the external recharge provider may succeed even if the database transaction later rolls back.

Required target: payment capture and service fulfilment must be represented as separately reconciled states with durable external references.

## 6. Provider webhook security

Every webhook must:

- verify provider signature/authenticity;
- validate merchant/order/reference;
- validate amount/currency where provided;
- be idempotent;
- persist raw/normalised event metadata needed for audit;
- tolerate repeated delivery;
- never trust status without reference matching.

## 7. Provider reconciliation

A scheduled reconciliation process must identify:

- provider success not credited internally;
- internal success not present at provider;
- internal pending beyond SLA;
- duplicate provider events;
- duplicate internal ledger effects;
- refund requested but not completed;
- payout processing without terminal provider state;
- recharge provider success/failure mismatch.

## 8. Wallet ledger integrity

The ledger must be append-oriented.

Do not edit historical posted financial entries in place.

Use compensating entries:
ORIGINAL DEBIT -> REVERSAL CREDIT

and preserve:

- original transaction ID;
- adjustment ID;
- reason;
- actor/system source;
- timestamp.

## 9. Balance invariants

For each user:

availableBalance = max(balance - reservedBalance, 0)

Additional invariants:

- reserved balance cannot exceed total balance;
- successful withdrawal must reduce total balance exactly once;
- failed withdrawal must release reservation exactly once;
- duplicate webhook must not change balance;
- successful recharge debit must reduce wallet value exactly once;
- refund/reversal must create an explicit compensating entry.

## 10. Customer-money segregation

The technical database must distinguish:

- customer transaction value;
- platform revenue/fees;
- vendor payable;
- provider fees;
- refunds;
- chargebacks;
- operational adjustments.

Do not net these into one generic wallet balance.

## 11. Rental settlement

For each booking store:

- gross customer charge;
- applicable taxes/fees;
- platform fee;
- vendor payable;
- payment provider reference;
- wallet/payment reference;
- booking reference;
- cancellation amount;
- refund amount;
- settlement reference;
- settlement status.

MPAY_RENTAL_PLATFORM_FEE_PERCENT must be treated as a commercial calculation, not as an accounting shortcut.

## 12. Recharge settlement

For each recharge store:

- customer amount;
- wallet debit amount;
- commission/markup if any;
- operator;
- mobile number;
- provider;
- provider transaction/reference;
- request timestamp;
- final provider status;
- refund/reversal reference.

## 13. Withdrawal controls

Current useful controls already observed:

- UPI ID required;
- UPI ID format validation;
- minimum amount;
- client request ID;
- wallet reservation;
- provider-specific status;
- processing state for uncertain provider outcome;
- terminal success/failure handling;
- withdrawal history with UPI ID and provider reference.

Required additions before production:

- verified ownership/beneficiary policy;
- velocity/risk limits;
- strong authentication before payout;
- provider webhook verification;
- reconciliation;
- suspicious-activity monitoring;
- audit trail for manual/admin intervention.

## 14. Admin controls

Financial admin actions must be:

- authenticated;
- role-restricted;
- logged;
- attributable to a named admin/service identity;
- reason-coded for manual adjustments;
- non-destructive to historical financial records.

## 15. Data retention and privacy

Transaction evidence must be retained for the applicable legal/accounting/payment-provider period, while personal data should be minimised and protected.

Sensitive values should not appear in ordinary application logs.

## 16. Production gate

No real-money launch until:

- legal classification signed off;
- payment-provider contracts verified;
- merchant/business KYC complete;
- regulated partner roles confirmed;
- refund/cancellation policies published;
- grievance process published;
- webhook/reconciliation tests passed;
- duplicate-event tests passed;
- crash-recovery tests passed;
- provider reconciliation tested;
- bank/provider settlement reconciles to mPay ledger;
- admin audit logs verified;
- incident runbook tested.
