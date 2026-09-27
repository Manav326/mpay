# mPay Target Stored-Value / Wallet Architecture — Direct Authorisation Route

Date: 2026-09-27
Status: Target architecture for legal/regulatory review; not a licence determination.

## 1. Decision recorded

mPay's intended production model is for the eventual mPay company itself to operate the customer-facing stored-value/wallet arrangement and maintain customer balances, with unused value available for on-demand withdrawal.

This is a regulatory design choice, not a conclusion that mPay is already authorised to issue a PPI or operate a payment system.

The production architecture must therefore be designed around the authorisation/licensing route legally available to the eventual mPay company. No production launch of the persistent withdrawable wallet should occur until the required authorisation/regulated structure and provider/bank arrangements are confirmed in writing.

RBI's current PPI framework states that banks and non-bank entities issue PPIs after the necessary RBI approval/authorisation, and the framework applies to PPI issuers/system participants. The exact licence/category and conditions applicable to the final mPay product must be confirmed by qualified Indian regulatory counsel. 

## 2. What this means for the product

The intended consumer experience remains:

Customer
  -> Add Money
  -> mPay wallet balance
  -> Recharge / chauffeur-driven rental
  -> Withdraw unused value on demand

The engineering model must not describe this merely as an ordinary company receivable or an unrestricted company balance.

The system must maintain a legally and accounting-wise distinct representation of customer value, together with the obligations and restrictions imposed by the eventual authorisation.

## 3. Authorisation gate before production

Before enabling the production wallet, the following must be confirmed:

1. Legal entity capable of applying for/holding the required authorisation.
2. Exact regulatory classification of the wallet/stored-value product.
3. Required RBI authorisation/approval, if applicable.
4. Permitted activities and transaction types.
5. Customer-fund safeguarding/settlement structure.
6. Permitted bank/current-account/escrow arrangement.
7. KYC/AML/CFT requirements.
8. Customer balance, transaction and withdrawal limits.
9. Refund, failed transaction and dispute obligations.
10. Grievance-redressal and customer-support obligations.
11. Audit, reporting, information-security and system-audit requirements.
12. Treatment of unclaimed/expired balances, if applicable.
13. Conditions for suspension/closure of a customer account.
14. Permitted use of PayU/Razorpay or any other payment provider for loading customer value.
15. Permitted payout mechanism for customer withdrawals.

These are release gates, not optional documentation.

## 4. Customer-value accounting model

The application must distinguish at minimum:

- customer available value;
- customer reserved value;
- customer value pending confirmation;
- customer value subject to refund/reversal;
- customer value withdrawn;
- customer-value adjustments;
- mPay service/platform revenue;
- vendor payable;
- recharge-provider payable;
- payment-provider fees;
- refunds/chargebacks;
- operational adjustments.

A customer's balance must never be increased merely because an application request says payment succeeded.

A provider-confirmed payment must have a unique internal reference and provider reference. Duplicate callbacks must be idempotent.

## 5. Add Money state model

Proposed state machine:

CREATED
-> PAYMENT_PENDING
-> PAYMENT_CONFIRMED
-> VALUE_POSTING
-> VALUE_POSTED
-> RECONCILED

Exception states:

PAYMENT_FAILED
PAYMENT_UNKNOWN
VALUE_POSTING_FAILED
REFUNDED
REVERSED

Important rule:

Provider success and internal value posting are separate state transitions. A database rollback cannot undo an external provider-side payment. Therefore unresolved provider outcomes require reconciliation rather than blind retry or silent correction.

## 6. Spend state model

For recharge and rental, customer value should move through an explicit reservation/debit lifecycle:

AVAILABLE
-> RESERVED
-> DEBITED

Failure:

RESERVED
-> RELEASED

Unknown external outcome:

RESERVED
-> PENDING_RECONCILIATION

The system must not debit customer value permanently until the relevant business transaction reaches the appropriate authoritative state.

## 7. Withdrawal state model

Proposed:

WITHDRAWAL_REQUESTED
-> ELIGIBILITY_CHECKED
-> VALUE_RESERVED
-> PAYOUT_PENDING
-> PAYOUT_CONFIRMED
-> VALUE_DEBITED
-> RECONCILED

Failure:

VALUE_RESERVED
-> PAYOUT_FAILED
-> VALUE_RELEASED

Unknown:

VALUE_RESERVED
-> PAYOUT_UNKNOWN
-> RECONCILIATION
-> CONFIRMED or FAILED

The payout path must be compatible with the eventual authorised wallet/payment structure. The current application's ability to call a payout provider does not itself establish regulatory permission for cash-out.

## 8. Rental settlement separation

For a ₹1,000 illustrative rental:

- customer charge/value movement: ₹1,000;
- vendor payable: ₹900;
- mPay contractual platform/service fee: ₹100;

subject to final contractual, GST and accounting treatment.

The application must not encode the ₹100 platform fee simply as the difference between two balances without an auditable transaction relationship.

Vendor settlement must have its own state and external settlement reference when actual bank/UPI payout is introduced.

## 9. Recharge separation

Way2API is currently used for catalogue/plan information.

PayU is currently intended for actual recharge execution/payment.

The system must separately track:

- customer-value movement;
- recharge order;
- external recharge request;
- external recharge status;
- provider reference;
- reversal/refund status.

A recharge-provider success must not be treated as equivalent to successful wallet funding.

## 10. Data and audit requirements

Every financial transaction should retain, as applicable:

- internal transaction ID;
- customer ID;
- service/order ID;
- transaction type;
- amount and currency;
- creation/confirmation timestamps;
- provider name;
- provider transaction/reference ID;
- idempotency key;
- previous state;
- current state;
- reconciliation state;
- related compensating/refund transaction;
- actor/system responsible for administrative action.

Historical financial records must be append-oriented. Corrections should use compensating entries rather than editing the original financial event.

## 11. Architecture boundary

The wallet domain must be isolated behind a stable internal abstraction.

Consumer services should request operations such as:

- create value-load transaction;
- confirm value load;
- get available balance;
- reserve value;
- release reservation;
- debit value;
- initiate withdrawal;
- reconcile transaction.

Recharge and rental services should not directly manipulate wallet ledger rows.

This allows the implementation to satisfy the eventual authorisation requirements without coupling every service to provider-specific or regulatory mechanics.

## 12. Production launch gates

The wallet cannot be considered production-ready merely because:

- PayU/Razorpay payment succeeds;
- the current account receives money;
- the database balance increases;
- withdrawal API returns success.

Production readiness requires evidence for the legal/regulatory structure, provider contracts, safeguarding/settlement arrangement, KYC/AML controls, reconciliation, customer disclosures, grievance handling, security controls and operational governance.

## 13. Explicit non-assumptions

This document does not assert that:

- mPay currently holds an RBI authorisation;
- mPay can currently issue PPIs;
- the existing company current account is an authorised safeguarding mechanism;
- PayU/Razorpay settlement into that account makes customer funds unrestricted company revenue;
- the current withdrawal implementation is legally sufficient for production;
- mPay is currently a BBPS participant.

Those matters require documentary verification before production.

## 14. Decision required from legal/provider workstream

The remaining business/legal decision is not whether mPay wants the wallet. That is now decided.

The remaining question is which exact authorisation/licensing structure permits the eventual mPay company to operate the intended withdrawable stored-value product, and what safeguarding/settlement arrangement that structure requires.

Engineering will implement against that confirmed structure rather than inventing a regulatory interpretation.
