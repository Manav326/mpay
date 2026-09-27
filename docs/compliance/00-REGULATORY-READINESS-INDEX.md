# mPay Regulatory & Compliance Readiness — Master Index

Status: Working regulatory-readiness dossier
Prepared: 2026-09-27
Scope: mPay Android app, user web portal, admin portal, backend and payment/service integrations
Legal status: Engineering/compliance working document; not a legal opinion. Final regulatory classification and licensing position must be confirmed by qualified Indian payments/regulatory counsel before production launch.

## 1. Purpose

This folder is the ordered evidence trail for answering questions from RBI, NPCI/BBPS participants, payment partners, auditors, banks, payment aggregators, and consumer/grievance authorities where legally applicable.

The governing principle is:

> mPay must describe the actual economic and technical flow of money, not merely the UI terminology used for that flow.

## 2. Product classification — preliminary

| Surface | Preliminary classification | Main regulatory concern |
|---|---|---|
| Add Money + persistent wallet balance + withdrawal | Stored-value/payment-instrument risk; potentially PPI activity if mPay itself issues/operates the stored value | RBI PPI authorisation, KYC, safeguarding/escrow, limits, cash-out/fund transfer, customer protection |
| Mobile recharge | Digital bill/recharge facilitation | BBPS/authorised participant structure, merchant/biller contracts, payment settlement, failed-transaction/refund handling |
| Car rental with driver | Service marketplace/booking intermediary | Vendor onboarding, consumer protection, payment collection/settlement, cancellation/refunds, tax/GST and transport-law obligations |

The wallet function is the highest-priority classification issue because customers can load money, retain a balance, spend it across more than one service, and withdraw it.

RBI's PPI framework covers payment instruments using stored value for goods/services and permitted financial uses, and states that no entity may set up and operate payment systems for PPIs without prior RBI approval/authorisation. Full-KYC PPIs have separate rules for goods/services, funds transfer and cash withdrawal. See the source register below.

## 3. Target regulatory posture

Until the legal structure is confirmed, mPay should be engineered toward these principles:

1. mPay is a technology/service platform, not an unlicensed deposit-taking or payment-system operator.
2. Customer money must be held/settled by the appropriately regulated payment partner/entity wherever regulation requires it.
3. mPay's target business model is a customer-facing service platform with reusable wallet value; the stored-value/payment layer must operate only through an appropriately regulated structure. Whether that is an authorised partner or direct authorisation by the eventual company is a legal/provider decision before production.
4. Every payment must have a traceable lifecycle: customer instruction -> provider transaction -> provider confirmation -> mPay transaction state -> service fulfilment -> settlement/refund/reversal.
5. A successful customer debit must never be treated as equivalent to a successful mPay credit without server-side verification.
6. Internal wallet records are an accounting/transaction representation; they must never be used to imply that mPay is legally entitled to hold customer funds unless the legal structure permits it.

## 4. Dossier sequence

1. 01-PRODUCT-CLASSIFICATION.md — regulatory classification and proposed legal posture.
2. 02-REGULATOR-QA.md — regulator/partner questions and evidence-based technical answers.
3. 03-TECHNICAL-CONTROLS.md — controls the platform must implement and demonstrate.
4. 04-OPEN-QUESTIONS.md — facts required from the business owner before final legal wording.
5. 05-EVIDENCE-MATRIX.md — requirement -> current status -> evidence -> gap -> action -> verification.
6. 06-MONEY-FLOW-SPECIFICATION.md — canonical money-flow state machine.
7. 07-INCIDENT-AND-RECONCILIATION-RUNBOOK.md — operational response to mismatches, duplicates, pending and reversals.
8. 08-LEGAL-POLICY-CHECKLIST.md — website/app disclosures and contractual documents.

## 5. Change-control rule

No compliance-related production code change should be made merely to make an answer look compliant. Each change must have:

- regulatory reason;
- business owner decision;
- technical design;
- test evidence;
- operational evidence;
- rollback/recovery behaviour;
- date and verifier.

## 6. Current repository evidence already observed

The current main branch contains:

- WalletService with balance, reserved balance, available balance, credits, reservations and wallet ledger entries.
- WithdrawalService with UPI-ID validation, provider selection, pending/processing/success/failure states and idempotent client request IDs.
- WithdrawalPersistenceService that reserves wallet value before payout and finalises/reverses the reservation based on provider outcome.
- PaymentSettlementService that credits wallet value after payment verification and, for recharge-purpose orders, subsequently invokes the recharge service.
- RazorpayService that creates wallet orders and verifies payment signatures/provider status before wallet credit.
- Admin financial views for recharge, withdrawal and ledger operations.

These are engineering observations, not a statement that the legal structure is compliant.

## 7. Important current technical observation

The current PaymentSettlementService recharge path credits the internal wallet and then invokes RechargeService within a Spring transaction. This requires a dedicated failure/reconciliation design because the external recharge provider is outside the database transaction boundary. A database rollback cannot automatically undo an external provider-side recharge.

This is a material compliance/reconciliation issue and is tracked in the technical controls dossier.

## 8. Business decisions recorded on 2026-09-27

- mPay intends to be operated by an Indian incorporated company (not yet incorporated).
- Current PayU/Razorpay accounts are in the founder's individual name; these are not the target permanent production structure.
- Customer Add Money is intended to settle to the company's current account after incorporation and provider re-onboarding/approval.
- The intended wallet permits loading, persistent balance, spending on recharge/rental and withdrawal of unused balance.
- The intended business model is Option A: mPay is the customer-facing service platform for recharge and chauffeur-driven rental, using PayU/other appropriately authorised payment infrastructure and earning contractual service/platform fees. The reusable stored-value component will use an appropriately regulated wallet/payment structure.
- Rental vendors are independent third parties; mPay is intended to be the customer-facing contracting/supplying party, with vendor settlement governed by vendor agreements.
- Current recharge route is PayU; BBPS is a future planned route and mPay currently has no BBPS relationship.

**Important:** These decisions do not establish regulatory authorisation. They define the target business model against which legal classification and technical controls will be designed.
