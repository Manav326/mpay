# mPay Compliance — Required Business / Legal Answers

These questions are intentionally separated from engineering. The final regulator-facing position must be based on these facts and supporting documents.

## A. Business identity

1. Exact legal entity name that will own/operate mPay:
2. Company type (Private Limited / LLP / Proprietorship / other):
3. CIN, if applicable:
4. Registered office state:
5. GST registration status:
6. PAN/TAN status:
7. Bank account(s) used for mPay collections/settlements:
8. Is the mPay brand owned by the same legal entity?

## B. Wallet / Add Money

9. When a customer adds ₹1,000, which legal entity's bank/escrow/settlement account ultimately receives the funds?
10. Is the PayU/Razorpay merchant account registered in mPay's legal entity name or another entity's name?
11. Is the money settled to an mPay operating/current account, a nodal/escrow account, a PPI issuer account, or another account?
12. Does mPay have any contractual right to use the collected customer money before the customer spends/withdraws it?
13. Is the wallet intended to remain a permanent customer balance?
14. What is the maximum wallet balance intended?
15. Are customers allowed to withdraw any unused Add Money amount?
16. Is withdrawal permitted back to the same source only, or to any verified UPI/bank account?
17. Is there any fee for Add Money?
18. Is there any fee for withdrawal?
19. Is there any expiry of wallet balance?
20. Is interest/return ever paid on wallet balance?

## C. Payment providers

21. Exact PayU product/service being used:
22. Exact Razorpay product/service being used:
23. Are PayU/Razorpay accounts in the same legal entity name as mPay?
24. What do the provider contracts say mPay's permitted business purpose is?
25. Does either provider explicitly approve wallet loading/stored value?
26. Does either provider explicitly approve payouts/withdrawals?
27. Which provider receives the customer's original payment?
28. Which provider performs settlement and to which bank account?
29. Are webhooks enabled in production?
30. Are refunds initiated through the same provider/payment route?

## D. Mobile recharge

31. Exact recharge provider(s): PayU, Way2API, BBPS participant, telecom operator, or other?
32. Is mPay a direct BBPS participant/Agent Institution/COU, or does another entity provide the BBPS interface?
33. Who is the biller/service provider for the recharge?
34. Who receives the customer's money?
35. Who bears failed-recharge/refund liability?
36. What provider reference/RRN is generated?
37. What are the provider's SLA/TAT rules for pending transactions?

## E. Car rental

38. Are vehicle owners/vendors independent third parties?
39. Does mPay or the vendor issue the customer invoice for the rental?
40. Who is the contractual supplier of the rental service to the customer?
41. Who receives the rental payment?
42. Does mPay collect money on behalf of vendors?
43. Does a regulated payment partner settle vendors directly?
44. How is MPAY_RENTAL_PLATFORM_FEE_PERCENT charged?
45. Is GST charged on the platform fee?
46. Who bears cancellation/refund amounts?
47. Who is responsible for driver, vehicle, insurance, permit and transport compliance?
48. What documents are collected from vendors before activation?

## F. Withdrawals

49. Exact payout provider used in production:
50. Does the provider contract permit consumer wallet/payout use?
51. Is the payout sent to a verified bank account/UPI belonging to the same customer?
52. What beneficiary verification is performed?
53. What is the maximum daily/monthly withdrawal?
54. Are withdrawals manually reviewed above any threshold?
55. What happens when the provider returns an uncertain/unknown status?

## G. Customer protection

56. Exact support email/phone:
57. Exact grievance officer/contact:
58. Refund policy URL:
59. Cancellation policy URL:
60. Privacy policy URL:
61. Terms of service URL:
62. Account deletion mechanism URL:
63. Published payout/refund TAT:
64. How are fraud/unauthorised transaction complaints handled?

## H. Required documents

Please provide copies/links of:

- legal entity incorporation documents;
- GST certificate, if applicable;
- bank account proof;
- PayU agreement/KYC approval;
- Razorpay agreement/KYC approval;
- recharge-provider agreement;
- BBPS/BBPOU/Agent Institution agreement, if applicable;
- rental vendor agreement;
- platform-fee terms;
- privacy policy;
- terms of service;
- refund/cancellation policy;
- grievance policy;
- data-processing/vendor agreements where applicable.

## Answers received — 2026-09-27

1. Intended operating entity: an Indian company to be incorporated; exact legal name to be confirmed.
2. Current PayU merchant account: individual founder name. Intended post-incorporation structure: company current account / company merchant arrangement.
3. Current Razorpay merchant account: individual founder name. Intended post-incorporation structure: company current account / company merchant arrangement.
4. Customer Add Money currently settles into a current account; intended post-incorporation settlement is the company's current account.
5. Intended wallet behaviour: customer can add money, retain balance, use it for recharge/rental, and withdraw unused balance.
6. Intended withdrawal: yes, including unused Add Money balance.
7. Intended recharge route: PayU now; BBPS later.
8. Current BBPS relationship: none.
9. Rental suppliers: independent third-party vehicle/driver vendors.
10. Intended rental economics: example model confirmed — customer pays ₹1,000, vendor payable ₹900, mPay platform/service fee ₹100, subject to final commercial/tax treatment.
11. Customer-facing rental supplier/invoice position: mPay is intended to be the customer-facing supplier/contracting party.
12. Business decision: Option A — mPay itself is intended to operate the stored-value wallet/withdrawal model, subject to obtaining/meeting the regulatory authorisation and compliance requirements.

## Consequence of Option A

Option A materially changes the compliance path. mPay should not operate the current wallet/withdrawal functionality as a self-issued PPI merely because the software is ready.

RBI's current PPI Master Directions state that non-bank PPI applicants must be companies incorporated in India, their MoA must cover PPI issuance, and they require RBI authorisation. The Directions also require minimum positive net worth of ₹5 crore at application and ₹15 crore by the end of the third financial year after final authorisation, subject to the applicable current framework. Full-KYC PPIs can support goods/services and specified fund-transfer/closure mechanisms, subject to limits and conditions. Therefore incorporation alone is not equivalent to authorisation. citeturn1view0

Until the legal/regulatory route is completed, the engineering target must be to keep the PPI capability behind a controlled production gate rather than treating the current personal merchant/current-account arrangement as the permanent company/payment architecture.

The current personal PayU/Razorpay arrangement also must not be represented to customers or regulators as a company-owned merchant account. Before company production launch, merchant KYC, bank settlement, contractual purpose and payment-provider approval must be aligned to the incorporated entity.

## Rental model — preliminary legal architecture

The intended model is:

Customer -> mPay as customer-facing contracting/supplying party -> independent vendor fulfils the chauffeur-driven rental service -> mPay pays/settles vendor under a vendor agreement -> mPay retains its disclosed platform/service margin.

This is preferable to describing mPay as merely "collecting money for vendors", but it must be validated against the actual contracts, invoices, GST treatment, vendor liability, transport obligations and payment-provider terms. If, in substance, mPay only collects and passes through customer funds for third-party vendors, the Payment Aggregator analysis may apply. The technical and contractual model must therefore be kept aligned.

## Recharge model — preliminary legal architecture

Current target:

Customer -> mPay interface -> authorised/contracted recharge/payment route -> telecom/biller fulfilment.

mPay currently has no BBPS relationship. Therefore mPay must not describe itself as a BBPS participant. If BBPS is introduced later, the exact role (for example, through an authorised BBPOU/Agent Institution/other permitted participant arrangement) must be contractually and technically documented. RBI's 2024 BBPS Directions include prepaid recharge in the BBPS bill definition and specify participant roles. citeturn0search0

## Final decision question

Option A is selected. Before implementation is finalised, obtain qualified Indian regulatory counsel's written classification of:

- mPay wallet as a PPI/stored-value product;
- mPay's intended withdrawal mechanism;
- whether any PA authorisation is separately required for rental/recharge collection;
- exact role permitted for PayU and the eventual BBPS participant;
- required safeguarding/escrow/account structure;
- KYC/AML/PMLA/FIU obligations;
- GST/accounting treatment;
- customer refund/closure mechanics.

This legal opinion becomes a required evidence item in the production gate.
