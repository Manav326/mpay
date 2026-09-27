# mPay Product & Regulatory Classification

## 1. Executive classification

mPay should not be presented as one undifferentiated "wallet/recharge/rental app". The platform contains separate regulatory-risk surfaces.

### A. Stored value / wallet

Current product behaviour observed in code:

- customer can add money through a payment gateway;
- mPay records a persistent wallet balance;
- wallet value can be used for mobile recharge;
- wallet value is used for car-rental transactions;
- wallet value can be withdrawn to a UPI ID;
- wallet transactions are maintained in an internal ledger;
- withdrawal reserves balance and later converts the reservation into a debit on successful payout.

This combination creates a strong PPI/stored-value regulatory question.

RBI's PPI framework says PPIs facilitate purchase of goods/services and other permitted activities against stored value, and states that no entity may set up and operate payment systems for PPIs without prior RBI approval/authorisation. Full-KYC PPIs are specifically permitted to support goods/services, funds transfer and cash withdrawal, subject to the applicable framework.

Engineering conclusion: we must not rely on a label such as "mPay wallet" or "internal wallet" to argue that the activity is outside PPI regulation. The legal analysis depends on what the instrument does and who holds/controls the customer funds.

### B. Mobile recharge

RBI's BBPS Directions, 2024 define a bill to include a notice/advice for recharge of prepaid services. BBPS provides an integrated bill-payment framework, and customer-facing interfaces can be provided by Customer Operating Units or Agent Institutions.

Therefore the recharge flow should be documented according to the actual provider route:

- If the recharge is routed through BBPS, mPay must identify its BBPS role/contractual chain and preserve the BBPS transaction/reference identifiers.
- If the recharge provider is outside BBPS, mPay must document the provider's legal role, mPay's contractual role, settlement path, refund/reversal process and why the route is permitted.
- mPay must not describe itself as a BBPS participant unless the contractual/certification evidence supports that statement.

### C. Car rental with driver

The car-rental feature is primarily a service/marketplace activity rather than a payment instrument by itself.

mPay should document:

- who is the actual service provider;
- whether mPay acts as marketplace/intermediary or merchant/service provider;
- vendor onboarding and due diligence;
- customer booking contract;
- driver/vehicle responsibility;
- fare calculation;
- cancellation/refund rules;
- customer support;
- vendor settlement;
- platform fee;
- tax/GST treatment;
- applicable state/local transport requirements.

If mPay collects customer money for third-party vendors and later settles vendors, the payment-collection structure must be reviewed alongside the applicable Payment Aggregator framework and the contractual model.

## 2. Why the current wallet design needs a legal decision

There are two structurally different models.

### Model 1 — mPay-issued stored value

Customer -> regulated payment method -> mPay-controlled stored value -> recharge/rental/withdrawal.

If mPay itself issues/operates the stored-value instrument, the product needs a formal PPI/payment-system legal analysis and, where required, RBI authorisation or a qualifying regulated structure.

The engineering system would then need to support the applicable KYC, limits, safeguarding/escrow, reconciliation, audit, grievance and transaction controls.

### Model 2 — regulated-partner money

Customer -> regulated payment provider/PPI/PA -> regulated account or balance -> mPay obtains authorised payment status -> service provider/vendor settlement.

In this model, mPay can remain primarily a technology/service layer, subject to the precise contracts and regulatory permissions of the partner structure.

### 2A. Canonical business model selected

The business owner has selected the following target description:

> "mPay is intended to operate as a customer-facing service platform for recharge and chauffeur-driven rental services, using PayU/other appropriately authorised payment infrastructure for customer payments. mPay earns contractual service/platform commissions or fees from the underlying service transactions. Where customers preload funds and maintain a reusable balance for subsequent third-party services or withdrawal, that stored-value functionality will be implemented through an appropriately regulated wallet/payment structure rather than treating the customer's funds as unrestricted mPay operating funds."

Therefore, "Option A" refers to the **overall business/service-platform model**, not a conclusion that mPay may independently issue stored value without authorisation.

Target design principle: preserve the Option A consumer/service experience while implementing the stored-value/payment layer only through an appropriately regulated structure. The final choice between a regulated partner and direct authorisation remains a legal/provider decision.

## 3. What mPay should not say to a regulator

Do not say:

- "The wallet is only an internal database balance, so it is not regulated."
- "PayU/Razorpay handles payments, therefore mPay has no regulatory responsibility."
- "Withdrawal is only a technical payout feature."
- "Car rental and recharge are our own services" if third-party vendors/operators actually perform them.
- "We are BBPS" unless the contractual/authorisation evidence exists.
- "Customer funds never touch mPay" unless bank/provider statements and contracts prove the actual settlement path.

## 4. Preferred regulator-facing position

Subject to legal confirmation:

> "mPay is designed as a technology-enabled consumer services platform offering mobile recharge and chauffeur-driven car-rental services. Payment initiation and regulated movement/settlement of customer funds are intended to be performed through appropriately regulated payment partners and contractual counterparties. mPay maintains transaction records and service-state records for reconciliation and customer support. Where stored-value functionality is offered, mPay will operate it only under a legally permitted and appropriately authorised/regulated structure; it will not rely on an internal ledger label to avoid applicable payment-system regulation."

This wording must be updated once the actual merchant, PA/PPI, BBPS and vendor contracts are confirmed.

## 5. Sources

- RBI Master Directions on PPIs: https://www.rbi.org.in/Scripts/NotificationUser.aspx/NotificationUser.aspx?Id=12156
- RBI FAQ on PPIs: https://rbi.org.in/Commonman/English/Scripts/FAQs.aspx?Id=2812
- RBI BBPS Directions, 2024: https://www.rbi.org.in/scripts/NotificationUser.aspx?Id=12616
- RBI Payment Aggregator 2025 press release: https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx?prid=61351
- PayU merchant terms: https://payu.in/tnc-new/
