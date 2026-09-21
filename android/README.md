# Recharge Android Client — Recharge Flow v0.4.0

This project is the current Android client baseline with the working authentication, JWT refresh, wallet, Razorpay wallet funding, and mobile recharge flow.

## Recharge flow

1. Enter the recharge target mobile number.
2. Detect operator and circle through the backend/Way2API integration.
3. Load current personalized R-Offers through the backend.
4. Select an offer.
5. Refresh and validate the authenticated user's wallet balance.
6. Confirm the recharge.
7. Submit `/api/v1/recharge` with a unique client request ID.
8. Show SUCCESS / PENDING / FAILED states.
9. Poll `/api/v1/recharge/{transactionId}` when the backend reports PENDING.
10. Refresh the wallet after the provider result is finalized.

The Android app does not contain any Way2API or recharge-provider credentials. Provider selection remains a backend concern so the execution provider can be replaced without changing the Android API contract.

## Backend endpoints used by this version

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/me`
- `GET /api/v1/wallet`
- `POST /api/v1/payments/orders`
- `POST /api/v1/payments/verify`
- `POST /api/v1/recharge/operator`
- `GET /api/v1/recharge/plans`
- `POST /api/v1/recharge`
- `GET /api/v1/recharge/{transactionId}`

## Notes

- Recharge state is cleared when leaving the Recharge screen.
- Bottom navigation uses top-level destination state restoration.
- Wallet funding remains Razorpay-based and is independent from recharge execution.
- Wallet debit is authoritative on the backend; the Android app never deducts the wallet locally.


## APK build and test automation

The existing `Docker Compose CI` workflow also builds the Android debug APK for pushes to `main` and Android feature branches (`feature/android-*` and `feature/android/**`). This keeps backend, Admin Web, and Android validation in the same CI pipeline.

For an Android build:
- builds a debug APK with JDK 17 and Android SDK 36,
- verifies `android/app/build/outputs/apk/debug/app-debug.apk`,
- uploads it as the `mpay-android-debug-apk` GitHub Actions artifact,
- keeps the artifact for 14 days.

The repository does not commit the generated APK because `android/app/build/` is ignored by Git.

### Download the APK into the Android Studio build output location

On the Windows development machine, install and authenticate the GitHub CLI once:

```powershell
gh auth login
```

Then from the repository root, on the same commit whose Android build you want to test:

```powershell
.\scripts\android-fetch-latest-apk.ps1
```

The script checks GitHub Actions for a successful Android APK CI run for the exact current commit and copies the APK to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

That location is ignored by Git and is the normal Gradle debug APK output location. Android Studio can install the APK through its device/deployment tooling, or the same file can be installed with ADB when a device is connected.

For local development, backend URL configuration is supplied through the `MPAY_API_BASE_URL` Gradle property/environment variable. The shared CI workflow currently builds the test APK with `http://192.168.31.47:8080/`, matching the local development backend used on the test PC.
