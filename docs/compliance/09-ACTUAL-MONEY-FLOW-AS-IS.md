# 09 — Actual Money-Flow Investigation (AS-IS)

**Branch:** `compliance/regulatory-readiness`  
**Repository:** `Manav326/mpay`  
**Investigation date:** 2026-09-27  
**Status:** Engineering evidence capture — not a legal opinion

## 1. Purpose

This document records the current implementation-level money flow before compliance architecture changes are made.

The purpose is to separate:

- customer value / customer balance
- payment-gateway collection
- wallet ledger entries
- recharge provider payable
- rental gross amount
- vendor payable
- mPay/platform fee
- withdrawal/cash-out
- refunds/reversals

No production application-code change is made as part of this investigation.

## 2. Current architecture observed

The current backend has:

- `PaymentGatewayService`
- `PaymentSettlementService`
- `WalletService`
- `RechargeService`
- `RechargeTransactionWorkflowService`
- `RentalPaymentService`
- `RentalPayoutService`
- `WithdrawalService`
- `WithdrawalPersistenceService`
- PayU payment gateway and payout providers
- Razorpay payment/payout integration
- mock payment and payout providers

The wallet is currently an internal database balance with a posted transaction ledger and a separate reservation amount.

## 3. Flow A — Add Money / general wallet top-up

### Current sequence

1. Customer requests a wallet payment order.
2. `PaymentGatewayService.createWalletOrder()` resolves the requested/configured gateway.
3. Provider creates a payment order.
4. Provider-side payment is verified by the provider implementation.
5. On successful capture, `PaymentSettlementService.settleCaptured()` calls `WalletService.credit()`.
6. A wallet CREDIT ledger entry is created.
7. The customer's reusable wallet balance increases.

### Current financial representation

`WalletService.credit()` increases the customer's `WalletEntity.balance` and creates a `WalletTransactionEntity` with:

- type = `CREDIT`
- status = `POSTED`
- reference type = `ADD_MONEY`
- external reference used for idempotency

### Important finding

The current implementation treats successful gateway payment verification as sufficient to post reusable customer value into the mPay wallet.

For the target compliance architecture, the system must additionally establish **who legally holds/issues the stored value** and whether mPay is acting as issuer, technology/service layer, agent, or another permitted role.

RBI's current PPI directions state that PPIs are stored-value payment instruments and that entities issuing PPIs require the applicable RBI approval/authorisation framework. citeturn0search0

## 4. Flow B — Add Money directly for a recharge

This is materially different from a normal wallet top-up.

### Current sequence

1. Customer creates a payment order with purpose = `RECHARGE`.
2. The selected recharge plan is resolved.
3. The payment amount is calculated from the recharge plan and customer commission.
4. The gateway payment is created.
5. On successful gateway verification, `PaymentSettlementService.settleCaptured()`:
   - credits the user's wallet for the gateway payment amount;
   - immediately calls `RechargeService.recharge()`.
6. `RechargeService.recharge()`:
   - resolves the recharge plan;
   - calculates company/client commission;
   - reserves the wallet debit amount;
   - creates a recharge transaction in `RESERVED`;
   - calls the configured recharge execution provider.
7. Provider result:
   - SUCCESS → reservation is converted to wallet debit;
   - FAILED → reservation is released;
   - other/unknown → transaction remains PENDING.
8. The wallet therefore temporarily receives the gateway-funded value and then spends it for the recharge.

### Critical architecture finding

The gateway settlement and the external recharge side effect occur from the same application flow, while the database transaction cannot atomically roll back an external provider action.

This is already partially mitigated by the recharge transaction state machine, but the target architecture should make the external provider lifecycle explicit:

`PAYMENT_CONFIRMED -> CUSTOMER_VALUE_AVAILABLE -> SERVICE_PAYMENT_RESERVED -> PROVIDER_EXECUTION -> SUCCESS/FAILED/UNKNOWN -> RECONCILIATION`

The service should never assume that a database rollback can undo a provider-side recharge.

## 5. Flow C — Wallet-funded mobile recharge

### Current sequence

1. Customer initiates recharge.
2. `RechargeTransactionWorkflowService.reserve()` creates the recharge transaction with status `RESERVED`.
3. Wallet amount is reserved.
4. External recharge provider is called.
5. On SUCCESS:
   - reservation is finalised;
   - wallet balance is debited;
   - recharge transaction becomes SUCCESS.
6. On FAILED:
   - wallet reservation is released;
   - recharge transaction becomes FAILED.
7. On unknown provider outcome:
   - transaction becomes PENDING;
   - reservation remains held until status resolution.

### Financial effect

Current wallet model is:

- `balance` = gross wallet balance
- `reservedBalance` = value held against unresolved/committed operations
- `availableBalance` = balance minus reservations

This is a sound engineering primitive for preventing double spend, but it is not by itself a regulated customer-money safeguarding/custody structure.

## 6. Flow D — Wallet-funded rental

### Current sequence

1. Customer selects a rental.
2. `RentalPaymentService.pay()` requires method = `WALLET`.
3. A rental payment record is created with status `PENDING`.
4. Wallet amount is reserved.
5. Reservation is immediately finalised into a wallet DEBIT.
6. Rental payment becomes `PAID`.
7. The booking carries a wallet ledger reference.

### Current financial effect

The customer wallet is reduced immediately by the rental gross amount.

No external payment provider is involved in the customer-to-rental payment at this stage.

## 7. Flow E — Rental vendor settlement

### Current implementation

`RentalPayoutService.settleCompletedBooking()` calculates:

- gross rental amount
- platform fee percentage
- platform fee amount
- vendor net amount

The default configured fee is represented as a percentage.

The example logic is:

`vendorNet = gross - platformFee`

The payout is first recorded as `PENDING`, then `settle()` credits the vendor user's **mPay wallet**.

### Critical finding

The current implementation does **not** currently perform a bank/UPI payout to the vendor at settlement time.

Instead:

`Customer wallet -> rental debit -> completed booking -> vendor net credited to vendor mPay wallet`

Therefore, the current implementation is **not yet equivalent** to the intended business statement that mPay pays an independent third-party vendor from a company current account.

This distinction must be resolved before production compliance representation.

## 8. Flow F — Customer withdrawal

### Current sequence

1. Customer submits amount, provider, client request ID and mandatory UPI ID.
2. `WithdrawalService` validates amount, UPI syntax and idempotency key.
3. `WithdrawalPersistenceService.createOrGetPending()` reserves wallet value.
4. Provider payout is initiated.
5. SUCCESS:
   - reserved amount is converted into a wallet WITHDRAW ledger debit;
   - withdrawal becomes SUCCESS.
6. FAILED:
   - wallet reservation is released;
   - withdrawal becomes FAILED.
7. Network/unknown outcome:
   - withdrawal becomes PROCESSING;
   - reservation remains held until provider status/webhook resolution.
8. Withdrawal history stores the UPI ID and provider information.

### Important compliance finding

The code currently implements a technical payout state machine, but the legal basis for customer cash-out depends on the actual regulated wallet/payment structure and the authorised provider's permitted role.

The current architecture must not describe a raw company-current-account payout as the legal source of customer stored value without establishing the applicable regulated structure.

## 9. Flow G — Rental refund

### Current implementation

When a rental booking is cancelled:

1. rental payment is locked;
2. payment must be `PAID`;
3. `RentalPaymentService.refund()` credits the same customer wallet;
4. rental payment becomes `REFUNDED`.

This is an internal wallet restoration, not a gateway refund to the original payment instrument.

### Compliance architecture implication

The final policy must distinguish:

- refund of customer stored value;
- reversal of a service transaction;
- refund back to original external payment source;
- withdrawal/cash-out of eligible unused customer value.

These cannot be treated as the same accounting event.

## 10. Flow H — Rental vendor payout reversal/refund risk

The current vendor settlement credits vendor wallet value after booking completion.

There is currently no equivalent complete external payout reconciliation layer for a bank/UPI vendor settlement because the actual implementation does not yet make that external payout.

Before production:

- vendor payable must be separately represented;
- vendor payout must have its own provider reference/idempotency state machine if external payout is introduced;
- booking cancellation after settlement must have a documented clawback/adjustment policy;
- platform fee and vendor payable must remain separate ledger concepts;
- historical entries must not be overwritten.

## 11. Consolidated current money-flow map

### Current AS-IS

`Customer`
→ PayU/Razorpay payment
→ `PaymentSettlementService`
→ mPay internal wallet CREDIT
→

**Recharge**
→ wallet reservation
→ recharge provider
→ wallet debit on success
→ provider execution

**Rental**
→ wallet reservation
→ wallet debit
→ booking completion
→ vendor net credited to vendor's mPay wallet
→ platform fee remains represented through payout calculation rather than a separate customer-fund movement

**Withdrawal**
→ wallet reservation
→ payout provider
→ wallet debit on success
→ customer's UPI destination

### Target conceptual model

`Customer`
→ authorised payment infrastructure
→ regulated stored-value/payment structure
→ customer available value
→

**Recharge**
→ authorised recharge/payment route
→ recharge provider/biller
→ service completion
→ provider settlement/reconciliation

**Rental**
→ service transaction
→ independent vendor payable
→ contractual mPay/platform fee
→ authorised vendor settlement route

**Withdrawal**
→ eligible customer-value redemption/cash-out
→ authorised payout route
→ verified customer bank/UPI destination

## 12. Accounting/control separation required

The target implementation should maintain separate concepts for:

1. Customer stored value / customer liability
2. Customer available value
3. Customer reserved value
4. Rental gross customer charge
5. Vendor payable
6. Platform/service fee revenue
7. Recharge provider payable
8. Payment gateway fees
9. Refunds/reversals
10. Chargebacks/disputes
11. Withdrawal/cash-out
12. Operational adjustments
13. Reconciliation status

A single wallet balance must not be used as the accounting representation for all of these concepts.

## 13. Evidence gaps requiring business/provider confirmation

The following cannot be safely inferred from source code and therefore require an answer/document from the business or provider.

### Q1 — Stored-value provider/issuer

For production, who will legally issue/operate the reusable customer wallet/stored-value component?

- A regulated PPI issuer/payment partner acting as the wallet provider for mPay; or
- mPay itself after obtaining whatever authorisation is legally required.

**Required before production architecture is finalised.**

### Q2 — Customer payment settlement

For PayU/Razorpay production accounts, where does customer payment settlement legally occur?

We need:

- merchant/entity name on the account;
- settlement bank account holder;
- permitted merchant/business purpose;
- whether wallet loading/reusable stored value is expressly permitted;
- applicable agreement/product confirmation.

### Q3 — Vendor settlement

For rental vendors, should production settlement be:

- direct bank/UPI payout to the vendor through an authorised payout/marketplace settlement arrangement; or
- value credited to a regulated vendor wallet/account structure?

The current code uses the second pattern internally, but this must not be assumed to be the intended production legal arrangement.

### Q4 — Recharge provider role

For the initial PayU/Way2API-based recharge implementation, what contractual role does each provider have?

We need the actual agreement/product documentation showing:

- recharge execution rights;
- settlement model;
- refunds/reversals;
- dispute handling;
- whether mPay is acting as merchant, agent, technology provider, or another role.

### Q5 — BBPS

mPay currently has no established BBPS relationship in the documented business answers.

Therefore the product must not represent itself as a BBPS participant until the actual NBBL/BBPOU relationship and role are established.

RBI's 2024 BBPS Directions identify NBBL as the authorised BBPS system provider and define the participant structure; operating a bill-payment system outside BBPS can raise separate authorisation requirements. citeturn0search3

## 14. Engineering conclusion

The current code already contains several important financial-control primitives:

- idempotency references;
- wallet row locking;
- wallet reservations;
- explicit POSTED ledger entries;
- provider PENDING/PROCESSING states;
- withdrawal reservation and finalisation;
- recharge reservation and finalisation;
- rental refund credit;
- vendor payout calculation.

However, the current implementation is **not yet the target compliance architecture**.

The highest-priority architectural changes are:

1. Separate customer stored-value representation from mPay revenue.
2. Introduce an explicit regulated-wallet/payment-provider boundary.
3. Make payment confirmation, wallet value issuance, service execution and provider settlement separately traceable.
4. Replace internal vendor-wallet credit as the assumed production vendor settlement mechanism with an explicit vendor-payable + authorised settlement model, subject to the business/provider answer.
5. Build reconciliation around every external provider effect.
6. Define refund/reversal/cash-out semantics separately.
7. Preserve complete immutable transaction history.

No application-code implementation should be started for these points until Q1–Q4 are answered or the legal/provider architecture is selected.

## 15. Regulatory reference

- RBI Master Directions on Prepaid Payment Instruments, updated December 27, 2024: https://www.rbi.org.in/Scripts/NotificationUser.aspx/NotificationUser.aspx?Id=12156
- RBI Bharat Bill Payment System Directions, 2024: https://www.rbi.org.in/scripts/NotificationUser.aspx?Id=12616

These references support the regulatory classification questions; they do not by themselves determine mPay's final legal status.

**Document status:** AS-IS engineering map completed.  
**Next stage:** obtain the business/provider answers above, then design the target financial domain/state model before changing application code.
