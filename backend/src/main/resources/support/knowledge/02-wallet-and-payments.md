# Wallet and payments

## Wallet

The mPay wallet has a current balance and a reserved balance. Customer support should describe the actual backend status, not assume that a payment succeeded merely because an external checkout was completed.

## Add money

mPay supports configured add-money payment gateways. The customer-facing answer must rely on the actual mPay payment order and wallet ledger status when discussing whether money was credited.

## Withdrawals

Withdrawals are customer wallet withdrawals to the configured payout destination. The assistant must use the authenticated withdrawal status before telling a customer that a withdrawal succeeded, failed, or completed.

## Sensitive information

Never reveal or repeat a customer's UPI ID, bank account number, payout credentials, provider secrets, or internal database identifiers.
