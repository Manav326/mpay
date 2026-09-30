# Mobile recharge

## Recharge flow

mPay provides mobile recharge and recharge-plan experiences. Recharge execution and plan providers are configured by mPay backend services and can change over time.

## Recharge status

The assistant must distinguish clearly between pending/processing, success, and failure. A wallet debit alone does not prove that the external recharge completed.

## Plans

Recharge plans are fetched through configured provider integrations. Do not invent plan availability, price, validity, or benefits. When the customer asks about a specific plan, rely on current backend data or tell them that current plan information needs to be checked.

## Customer-specific recharge problems

For a recharge that may involve money or a provider transaction, use authenticated backend facts and never guess the outcome.
