# mPay Canonical Money-Flow Specification — Target Architecture

Date: 2026-09-27
Status: Target architecture; implementation must follow legal/provider sign-off.

## 1. Target operating model

mPay will operate as a customer-facing consumer-services platform for mobile recharge and chauffeur-driven rental services.

The selected production intent is that the eventual mPay company itself will operate the customer-facing stored-value/wallet arrangement, maintain customer balances and support on-demand withdrawal of unused value.

This is a target regulatory architecture, not a claim that mPay is currently authorised to issue PPIs or operate a payment system. The exact authorisation/licensing route, safeguarding/settlement structure and permitted activities must be confirmed before production.

PayU/other appropriately authorised payment infrastructure will be used for customer payments where permitted. mPay earns contractual service/platform commissions or fees from underlying service transactions.

## 2. Add Money

Target conceptual flow:

Customer -> authorised payment infrastructure -> authorised settlement/safeguarding structure -> mPay wallet/value account -> service availability.

Only after authoritative provider confirmation should customer value become available.

The engineering model must keep payment confirmation separate from value posting. A provider-side payment cannot be undone by an application database rollback.

## 3. Wallet use

Customer value is intended for:

- mobile recharge;
- mPay rental booking;
- other explicitly approved services;
- on-demand withdrawal of unused value, subject to the final authorised structure and applicable limits/controls.

The wallet domain must distinguish customer value from mPay revenue, vendor payable and provider payable.

## 4. Withdrawal

Target:

Customer withdrawal request -> authentication/risk/KYC checks -> authorised payout mechanism -> provider confirmation -> wallet value finalisation -> reconciliation.

The withdrawal must not be treated as an unrestricted transfer from an mPay operating account merely because the current implementation can technically initiate a payout.

The final payout mechanism, customer limits, safeguarding and reconciliation requirements must be compatible with the authorisation/licensing structure.

## 5. Rental

Target commercial model:

Customer -> mPay checkout/payment/value arrangement -> gross rental consideration -> service fulfilment by independent vendor -> vendor payable -> mPay service/platform revenue.

Illustrative example:

- customer charge: ₹1,000;
- vendor payable: ₹900;
- mPay contractual service/platform fee: ₹100;

subject to final contractual, GST and accounting treatment.

mPay must have:

- vendor agreement;
- customer rental terms;
- cancellation/refund policy;
- vendor onboarding/KYC documents appropriate to the business model;
- vendor settlement records;
- platform-fee invoice/accounting;
- vehicle/driver compliance responsibility matrix.

## 6. Recharge

Initial phase:

Customer -> authorised payment/wallet arrangement -> mPay recharge service -> authorised recharge provider -> telecom/biller -> final provider status.

Current business answer:

- Way2API is used for plan/catalogue information.
- PayU is intended for actual recharge execution/payment.

Future BBPS phase:

mPay must first establish its exact BBPS role through an authorised participant/partner. BBPS must not be represented as active until the participant/contractual status is established.

## 7. Fundamental accounting separation

The system must maintain separate concepts for:

- customer funds/value;
- customer reserved value;
- service revenue;
- platform fee;
- vendor payable;
- recharge-provider payable;
- payment-provider fees;
- refunds;
- chargebacks/disputes;
- operational adjustments.

Customer value must never be silently converted into platform revenue.

## 8. Non-negotiable invariants

1. No client-side payment success can create financial credit by itself.
2. Every financial effect has a unique transaction reference.
3. Repeated provider callbacks cannot duplicate money movement.
4. Unknown provider outcomes remain unresolved until reconciled.
5. Historical financial records are not overwritten.
6. Corrections use linked compensating transactions.
7. External provider effects are reconciled independently of DB transactions.
8. Customer value is never silently converted into platform revenue.
9. Vendor payable and platform fee are separately identifiable.
10. Every completed financial transaction can be reconstructed from event history.

## 9. Pre-incorporation restriction

Until incorporation and payment-provider KYC/contract alignment are complete, the existing personal-name PayU/Razorpay setup must be treated as development/pre-production infrastructure. It should not be used as evidence of the final production regulatory structure.

## 10. Regulatory decision gate

Production launch of the persistent withdrawable wallet requires written confirmation of:

- the exact authorisation/licensing route available to the eventual mPay company;
- who legally issues/provides the stored value;
- who holds/safeguards customer funds;
- the permitted bank/current-account/settlement arrangement;
- which regulated entities perform collection and payout;
- whether the intended recharge/rental/withdrawal uses are permitted;
- applicable KYC/AML/customer-protection obligations;
- transaction and balance limits;
- refund/dispute/grievance responsibility;
- required audit/reporting/security controls.

Until these are answered, engineering should implement the wallet behind a stable domain abstraction and must not treat the current account as a substitute for the final regulated customer-value arrangement.
