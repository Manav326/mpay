# Recharge Android Client — Recharge Flow v0.4.0

This project is the current Android client baseline with the working authentication, JWT refresh, wallet, Razorpay wallet funding, and mobile recharge flow.

## Recharge flow

1. Enter the recharge target mobile number.
2. Detect operator and circle through the backend/Way2API integration.
3. Load current personalized R-Offers through the backend.
4. Select an offer.
5. Refresh and validate the authenticated user's wallet balance.
6. Confirm the recharge.
7. Submit /api/v1/recharge with a unique client request ID.
8. Show SUCCESS / PENDING / FAILED states.
9. Poll /api/v1/recharge/{transactionId} when the backend reports PENDING.
10. Refresh the wallet after the provider result is finalized.

The Android app does not contain any Way2API or recharge-provider credentials. Provider selection remains a backend concern so the execution provider can be replaced without changing the Android API contract.

## Backend endpoints used by this version

- POST /api/v1/auth/login
- POST /api/v1/auth/register
- POST /api/v1/auth/refresh
- GET /api/v1/me
- GET /api/v1/wallet
- POST /api/v1/payments/orders
- POST /api/v1/payments/verify
- POST /api/v1/recharge/operator
- GET /api/v1/recharge/plans
- POST /api/v1/recharge
- GET /api/v1/recharge/{transactionId}

## Notes

- Recharge state is cleared when leaving the Recharge screen.
- Bottom navigation uses top-level destination state restoration.
- Wallet funding remains Razorpay-based and is independent from recharge execution.
- Wallet debit is authoritative on the backend; the Android app never deducts the wallet locally.

## APK build and public Android distribution

The `Android APK CI` workflow builds an optimized, signed Release APK and Play Store AAB with JDK 17 and Android SDK 37. For pull requests, it validates the build and uploads branch-specific artifacts. For pushes to `main`, a separate least-privilege publish job downloads the exact release APK artifact and publishes it as a versioned GitHub Release with a SHA-256 checksum.

The public website uses the stable asset URL:

```text
https://github.com/Manav326/mpay/releases/latest/download/mpay-android.apk
```

The homepage's Android card reads version and APK size from the latest public release, streams the APK through the same-origin web route, and displays actual download byte progress from 0–100%. Until the first signed main-branch release is published, the card reports that the app is being prepared.

### Required release secrets

Configure these repository Actions secrets before the first public release:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `MAPS_API_KEY` when Maps functionality is enabled
- `MPAY_FIREBASE_API_KEY`
- `MPAY_FIREBASE_APP_ID`
- `MPAY_FIREBASE_PROJECT_ID`
- `MPAY_FIREBASE_SENDER_ID`

Keep the same signing key for every public update. A different signing key prevents an in-place update and can require uninstalling the existing app, which deletes local app data. Production builds derive the API origin from `MPAY_PUBLIC_ROOT_URL` (for example, `https://quantaedge.in` → `https://api-mpay.quantaedge.in/`). Local builds without the root setting may still use `MPAY_API_BASE_URL` or `-PmpayApiBaseUrl`.

### Download or reuse an APK from Windows

On the Windows development machine, install and authenticate GitHub CLI when needed:

```powershell
gh auth login
```

From the repository root, on the commit whose Android CI artifact you want to test:

```powershell
.\scripts\android-fetch-latest-apk.ps1
```

The helper:
1. Reuses a cached release APK only when its commit marker matches the current commit.
2. Otherwise retrieves the successful `Android APK CI` release artifact for the exact commit, or for a later commit only when the helper verifies that no Android-related file changed.
3. Stores the file at `android/app/build/outputs/apk/release/app-release.apk` and records its commit marker beside it.
4. Optionally offers to install it with ADB on a connected device.

Use `-ForceDownload` to replace the cached file:

```powershell
.\scripts\android-fetch-latest-apk.ps1 -ForceDownload
```

The actual Android application ID is `com.client.mpay`. The Gradle namespace `com.recharge.client` is not the installed package name.

For local development, backend URL configuration is supplied through the `MPAY_API_BASE_URL` environment variable or `-PmpayApiBaseUrl` Gradle property. Production/release builds must use the public HTTPS API origin.
