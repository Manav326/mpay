# Recharge backend: offer cache + recharge history + commission summary

## New behavior

- `GET /api/v1/recharge/plans` fetches Way2API R-Offers once per target mobile/operator/circle cache key within the configured TTL (default 10 minutes).
- `POST /api/v1/recharge` never calls Way2API during checkout. It resolves the selected offer from the server-side cache and fails fast with a refresh message when the offer has expired.
- Recharge transactions retain plan description/validity, Way2API order id, provider reference, wallet ledger reference and completion time.
- Successful recharge commission defaults to 1% for the client and is configurable with `app.commission.client-percent`.
- Company commission remains configurable with `app.commission.company-percent`.
- Commission summaries count only successful recharges and use `completedAt` (falling back to `createdAt` for older rows), in Asia/Kolkata time.

## New endpoints

`GET /api/v1/recharge/history?page=0&size=20`

`GET /api/v1/recharge/commission-summary`

`GET /api/v1/wallet` now also returns `availableBalance` and `reservedBalance`; `balance` remains backward compatible.

## Configuration

```yaml
app:
  commission:
    company-percent: 1.0
    client-percent: 1.0
  recharge:
    plan-provider: way2api
    execution-provider: mock
    mock-execution-status: SUCCESS
    offer-cache-ttl-seconds: 600
```

The Way2API credential remains backend-only in `config/application-secrets.yml`.

## Database

A new Flyway migration `V4__recharge_history_and_offer_cache.sql` adds:

- recharge history metadata and completion timestamp
- wallet ledger reference metadata
- `recharge_offer_cache` table and indexes

Do not rename or delete the already-applied V1, V2 or V3 migrations.


## Cache policy update

Way2API R-Offers are personalized to a prepaid mobile number, so a single operator-wide cache would be incorrect. The implementation therefore caches per mobile number + operator + circle for 24 hours. A successful `/recharge/plans` fetch populates/refreshes that cache. A failed recharge invalidates the cached offers for that target, so the next `/recharge/plans` request performs a fresh Way2API lookup. Checkout itself never calls Way2API.
