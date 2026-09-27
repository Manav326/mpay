# mPay — Confirmed Business Answers

Date: 2026-09-27
Source: Business-owner answers supplied for regulatory architecture.

1. Legal entity: A Private Limited company is intended, but incorporation has not yet occurred. Final legal name is pending incorporation.
2. PayU merchant account: currently in the founder's personal name; intended to move to the company's current account/entity after incorporation.
3. Razorpay merchant account: currently in the founder's personal name; intended to move to the company's current account/entity after incorporation.
4. Add Money settlement: intended settlement destination is the company's current account after incorporation.
5. Wallet model desired: Yes. Customer may add money, retain a balance, use it for mobile recharge and car rental, and later withdraw unused value.
6. Withdrawal: Yes. Customers should be able to withdraw unused Add Money value.
7. Recharge: PayU is intended as a recharge/payment provider initially; BBPS is planned for a later phase.
8. BBPS relationship: None currently.
9. Rental supply: Vehicle owners/vendors are independent third parties.
10. Rental economics: customer pays gross rental amount; intended example is ₹1,000 customer charge, ₹900 vendor payable and ₹100 mPay platform fee, subject to final commercial/tax treatment.
11. Rental merchant-facing position: mPay is intended to be the customer-facing supplier/merchant for the rental service, while independent vendors provide the underlying vehicle/driver service.
12. Target regulated-payment architecture: Option A at the business-model level, with a regulated wallet/payment structure. mPay is intended to operate as a customer-facing service platform for recharge and chauffeur-driven rental services, using PayU/other appropriately authorised payment infrastructure for customer payments. mPay earns contractual service/platform commissions or fees from the underlying service transactions. Where customers preload funds and maintain a reusable balance for subsequent third-party services or withdrawal, that stored-value functionality will be implemented through an appropriately regulated wallet/payment structure rather than treating the customer's funds as unrestricted mPay operating funds.

## Compliance implications

- The current personal-name PayU/Razorpay arrangement is a pre-incorporation setup and must not be treated as the final production merchant structure.
- Before production, provider KYC, contracts, settlement accounts and approved business purpose must be aligned to the incorporated operating entity and the intended regulated-partner structure.
- The desired persistent, spendable and withdrawable wallet creates a material stored-value/PPI issue. The target is not to treat the wallet as an ordinary unrestricted company balance. The wallet must operate through an appropriately regulated structure, whether through an authorised partner or an authorisation obtained by the eventual company, as confirmed by counsel/provider agreements before production.
- The rental model requires a formal merchant/vendor agreement and accounting treatment for gross customer consideration, vendor payable and mPay platform/service revenue.
- BBPS must not be represented as an existing mPay capability or regulatory status. It is a future integration subject to the actual participant/provider structure.

These facts are business requirements and must be revalidated against contracts and actual bank/provider flows before being used as a final regulator representation. Option A describes the overall customer/service model; it does not by itself mean that mPay is authorised to issue PPIs or operate a payment system.
