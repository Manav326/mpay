# mPay Target Financial Domain Model — Production-Authorisation-First

Date: 2026-09-27
Status: Target engineering design; production implementation is gated by legal/regulatory authorisation and approved operating model.

## 1. Design decision

mPay will be engineered from day one as an authorisation-first stored-value system.

The product requirement is:
- customer can load value;
- customer can retain value;
- value can be spent on approved mPay services;
- unused value can be withdrawn on demand;
- every customer-value movement is auditable and reconcilable.

The system must therefore model customer value as a regulated financial obligation/value position, not as ordinary application revenue.

RBI's current PPI Master Directions state that PPIs are stored-value instruments and that entities issuing PPIs require the necessary RBI approval/authorisation. Full-KYC PPIs have provisions for goods/services, funds transfer and cash withdrawal. The exact final product classification and authorisation route must be confirmed by counsel and RBI/provider engagement before production.

## 2. Core domain boundaries

The financial platform will have six explicit domains:
1. Customer Value
2. Payment/Clearing
3. Service Transactions
4. Settlement Payables
5. Refunds/Reversals
6. Reconciliation/Audit

Business services must not directly edit customer wallet balances.
All value movement must pass through a wallet/financial domain API and produce immutable financial events.

## 3. Customer-value accounts

At minimum, the financial model must distinguish:

- Customer liability/value — value attributable to a specific customer.
- Customer reserved value — value temporarily unavailable while a service or payout is unresolved.
- Customer pending value — value whose external payment confirmation or posting is not yet final.
- Customer withdrawn value — value permanently removed through an authorised withdrawal.
- Customer adjustment — a compensating entry linked to a previous transaction; never a silent balance edit.
- mPay revenue — platform/service fees belonging to mPay, separate from customer value.
- Vendor payable — amount contractually owed to a rental vendor.
- Recharge-provider payable — amount due for an external recharge transaction where applicable.
- Payment-provider clearing — amounts awaiting confirmation/settlement/reversal.

## 4. Ledger principle

Use an immutable, append-oriented financial ledger.

Available customer value is a derived position:

available customer value = confirmed customer value - reserved customer value - other legally required holds

A cached balance may be retained for performance, but it must be rebuildable from the ledger and protected by reconciliation checks.

Every financial entry should contain:
- unique entry ID;
- transaction ID;
- customer ID where applicable;
- account/domain type;
- debit/credit direction;
- amount and currency;
- source event;
- provider reference where applicable;
- timestamp;
- resulting state;
- correlation/idempotency key;
- linked compensating transaction where applicable.

## 5. Add Money

State machine:

CREATED -> PAYMENT_PENDING -> PAYMENT_CONFIRMED -> VALUE_POSTING -> VALUE_POSTED -> RECONCILIATION_PENDING -> RECONCILED

Exception states:
- PAYMENT_FAILED
- PAYMENT_UNKNOWN
- VALUE_POSTING_FAILED
- REFUNDED
- REVERSED

Rules:
1. Client success screens never create value.
2. Server verifies provider payment.
3. Provider reference must be unique.
4. Repeated callbacks are idempotent.
5. Payment confirmation and wallet posting are separate events.
6. Provider success with missing wallet posting becomes a reconciliation exception.
7. Wallet posting without authoritative payment confirmation is a critical exception.
8. Later refund/reversal creates a compensating value movement rather than rewriting history.

## 6. Wallet spending

Reservation model:
AVAILABLE -> RESERVED -> DEBITED

Failure: RESERVED -> RELEASED
External unknown: RESERVED -> PENDING_EXTERNAL_RECONCILIATION

A service must not directly subtract from a customer balance.
The service requests a reservation with customer, amount, currency, business transaction, idempotency key and applicable expiry/timeout.
Only the financial domain can convert reservation to final debit.

## 7. Recharge

Recharge has two independent lifecycles:

Customer-value lifecycle: customer value -> reserve -> debit/release.
Provider lifecycle: recharge request -> provider pending -> success/failure/unknown -> reconciliation.

Way2API is catalogue/plan information only.
PayU is the current intended actual recharge/payment execution route.
A provider timeout must not be treated as failure merely because the HTTP request timed out.

## 8. Rental

Rental payment: customer value -> reserve -> booking/payment confirmation -> debit.

Rental settlement: completed eligible booking -> calculate gross amount -> calculate contractual platform fee -> create vendor payable -> vendor settlement -> reconciliation.

Illustrative ₹1,000 transaction:
- gross customer consideration: ₹1,000;
- vendor payable: ₹900;
- mPay platform/service fee: ₹100.

This is illustrative only and remains subject to final contract, GST and accounting treatment.
The vendor payable must be independently identifiable from mPay revenue.

## 9. Withdrawal

State machine:
REQUESTED -> ELIGIBILITY_CHECKED -> VALUE_RESERVED -> PAYOUT_PENDING -> PAYOUT_CONFIRMED -> VALUE_DEBITED -> RECONCILED

Failure: PAYOUT_FAILED -> VALUE_RELEASED
Unknown: PAYOUT_UNKNOWN -> RECONCILIATION -> PAYOUT_CONFIRMED or PAYOUT_FAILED

The final payout mechanism must be one permitted by the authorised operating structure.
UPI ID validation is a technical control; it is not itself evidence that mPay is legally permitted to perform cash-out.

## 10. Refund and reversal

Refunds must identify the original transaction, provider reference, amount, reason, initiator, provider refund reference, state and reconciliation status.

A refund must use the legally and contractually required destination.
The current wallet-credit refund behaviour cannot automatically be treated as the final production refund mechanism. Authorised product rules and payment-provider contracts must determine whether value returns to the wallet, original payment source or another permitted destination.

## 11. Reconciliation

Three-way reconciliation is required wherever applicable:
1. mPay internal financial ledger;
2. external provider transaction records;
3. bank/settlement records.

Mismatch examples include provider success with no internal value, internal value with provider failure, provider refund with no internal reversal, external withdrawal paid while reservation remains open, vendor settlement marked successful without bank confirmation, and recharge successful externally while customer reservation remains pending.

Every mismatch receives severity, owner, first-seen timestamp, internal reference, provider reference, investigation state, resolution event and compensating transaction if required.

## 12. Idempotency

Idempotency must exist at every money-moving boundary:
- Add Money request;
- payment confirmation/webhook;
- wallet credit;
- wallet reservation/finalisation/release;
- recharge request;
- rental payment;
- vendor settlement;
- withdrawal;
- refund;
- reconciliation correction.

The same provider event must never create two financial effects.

## 13. Administrative controls

Admin users must never receive a generic edit-wallet-balance capability.

Permitted correction:
Original transaction -> investigation -> reason-coded compensating transaction -> reviewer/authorisation where required -> audit event -> reconciliation.

The original record remains unchanged.
High-risk operations should require role separation and, where appropriate, maker-checker approval.

## 14. Regulatory configuration layer

The following must be configuration/data-driven rather than scattered through business code:
- KYC level;
- balance limits;
- transaction limits;
- loading limits;
- withdrawal limits;
- velocity limits;
- supported funding methods;
- permitted wallet use cases;
- fees;
- refund rules;
- customer closure/redemption rules;
- risk/fraud holds;
- required authentication/AFA;
- reconciliation tolerances;
- reporting categories.

These values must only be activated according to the final authorisation and approved operating procedures.

## 15. Required technical services

Target components:
- WalletAccountService
- WalletLedgerService
- WalletReservationService
- PaymentTransactionService
- PaymentReconciliationService
- WithdrawalService
- RefundService
- ServiceSettlementService
- VendorPayableService
- FinancialAuditService
- RegulatoryLimitService

Existing services can be refactored incrementally into these boundaries; no broad rewrite is required.

## 16. Production release gates

The wallet production flag remains disabled until evidence exists for:
- incorporated mPay entity;
- final legal classification;
- required RBI authorisation/approval;
- approved customer-fund safeguarding/settlement arrangement;
- approved bank/current-account structure;
- PayU/Razorpay or other provider contracts under the company entity;
- KYC/AML/CFT controls;
- customer limits and redemption rules;
- grievance/dispute process;
- refund/reversal process;
- reconciliation and audit controls;
- security/system audit requirements;
- required customer terms/disclosures;
- operational incident runbook.

## 17. Engineering release policy

Before authorisation:
- development and test environments may exercise the financial state machine with mock/sandbox providers;
- production customer-value loading and withdrawal remain disabled;
- no personal-name payment account is treated as the production financial structure;
- no claim of RBI authorisation is made;
- no customer funds are accepted under the assumption that later authorisation will cure the arrangement.

After authorisation:
- activate only capabilities explicitly permitted by the approved structure;
- preserve immutable auditability;
- reconcile continuously;
- monitor limits and risk controls;
- retain evidence for regulatory/provider/audit review.

## 18. Architectural principle

The consumer application should not need to know whether a regulatory requirement is implemented by a direct mPay-authorised component, a bank, an authorised payment system participant, a payment provider, or another permitted regulated infrastructure.

The financial domain owns that boundary.
This prevents a later regulatory requirement from forcing a rewrite of recharge, rental, Android, web and admin applications.

## 19. Explicit non-assumptions

This design does not yet select a final licence/category.
It does not assume that mPay is currently authorised, that the existing current account can safeguard customer value, that PayU/Razorpay settlement alone establishes a compliant wallet, that the current withdrawal provider is the final permitted payout route, or that existing application balances are legally recognised stored value.

Those decisions belong to the regulatory/legal workstream and must be evidenced before production activation.