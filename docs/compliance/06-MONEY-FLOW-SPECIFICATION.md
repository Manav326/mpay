# mPay Canonical Money-Flow Specification — Target Architecture

Date: 2026-09-27
Status: Target architecture; implementation must follow legal/provider sign-off.

## 1. Target operating model

mPay will operate as a customer-facing consumer-services platform for mobile recharge and chauffeur-driven rental services. PayU/other appropriately authorised payment infrastructure will be used for customer payments. mPay earns contractual service/platform commissions or fees from underlying service transactions. The reusable stored-value component must operate through an appropriately regulated wallet/payment structure.

mPay should not treat its own database balance as an independently issued stored-value instrument.

## 2. Add Money

Target:

Customer -> mPay -> regulated payment/PPI partner -> regulated settlement/safeguarding structure -> payment confirmation -> mPay transaction state.

Only after authoritative provider confirmation should service availability be updated.

If a regulated partner provides/holds the actual wallet or stored value, mPay's database should mirror the authorised balance/transaction state rather than represent itself as the issuer. If the eventual company seeks direct PPI/payment-system authorisation, the implementation must instead satisfy that authorisation's safeguarding, KYC, limits, reconciliation and governance requirements.

## 3. Wallet use

Customer value is used for:

- mobile recharge;
- mPay rental booking;
- other explicitly approved services only.

The platform must identify whether each spend is a customer payment to mPay, a payment through the regulated partner, or a transfer from a partner-operated wallet.

## 4. Withdrawal

Target:

Customer withdrawal request -> authentication/risk checks -> regulated payout/wallet partner -> provider confirmation -> final transaction state.

The withdrawal must not be treated as an unrestricted transfer from an mPay operating account.

Before production, the regulated structure/partner contract must expressly support the intended payout/cash-out use. mPay's ordinary operating account must not be treated as an unrestricted substitute for the regulated customer-value arrangement.

## 5. Rental

Target commercial model:

Customer -> mPay checkout/payment arrangement -> gross rental consideration -> service fulfilment by independent vendor -> vendor payable ₹900 on a ₹1,000 example -> mPay service/platform revenue ₹100, subject to final GST/accounting treatment.

The actual regulated collection/settlement path must be chosen with the payment partner and legal/tax adviser.

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

Customer -> regulated payment/wallet arrangement -> mPay recharge service -> authorised recharge provider -> telecom/biller -> final provider status.

Future BBPS phase:

mPay must first establish its exact BBPS role through an authorised participant/partner. BBPS must not be represented as active until the participant/contractual status is established.

## 7. Fundamental accounting separation

The system must maintain separate concepts for:

- customer funds/value;
- service revenue;
- platform fee;
- vendor payable;
- payment-provider fees;
- refunds;
- chargebacks/disputes;
- operational adjustments.

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

## 9. Current pre-incorporation restriction

Until incorporation and payment-provider KYC/contract alignment are complete, the existing personal-name PayU/Razorpay setup must be treated as development/pre-production infrastructure. It should not be used as evidence of the final production regulatory structure.

## 10. Regulatory decision gate

Production launch of the persistent withdrawable wallet requires written confirmation of:

- who legally issues/provides the stored value;
- who holds/safeguards customer funds;
- which regulated entity performs collection and payout;
- whether the intended use is permitted under the partner's licence/authorisation;
- applicable KYC/AML/customer-protection obligations;
- refund/dispute/grievance responsibility.

Until these are answered, engineering should preserve the service abstraction so the regulated partner can be substituted without redesigning the consumer product.
