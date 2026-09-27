# mPay Regulator / Bank / Payment-Partner Q&A

Use: evidence-backed answer bank. Replace placeholders only after the corresponding fact is verified.

## 1. What exactly is mPay?

Answer:

> mPay is a technology-enabled consumer services platform. Its current service surfaces are mobile recharge and chauffeur-driven car-rental services, with payment functionality integrated through payment providers. The platform maintains transaction and service records so that customer instructions, provider outcomes, service fulfilment, refunds and reconciliation can be tracked end-to-end.

Evidence: product pages, service contracts, backend transaction models, payment-provider contracts.

## 2. Is mPay a bank?

Answer:

> No. mPay does not represent itself as a bank and does not provide deposit accounts or banking services.

Control: product copy and terms must not use banking/deposit terminology.

## 3. Is mPay issuing a regulated wallet/PPI?

Answer — current status: LEGAL DECISION REQUIRED

> The platform currently has wallet-like stored-value functionality in its application and backend. Before production launch, we will operate that functionality only under a legally permitted structure—either through an appropriately authorised/regulated partner arrangement or after obtaining any authorisation required for mPay's own issuance/operation. We will not rely solely on the fact that the balance is represented in an internal database.

This is the most important unresolved regulatory answer.

## 4. Where does customer money go when Add Money is used?

Technical answer currently observed:

> A customer payment is initiated with the selected payment provider. After server-side verification of the provider payment, mPay records a corresponding wallet credit in its internal ledger.

Evidence: WalletService, PaymentSettlementService, RazorpayService.

Required final answer: add the actual bank/settlement destination from provider contracts and bank statements. Engineering code alone cannot establish the legal custody of funds.

## 5. When does mPay consider money available?

Answer:

> Customer value is made available for service use only after the server has verified the provider-side payment outcome and the corresponding internal transaction is recorded. A client-side success screen is not treated as sufficient evidence of settlement.

Required control: all payment providers must follow the same server-authoritative settlement contract.

## 6. Can the wallet be used for recharge?

Answer:

> The current application supports wallet-funded mobile recharge. The transaction is represented in the wallet ledger and linked to the recharge transaction.

Regulatory qualification: if the wallet is stored value accepted for third-party services, its legal classification must be resolved before production.

## 7. Can the wallet be used for car rental?

Answer:

> The current application supports wallet-funded car-rental bookings. Rental payment is recorded as a wallet transaction and linked to the booking.

Required evidence: vendor contract, settlement model and platform-fee treatment.

## 8. Can customers withdraw wallet money?

Answer:

> The current application provides withdrawal to a customer-supplied UPI ID through a configured payout provider. The platform creates a pending withdrawal, reserves the requested wallet value, and only finalises the wallet debit after a confirmed successful provider outcome. Failed or reversed outcomes release the reservation.

Regulatory qualification: cash-out/fund transfer strengthens the PPI classification question and therefore must be governed by the final legal structure.

## 9. How is duplicate withdrawal handled?

Answer:

> Withdrawal requests require a client request ID. An existing request with the same user and client request ID is returned rather than creating a second payout. Provider outcomes are also persisted against the withdrawal record.

Evidence: WithdrawalService, WithdrawalPersistenceService.

## 10. What happens if the payout provider times out?

Answer:

> A transport failure is not automatically interpreted as payout failure. The withdrawal is moved to a processing/unknown-outcome state and the wallet reservation is retained until a provider status or webhook resolves the outcome.

This prevents double payout and premature release of customer value.

## 11. What happens if a recharge provider fails?

Required final answer must distinguish:

- payment captured;
- wallet credit;
- recharge request accepted;
- recharge successful;
- recharge failed;
- refund/reversal.

The platform must reconcile these states rather than treating them as one boolean transaction.

## 12. What happens if payment succeeds but mPay crashes before credit?

Target answer:

> The payment provider transaction remains the source event. mPay does not rely solely on the synchronous application response. The transaction is recoverable through provider verification/webhook/reconciliation, and an idempotent settlement operation can safely be replayed.

Required implementation: durable payment order + provider reference + webhook/reconciliation job + idempotent settlement.

## 13. What happens if wallet credit succeeds but service fulfilment fails?

Target answer:

> The payment/ledger state and service state are treated as separate but linked records. A service failure triggers the applicable reversal/refund workflow rather than an untracked balance adjustment. The system retains the original transaction, provider references and adjustment reference for audit.

Important: external provider side effects cannot be rolled back by a database transaction; a compensation/reconciliation mechanism is mandatory.

## 14. How are refunds handled?

Answer:

> Refunds are processed according to the original payment route and the applicable service policy. The customer-facing record identifies the original transaction and the resulting refund/reversal status.

PayU documentation states that refunds are sent to the source account used for the payment and provides a typical 5–21 day reflection period, subject to the payment method/bank. This must be reflected accurately in mPay's published policy rather than promising an unconditional instant refund.

## 15. Who receives car-rental money?

Current answer: LEGAL/BUSINESS FACT REQUIRED

The final answer must identify whether:

A. the vendor is the merchant/service provider and mPay facilitates booking/payment and later settlement;

B. mPay is the merchant/service provider and independently contracts with vendors;

C. a regulated payment partner collects and settles directly to vendors.

The answer determines the contractual, tax, settlement and payment-regulatory design.

## 16. How is the rental platform fee treated?

Current product concept: MPAY_RENTAL_PLATFORM_FEE_PERCENT.

Final answer must state:

- gross booking amount;
- platform fee;
- taxes on the platform fee, if applicable;
- vendor payable;
- refund/cancellation treatment;
- settlement date;
- whether the fee is deducted before or after settlement;
- who issues the customer invoice;
- who issues vendor settlement statement.

No regulatory answer should be invented until the commercial model is confirmed.

## 17. What is mPay's role in mobile recharge?

Current answer: CONTRACT REQUIRED

The final answer must state the actual recharge provider(s), whether the route is BBPS, the contractual relationship, who is the biller, who collects customer funds, and who settles the recharge amount.

RBI's 2024 BBPS Directions expressly include prepaid-service recharge within the bill-payment framework. If mPay operates within BBPS, its exact role and participant chain must be evidenced.

## 18. Does mPay perform KYC?

Current answer: SCOPE REQUIRED

KYC obligations depend on the final regulated role. If mPay operates only as a technology/service platform, its onboarding requirements may differ from those of a PPI issuer/PA/BBPS participant. If mPay itself operates a regulated payment product, the applicable KYC/AML requirements must be implemented and evidenced.

## 19. How does mPay protect customer money?

Target answer:

> Customer funds are handled through the regulated settlement structure applicable to the selected payment service. mPay maintains transaction-level records and reconciliation controls. Where a regulated stored-value product is used, safeguarding/escrow requirements are implemented by the authorised issuer in accordance with the applicable RBI framework.

Do not use this answer until the actual settlement structure is verified.

## 20. Can mPay use customer money for its own expenses?

Target answer:

> No customer payment balance is treated as mPay's unrestricted operating cash. Customer value is separated logically and, where legally required, safeguarded through the regulated settlement/escrow structure. Platform revenue is recorded separately from customer transaction value.

This requires accounting and bank-reconciliation evidence.

## 21. What happens on account closure?

Required final answer:

- outstanding customer value;
- pending service transactions;
- pending refunds;
- pending withdrawals;
- identity verification;
- final settlement/refund route;
- record-retention requirements.

## 22. What happens in fraud/unauthorised transaction cases?

Target answer:

> mPay provides a customer reporting channel, restricts relevant operations where appropriate, preserves transaction evidence, coordinates with the relevant payment provider/regulated entity, and follows the applicable dispute, fraud and refund process.

## 23. What audit evidence can mPay produce?

The system should be able to produce, for a selected transaction:

1. user/account reference;
2. customer instruction timestamp;
3. provider/order/reference ID;
4. request amount/currency;
5. server verification evidence;
6. provider status;
7. mPay transaction state;
8. wallet ledger entry, if applicable;
9. service transaction/booking reference;
10. provider fulfilment result;
11. refund/reversal/chargeback reference;
12. final settlement status;
13. relevant timestamps;
14. actor/admin actions;
15. reconciliation status.

## 24. Core regulator answer

> "Our compliance model is based on separating service logic from regulated payment movement, maintaining a complete audit trail, using server-authoritative payment status, preventing duplicate settlement, reconciling external provider outcomes with internal records, and operating any stored-value functionality only within a legally permitted regulated structure."

This is the central answer that the technical architecture should make demonstrably true.
