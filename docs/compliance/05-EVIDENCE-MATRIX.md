# mPay Compliance Evidence Matrix — Initial Baseline

Status values: OBSERVED, PARTIAL, UNKNOWN, GAP, BLOCKER.

| # | Requirement / question | Current status | Engineering evidence | Business evidence required | Action |
|---|---|---|---|---|---|
| 1 | Legal owner identified | UNKNOWN | None required from code | Legal entity documents | Founder to provide |
| 2 | Product regulatory classification | PARTIAL | Wallet + recharge + rental architecture observed | Legal opinion/business model | Finalise classification |
| 3 | Wallet legal basis | BLOCKER | WalletService, withdrawal flow | PPI/partner agreement/legal opinion | Decide own PPI vs regulated partner |
| 4 | Customer-money settlement account | UNKNOWN | Provider integration code only | Bank/provider statements and contracts | Verify actual destination |
| 5 | PayU permitted purpose | UNKNOWN | PayU integration exists | PayU KYC/approval/contract | Verify approved use |
| 6 | Razorpay permitted purpose | PARTIAL | Razorpay wallet order/verification | Razorpay KYC/contract | Verify wallet/add-money approval |
| 7 | Payment server verification | OBSERVED/PARTIAL | Razorpay signature + provider status check | Provider documentation/config | Standardise across providers |
| 8 | Payment webhook | UNKNOWN | Must inspect provider endpoints/config | Provider dashboard evidence | Verify and implement |
| 9 | Payment idempotency | OBSERVED/PARTIAL | Client request IDs and external refs | Test evidence | Complete across all flows |
| 10 | Wallet ledger | OBSERVED | WalletService + WalletTransaction records | Accounting mapping | Strengthen append-only controls |
| 11 | Wallet reservation | OBSERVED | WithdrawalPersistenceService | Legal model | Preserve only under approved wallet model |
| 12 | Withdrawal beneficiary verification | PARTIAL | UPI syntax validation | Provider/customer ownership policy | Add ownership/risk controls |
| 13 | Withdrawal reconciliation | PARTIAL | Processing/unknown state | Provider settlement reports | Build scheduled reconciliation |
| 14 | Recharge classification | UNKNOWN | RechargeService/provider integrations | Provider/BBPS agreement | Determine exact role |
| 15 | Recharge refund/reversal | PARTIAL | Transaction statuses exist | Provider TAT/contract | Build formal compensation flow |
| 16 | Rental vendor settlement | UNKNOWN | Rental/payment records | Vendor agreement + bank flow | Define merchant/settlement model |
| 17 | Rental platform fee | OBSERVED/PARTIAL | MPAY_RENTAL_PLATFORM_FEE_PERCENT | Commercial/GST treatment | Map to accounting |
| 18 | Customer refund policy | UNKNOWN | Some refund concepts exist | Published policy | Finalise policy |
| 19 | Grievance mechanism | PARTIAL | Customer support surfaces exist | Named grievance contact/process | Formalise and publish |
| 20 | Privacy/data compliance | PARTIAL | App/web/backend data collection exists | Privacy notice/data map | Complete DPDP/data audit |
| 21 | Account deletion | PARTIAL | Public mechanism exists from product work | Deletion execution evidence | Verify backend completion |
| 22 | Admin financial audit trail | PARTIAL | Admin financial views exist | Access-control/audit logs | Verify immutable audit trail |
| 23 | Incident/reconciliation runbook | GAP | Not yet formalised | Operational ownership | Create and test |
| 24 | Regulatory sign-off | BLOCKER | Engineering cannot provide legal sign-off | Qualified legal/compliance review | Required before launch |

## Evidence standard

For every completed row retain:

- source document;
- version/date;
- code/config path;
- screenshot or exported report where appropriate;
- test result;
- reviewer;
- completion date.

## Current blockers

1. Legal entity/business facts.
2. Exact customer-money settlement route.
3. Whether mPay itself or a regulated partner operates the stored-value balance.
4. PayU/Razorpay permitted use under merchant agreements.
5. Exact recharge/BBPS role.
6. Rental merchant/vendor settlement model.
