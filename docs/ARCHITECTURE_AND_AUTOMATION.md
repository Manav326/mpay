# mPay Architecture and Development Automation

Last reviewed after Android APK CI automation merge to main.

## 1. System at a glance

mPay is a unified repository containing three application layers plus local infrastructure:

```text
                         ┌──────────────────────────┐
                         │       Android App        │
                         │ Kotlin + Jetpack Compose │
                         │ Retrofit + ViewModels    │
                         └────────────┬─────────────┘
                                      │ HTTPS/HTTP
                                      ▼
┌──────────────────┐        ┌──────────────────────────┐
│   Admin Web      │  HTTP  │     Spring Boot API      │
│ Next.js          ├───────►│ Kotlin                    │
│ Role-aware UI    │        │ auth / wallet / recharge │
└──────────────────┘        │ admin / provider layer  │
                            └───────┬─────────┬────────┘
                                    │         │
                              ┌─────▼───┐ ┌──▼──────┐
                              │Postgres │ │ Redis   │
                              │data     │ │cache    │
                              └─────────┘ └─────────┘

        External integrations behind backend provider abstractions:
        Way2API / PayU UAT / Razorpay / Twilio Verify
```

The Android application is not part of the Docker runtime stack. PostgreSQL, Redis, the backend, Admin Web, and optional Caddy run through Docker Compose.

## 2. Repository structure

```text
mpay/
├── backend/                 Spring Boot / Kotlin API
│   ├── src/main/.../api     REST controllers + DTOs
│   ├── .../domain           JPA/domain entities
│   ├── .../repository       persistence repositories
│   ├── .../service          business workflows
│   ├── .../provider         provider abstractions + implementations
│   ├── .../security         JWT, CORS, security configuration
│   └── config/              local runtime secrets/config mount
│
├── admin-web/               Next.js Admin portal
│   ├── app/                  portal UI
│   └── lib/                  API client, types, demo data
│
├── android/                 Kotlin Android client
│   └── app/src/main/java/
│       └── com/recharge/client/
│           ├── core/         network, security, models, cache, ViewModels, UI helpers
│           └── features/     auth, home, recharge, wallet, profile
│
├── infra/caddy/              production reverse proxy
├── scripts/                  Windows development automation
├── docker-compose.yml        local runtime + image definitions
├── .github/workflows/        GitHub Actions CI
└── .env / .env.example       development configuration
```

## 3. Backend architecture

The backend is the authoritative business and security layer. Android and Admin Web should not directly call external recharge/payment providers.

### Request flow

```text
Client
  │
  ▼
REST Controller
  │
  ▼
Service / Workflow
  │
  ├── Domain + Repository ───► PostgreSQL
  │
  ├── Cache service/provider ─► Redis
  │
  └── Provider abstraction ───► external provider
```

Important backend areas currently present include:

- `api`: HTTP controllers, API models, provider diagnostics, exception handling.
- `service`: authentication, profile, wallet, payments, recharge, recharge history, commission, admin, password reset, OTP, profile-image storage, and transaction workflow logic.
- `provider`: stable interfaces for plans/recharge execution plus Way2API and PayU implementations/mocks.
- `repository`: persistence access.
- `domain`: persisted business entities such as users/wallet-related records, recharge/payment records, and admin/vendor data.
- `security`: JWT authentication, authorization, and CORS.

### Recharge flow

```text
Android
  │ POST /api/v1/recharge/operator
  ▼
Backend detects operator/circle
  │
  │ GET /api/v1/recharge/plans
  ▼
Backend PlanCatalogProvider
  │
  ├── mock plan provider when configured
  └── PayU plan provider when enabled/configured
  │
  ▼
Android selects plan
  │ POST /api/v1/recharge
  ▼
RechargeService / transaction workflow
  │
  ├── validates wallet/balance
  ├── creates authoritative transaction/ledger state
  ├── calls RechargeExecutionProvider
  └── returns SUCCESS / PENDING / FAILED
  │
  ▼
Android polls /api/v1/recharge/{transactionId} when PENDING
```

Client request IDs are part of the recharge contract so retries can be correlated/idempotently handled by the backend workflow.

### Security model

- Normal CLIENT accounts use the normal login flow.
- ADMIN/MANAGER accounts use portal-specific login endpoints.
- The backend is the source of truth for Admin Web authorization and permissions.
- Android receives authentication tokens and calls only application APIs.
- External provider credentials are backend-side secrets and are not sent to Android.

## 4. Admin Web architecture

The Admin Web is a Next.js portal using a typed API client in `admin-web/lib/api.ts`.

```text
Browser
  │
  ├── portal login
  ▼
/api/v1/auth/portal-roles
/api/v1/auth/admin-login
/api/v1/auth/manager-login
  │
  ▼
JWT + permissions
  │
  ├── dashboard
  ├── user list
  ├── read-only user detail
  ├── recharge history
  ├── wallet history
  └── vendor management when permitted
```

The UI reflects permissions returned by the backend; it is not the security boundary.

## 5. Android architecture

The Android client uses Kotlin, Jetpack Compose, Android ViewModels, Retrofit/OkHttp, DataStore, and Razorpay Checkout.

```text
MainActivity / Compose navigation
          │
          ▼
Feature screens
          │
          ▼
ViewModels (UI state + workflows)
          │
          ▼
ClientRepository
          │
          ▼
Retrofit API services
          │
          ▼
Spring Boot backend
```

Current top-level Android areas include:

- Auth: login, registration, forgot/reset password.
- Home: user/balance summary and navigation.
- Recharge: mobile validation, operator detection, plan loading, recharge execution, pending polling, and history.
- Wallet: balance, add money through Razorpay, wallet history, and withdrawal.
- Profile: profile data and profile image.
- Core: network configuration, API models/services, token storage, cache, ViewModels, themes and shared UI utilities.

The Android app's default physical-device API URL is the local development backend at `http://192.168.31.47:8080/`, overridable with the Gradle property/environment variable `MPAY_API_BASE_URL`.

## 6. Docker runtime architecture

`docker-compose.yml` defines:

- `postgres`: PostgreSQL 17.
- `redis`: Redis 7 Alpine.
- `pgadmin`: pgAdmin 9 on host port 5050.
- `backend`: Spring Boot image on port 8080.
- `admin-web`: Next.js image on port 3000.
- `caddy`: optional production profile on ports 80/443.

Named persistent volumes are intentionally reused. In particular, the existing PostgreSQL development volume must be preserved. Routine feature switching must not use `docker compose down -v`.

## 7. Two local application environments

### Stable local environment

`mpay` uses locally built application images during normal development.

### GitHub feature-test environment

`mpay-github` runs the same infrastructure with backend/Admin Web images pulled from GHCR.

Both environments use host ports 3000, 8080 and 5050, so only one application stack should be running at a time.

## 8. GitHub Actions architecture

The repository uses one shared workflow:

` .github/workflows/docker-compose.yml `

It has two jobs.

### Backend/Admin Web job

For pushes to `feature/**`:

1. Validate Compose configuration.
2. Build backend/Admin Web images in GitHub.
3. Push feature images to GHCR.
4. Publish the branch tag `feature-<safe-branch>-latest`.
5. Publish immutable `sha-<commit>` tags.

For pull requests to `main`, the images are built for validation but are not published.

For pushes to `main`, the images are published as `latest` plus `sha-<commit>`.

### Android APK job

The Android job intentionally runs only for push events to:

- `main`
- `feature/android-*`
- `feature/android/**`

It uses:

- JDK 17
- Android SDK 36
- Gradle wrapper from the repository

It builds:

`android/app/build/outputs/apk/debug/app-debug.apk`

and uploads that APK as:

`mpay-android-debug-apk`

with a 14-day artifact retention.

Important: a successful pull-request run does not necessarily contain the Android artifact because the Android job is restricted to push events.

## 9. Exact feature development workflow

Use this workflow for future changes, especially new Android features:

```text
main
  │
  └──► feature/<feature-name>
          │
          ├── implement feature
          ├── git push
          │
          └── GitHub Actions
                ├── Docker images (all feature branches)
                └── Android APK (Android feature branches)
          │
          ▼
     local verification
          │
          ▼
     Pull Request → main
          │
          ▼
        merge
```

Recommended Android feature branch examples:

```text
feature/android-wallet-ui
feature/android-recharge-retry
feature/android-profile-v2
```

## 10. Windows commands: daily development

### Start the stable local stack

```powershell
cd D:\mPay-agent-ready\mPay-github\mpay
docker compose -p mpay up -d
```

### Stop the stable stack without deleting data

```powershell
docker compose -p mpay stop
```

### Switch to an Android feature branch

```powershell
git fetch origin
git switch feature/android-<feature-name>
git pull origin feature/android-<feature-name>
```

### Start GitHub-built backend/Admin Web for the current branch

```powershell
.\scripts\mpay-github-update.ps1
```

### Check containers

```powershell
docker compose -p mpay-github ps
```

### View logs

```powershell
.\scripts\logs.ps1
```

### Stop the GitHub test stack without deleting data

```powershell
docker compose -p mpay-github stop
```

### Return to the stable stack

```powershell
docker compose -p mpay up -d
```

## 11. Android APK automation commands

### One-time prerequisites

GitHub CLI:

```powershell
winget install --id GitHub.cli
gh auth login
gh auth status
```

ADB verification:

```powershell
adb --version
adb devices
```

### The main command to remember

From the repository root, on the exact commit you want to test:

```powershell
.\scripts\android-fetch-latest-apk.ps1
```

The helper:

1. reads the current Git branch and commit SHA;
2. checks whether a cached `app-debug.apk` belongs to that exact commit;
3. reuses the cached APK without contacting GitHub when it matches;
4. otherwise finds a successful push-triggered `Docker Compose CI` run for the exact commit;
5. downloads `mpay-android-debug-apk`;
6. places it in the normal Gradle debug output directory;
7. asks whether to install;
8. automatically selects a single connected ADB device;
9. asks you to choose when multiple devices exist;
10. handles the CI-vs-local signing-key mismatch by offering uninstall + reinstall.

### Force a fresh download

```powershell
.\scripts\android-fetch-latest-apk.ps1 -ForceDownload
```

Use this only when you intentionally want to replace the cached APK.

### Current APK output location

```text
android\app\build\outputs\apk\debug\app-debug.apk
```

The helper stores a local commit marker beside it:

```text
android\app\build\outputs\apk\debug\app-debug.apk.commit
```

Both are ignored by Git.

## 12. Android wireless debugging

The installed ADB can use Android wireless debugging. The repository helper does not require a TCP/IP address; it uses the exact serial returned by `adb devices`, including mDNS serials such as:

```text
adb-<pairing-id>._adb-tls-connect._tcp    device
```

That is why the installation helper can select the wireless device exactly as Android Studio does.

## 13. CI artifact and image rules

Do not commit generated APK files.

Do not commit Docker image binaries.

Do not commit application secrets.

Use GHCR for backend/Admin Web images and GitHub Actions artifacts for the debug APK.

Feature testing is commit-oriented: the local helper requests the artifact corresponding to the exact local Git commit, which prevents accidentally installing/testing an APK from another revision.

## 14. Secrets and data safety

Development configuration may use the local `.env` and locally mounted backend secret configuration.

Production secrets must not be committed, baked into Docker images, placed in Android source, or exposed in the public Admin Web bundle.

Never delete the development PostgreSQL/Redis volumes to solve an application problem. Fix the application/container configuration instead.

## 15. Before implementing the next Android feature

Use this sequence:

```powershell
git fetch origin
git switch main
git pull origin main
git switch -c feature/android-<new-feature>

# implement and test
git add .
git commit -m "feat(android): <description>"
git push -u origin feature/android-<new-feature>

# wait for/inspect CI from GitHub
# pull the GitHub-built APK with:
.\scripts\android-fetch-latest-apk.ps1

# test on the connected Android device
# then create/merge the PR after verification
```

## 16. Current verified baseline

PR #4 (`chore(android): add automated APK build and retrieval`) has been merged into `main`.

Merge commit:

`de05b6c4828fe4045f84ce9191eff9e675ab10f0`

The Android CI build, artifact retrieval, local cache reuse, wireless ADB device selection, and APK installation flow were all verified on the development machine before the merge.

## 17. Primary automation files

```text
.github/workflows/docker-compose.yml
scripts/mpay-github-update.ps1
scripts/android-fetch-latest-apk.ps1
scripts/android-build-release.ps1
docker-compose.yml
android/README.md
```

Treat these files as the first places to inspect when changing CI/CD, feature-image testing, or Android APK delivery.