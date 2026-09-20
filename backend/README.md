# Recharge Backend - PayU UAT Foundation

This build adds the first PayU UAT integration phase without changing the existing auth, wallet, Razorpay, or Way2API operator-detection flow.

## PayU UAT configuration

Create or update the external file:

`config/application-secrets.yml`

using the existing production-shaped structure:

```yaml
app:
  payu:
    client-id: YOUR_PAYU_UAT_CLIENT_ID
    client-secret: YOUR_PAYU_UAT_CLIENT_SECRET
```

The example file is `config/application-secrets.yml.example`.

PayU UAT endpoints configured by default:

- OAuth token: `https://uat-accounts.payu.in/oauth/token`
- NBC sandbox: `https://bbps-sb.payu.in`
- Scope for the foundation phase: `read_plans`

The backend obtains and caches the OAuth access token in memory. The token is refreshed automatically when it is close to expiry. The access token and client secret are never returned to the Android client or diagnostic response.

## UAT health check

After the backend starts and you are authenticated, call:

`GET /api/v1/provider/payu/health`

A configured and working PayU UAT connection returns `authenticated: true`.

This endpoint is intended for development/UAT diagnostics. It requires authentication and never exposes PayU credentials or the bearer token.

## Next phase

Once the PayU UAT credentials work, the next phase will implement the exact PayU plan API contract, including operator/circle mapping, followed by the UAT recharge transaction, status reconciliation, and callbacks.


## Phase 2 - PayU plan contract (development/UAT-safe)

The backend now has a dedicated `PlanCatalogProvider` abstraction and a `PayUPlanProvider`.

While the newly created PayU credentials are not yet verified, `app.payu.plan-mock-enabled` is `true`.
The recharge plans endpoint therefore returns deterministic mock plans so the Android/backend contract can be tested without pretending that PayU authentication succeeded.

### Test plans endpoint

After login, call:

`GET /api/v1/recharge/plans?mobile=7070107483&operator=AIRTEL&circle=Bihar%20and%20Jharkhand`

The response is our stable application `RechargePlanDto` model, not PayU's raw response.

When PayU UAT credentials are verified:

1. set `plan-mock-enabled: false`
2. set `agent-id`
3. configure `operator-code-mappings` and `circle-code-mappings` with the exact PayU IDs
4. restart the backend

The provider will then call PayU's documented `getRechargePlans` API with a Bearer access token and normalize PayU's `payload -> circleWisePlanLists -> plansInfo` response into our internal model.
