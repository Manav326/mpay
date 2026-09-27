# mPay — Confirmed Business Answers

Date: 2026-09-27  
Source: Business-owner answers supplied for regulatory architecture.

1. Legal entity: A Private Limited company is intended, but incorporation has not yet occurred. Final legal name is pending incorporation.
2. PayU merchant account: currently in the founder's personal name; intended to move to the company's current account/entity after incorporation.
3. Razorpay merchant account: currently in the founder's personal name; intended to move to the company's current account/entity after incorporation.
4. Add Money settlement: intended settlement destination is the company's current account after incorporation.
5. Wallet model desired: Yes. Customer may add money, retain a balance, use it for mobile recharge and car rental, and later withdraw unused value on demand.
6. Withdrawal: Yes. Customers should be able to withdraw unused Add Money value on demand.
7. Recharge: Way2API is used for fetching recharge plans/catalogue data. PayU is used for actual recharge execution/payment.
8. BBPS relationship: None currently. BBPS is a future phase.
9. Rental supply: Vehicle owners/vendors are independent third parties.
10. Rental economics: customer pays gross rental amount; intended example is ₹1,000 customer charge, ₹900 vendor payable and ₹100 mPay platform fee, subject to final commercial/tax treatment.
11. Rental merchant-facing position: mPay is intended to be the customer-facing supplier/merchant for the rental service, while independent vendors provide the underlying vehicle/driver service.
12. Target regulated-payment architecture: Option A at the business-model level, with a regulated wallet/payment structure. mPay is intended to operate as a customer-facing service platform for recharge and chauffeur-driven rental services, using PayU/other appropriately authorised payment infrastructure for customer payments. mPay earns contractual service/platform commissions or fees from the underlying service transactions. Where customers preload funds and maintain a reusable balance for subsequent third-party services or withdrawal, that stored-value functionality will be implemented through an appropriately regulated wallet/payment structure rather than treating the customer's funds as unrestricted mPay operating funds.

## Compliance implications

- The current personal-name PayU/Razorpay arrangement is a pre-incorporation setup and must not be treated as the final production merchant structure.
- After incorporation, provider KYC, contracts, settlement accounts and approved business purpose must be aligned to the incorporated operating entity and intended regulated-partner structure.
- The desired persistent, spendable and **withdrawable-on-demand** wallet creates a material stored-value/PPI issue. RBI's current PPI directions distinguish closed-system PPIs from regulated PPI structures and state that entities issuing PPIs require the applicable RBI approval/authorisation framework. citeturn0search1
- The fact that PayU/Razorpay settlement reaches the company's current account does not by itself make the customer's reusable wallet balance unrestricted company revenue or operating cash. The target architecture must establish the legal holder/issuer of customer value, safeguarding/settlement arrangement and redemption mechanism before production.
- The rental model requires a formal merchant/vendor agreement and accounting treatment for gross customer consideration, vendor payable and mPay platform/service revenue.
- Because mPay intends to collect customer money and subsequently pay independent rental vendors, the payment-collection/settlement structure must be reviewed against the applicable payment-aggregator/merchant-settlement framework. RBI materials describe escrow arrangements for authorised PAs and require permitted credits/debits for such accounts; this should be settled with counsel and the selected payment provider before production. citeturn1search0
- BBPS must not be represented as an existing mPay capability or regulatory status. It is a future integration subject to the actual participant/provider structure. RBI's BBPS Directions establish specific participant and escrow arrangements for BBPS activities. citeturn1search3

## Important architectural distinction

The business answer confirms the desired **economic flow**:

**Customer → PayU/Razorpay → mPay banking/settlement structure → customer wallet value → service debit → mPay pays vendor/provider**

However, this is not yet the final **legal fund-flow architecture**.

For production, we must determine whether the regulated wallet/payment structure is:

1. a regulated partner's wallet/payment instrument with mPay acting as the service/technology layer; or
2. another specifically authorised structure available to the incorporated mPay entity.

We should not implement the current-account model as though it were automatically compliant merely because PayU/Razorpay settles there.

## Recharge clarification

The roles are now clear at the product level:

**Way2API → plan/operator/catalogue information**

**PayU → actual recharge execution/payment**

Way2API therefore should not be represented as the holder of customer funds or the payment/recharge settlement institution unless its actual agreement says otherwise.

These facts are business requirements and must be revalidated against contracts and actual bank/provider flows before being used as a final regulator representation. Option A describes the overall customer/service model; it does not by itself mean that mPay is authorised to issue PPIs or operate a payment system.
