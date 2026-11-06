# mPay local production deployment through the existing Cloudflare Tunnel

This is the selected mPay hosting path: Docker services remain on the Windows host, while the existing Cloudflare Tunnel provides public HTTPS ingress. It does not create a second tunnel, Caddy listener, public application port, or new app data volume shared with development.

## 1. Android download experience

The public homepage shows the Android download card immediately below the hero and before the feature section. It reads the latest non-draft GitHub release through the same-origin metadata route and downloads the signed APK through a same-origin streaming route. The browser can therefore read the response size and report real byte progress instead of showing an invented timer.

After a successful main-branch Android workflow, the repository publishes:
- `mpay-android.apk` as the stable latest-release asset;
- `mpay-android.apk.sha256` as its checksum;
- a versioned release tag tied to the exact commit.

The stable asset URL is `https://github.com/Manav326/mpay/releases/latest/download/mpay-android.apk`. The website does not link to an expiring Actions artifact. Until the first signed release exists, the card deliberately shows that the app is being prepared.

### Required GitHub Actions secrets

Before the first main-branch release, configure these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `MAPS_API_KEY` (when Maps functionality is being released)
- `MPAY_FIREBASE_API_KEY`
- `MPAY_FIREBASE_APP_ID`
- `MPAY_FIREBASE_PROJECT_ID`
- `MPAY_FIREBASE_SENDER_ID`

The workflow already defaults `MPAY_API_BASE_URL` to `https://api.mpay.thinkwithsujeet.in`; a repository variable of the same name may override it. The signing key must be the same key used for any existing distributed mPay installation. A different signing key prevents an in-place Android update and may require uninstalling the previous app (which deletes its local app data).

The Android package ID is `com.client.mpay`. The Windows APK helper now uses that actual application ID.

## 2. Prepare production-only backend configuration

From the repository root, create the local runtime environment file:

```powershell
Copy-Item .env.cloudflare.example .env.cloudflare
```

Edit `.env.cloudflare` and replace every `REPLACE_WITH` value with the approved live configuration. Keep the completed file out of Git; it is ignored.

Create `backend/config/cloudflare-production/application-secrets.yml` from the repository's example shape, but populate it with production credentials only. This separate directory is mounted only into the Cloudflare production backend; it leaves the development `backend/config/application-secrets.yml` untouched and is ignored by Git. Do not copy the example's mock/demo values into production. The deploy preflight rejects obvious placeholders, mock providers, demo OTP, sandbox/test settings, a short JWT secret, and missing key provider settings.

At minimum, validate these provider values with their owners before enabling transactions:

- a unique JWT signing secret (at least 32 characters);
- Way2API credentials for the configured customer OTP flow;
- Razorpay key ID/secret for the configured wallet-payment route;
- PayU client ID/secret, approved agent ID, merchant key/salt, and operator/circle mappings;
- the PayU live authentication, NBC, verification and callback URLs supplied by PayU;
- separate live PayU payout authentication/API URLs when PayU payout is the selected payout provider.

The PayU configuration in `application.yml` retains safe UAT defaults for development. The Cloudflare production overlay requires explicit live values through the `MPAY_PAYU_*` variables and refuses UAT/test/placeholder URLs. Do not guess the live endpoints or switch the flag until the live account has been approved and tested.

Mock payment and withdrawal providers are forced off in the production Compose overlay. The selected payment/withdrawal provider lists must not include `mock`. Payout integrations default disabled; withdrawals are not production-ready until at least one payout provider is configured with live credentials, webhook authentication and verified reconciliation. Do not enable payout simply to make the preflight pass.

## 3. Preserve existing mPay data before the first cutover

The Cloudflare production Compose project owns isolated named volumes:

- `mpay_cloudflare_production_postgres_data`
- `mpay_cloudflare_production_redis`
- `mpay_cloudflare_production_profile_images`
- `mpay_cloudflare_production_rental_images`

They do not reuse the development or feature-test database/media volumes. The deployment script never runs `down -v`, `docker system prune`, or global image/container cleanup.

Back up whichever existing mPay Compose project contains the data to retain. The project name must match the active stack (the examples use `mpay`; use `mpay-github` only when that is the active data source):

```powershell
.\scripts\mpay-db-backup.ps1 -ComposeProject mpay
```

The backup contains PostgreSQL plus profile and rental media. It intentionally excludes Redis cache and backend secret files. Keep a separate secure backup of `backend/config/cloudflare-production/application-secrets.yml`; never put secrets in the database backup folder shared with others.

Record the resulting backup directory. On first production cutover, restore it into the isolated production volumes:

```powershell
.\scripts\mpay-cloudflare-deploy.ps1 -RestoreBackup ".\backups\mpay-YYYYMMDD-HHmmss"
```

For a genuinely new mPay installation with no existing user, wallet, transaction or media data, use `-AllowFreshInstall` instead. This is an explicit choice; the script will not silently initialize an empty production database. A restore into an existing production database also requires `-ConfirmRestoreOverwrite`.

## 4. Verify the existing Cloudflare network

The deployment script checks that the Docker network `cloudflare-edge` exists and that the existing connector container `qrtoprint-local-cloudflared-1` is attached to it. It does not create, restart, reconfigure or detach the connector. If the connector has a different name, pass `-CloudflareConnectorContainer <actual-container-name>`.

Configure these two public hostnames in the existing tunnel's Public Hostnames / ingress settings:

| Public hostname | Service target inside `cloudflare-edge` |
| --- | --- |
| `mpay.thinkwithsujeet.in` | `http://mpay-admin-web:3000` |
| `api.mpay.thinkwithsujeet.in` | `http://mpay-api:8080` |

No Caddy profile is started. Docker publishes only loopback diagnostic ports by default (`127.0.0.1:13000` for Admin Web and `127.0.0.1:18080` for the API). Public requests enter through the existing tunnel network aliases.

## 5. Deploy and verify

The deployment must be run from a clean, synchronized `main` checkout after the feature PR is merged and the main-branch workflows have passed:

```powershell
git checkout main
git pull --ff-only origin main
.\scripts\mpay-cloudflare-deploy.ps1
```

The script verifies the exact main commit, production environment values, backend secret file, existing Cloudflare connector membership, immutable GHCR image tags, and database state. It creates an automatic PostgreSQL/media backup before updating an already-running production project. It then pulls the exact backend `sha-<commit>` and Admin Web `azure-sha-<commit>` images, starts services without rebuilding locally, waits for `/actuator/health`, checks the local homepage, and checks the public routes.

If the tunnel hostnames have not yet been configured, use `-SkipPublicSmokeTest` only to perform the local container smoke test; configure the two public hostnames and rerun the full script before considering the deployment verified.

## 6. Voice calling is a separate network requirement

Cloudflare Tunnel's HTTP/HTTPS ingress can carry the Admin Web, REST API and WebSocket signaling, but it does not relay WebRTC audio media over UDP. The app's voice-calling feature therefore needs a separately reachable TURN service (UDP/TCP 3478 plus its configured relay port range), TURN username/credential, and the matching `MPAY_CALL_ICE_SERVERS` URLs. Firebase Admin service-account credentials and the Android Firebase app secrets are also required for incoming-call notifications.

Until an external TURN path and FCM delivery are tested from real networks, do not grant `CALL_CUSTOMER` to production staff roles or represent voice calling as ready. Do not try to route TURN UDP through the regular Cloudflare HTTP tunnel.

## 7. Final business validation before public launch

Production code and infrastructure cannot substitute for merchant account approvals or live credentials. Before allowing customers to transact, run controlled end-to-end checks for registration/OTP and recovery, wallet top-up with signed provider callbacks, recharge success/pending/failure and reconciliation, payout success/failure/webhook authentication, wallet ledger consistency, admin role restrictions, and a signed Android update on a real device.

The deploy script intentionally blocks mock/UAT/placeholder payment configuration. The checked-in example values are not live credentials. Payout and voice-calling readiness remain provider/network-dependent release gates.
