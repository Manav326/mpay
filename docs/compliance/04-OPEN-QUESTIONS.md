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

## Final decision question

The most important business decision is:

Do you want mPay itself to operate the stored-value wallet and withdrawals, or do you want the wallet/payment balance to be provided by a regulated payment/PPI partner while mPay remains the technology/service layer?

This decision must be made before we finalise the regulator-facing legal position.
