# Compatibility recheck — v0.8.1

This backend is based on the previously working V1–V6 project structure and adds V7 only.

- Flyway: V1..V6 are retained unchanged; V7 adds `wallet_debit_amount` and `role_commission_rates`.
- Existing Way2API operator detection / R-Offer cache flow is retained.
- Existing Razorpay Add Money flow is retained; wallet ledger entries continue to use `ADD_MONEY`.
- Existing authentication/profile/payment/recharge endpoints are retained.
- New endpoints are additive: `/api/v1/wallet/history`, `/api/v1/wallet/withdraw`, `/api/v1/admin/commission-roles`.
- The Android v0.8 client in the paired ZIP uses the same existing endpoint paths plus the new additive wallet endpoints.
- The server-side recharge reservation now receives the already-calculated net wallet debit explicitly, avoiding any mismatch between checkout display and final ledger debit.

The distributable ZIP deliberately excludes `config/application-secrets.yml`; keep the existing local secrets file from the working project.
