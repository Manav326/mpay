# mPay Unified Development + Docker Deployment

This repository groups the current mPay backend, Admin Web, and Android client under one parent folder.

## Architecture and automation

The complete system architecture, GitHub Actions flow, Docker/GHCR feature-test workflow, Android APK automation, ADB installation flow, exact reusable PowerShell commands, branch workflow, and current verified baseline are documented here:

[docs/ARCHITECTURE_AND_AUTOMATION.md](docs/ARCHITECTURE_AND_AUTOMATION.md)

## Runtime model

- PostgreSQL: Docker service `postgres`
- Redis: Docker service `redis`
- Spring Boot backend: Docker service `backend`
- Next.js Admin Web: Docker service `admin-web`
- Android: standalone Android Studio project; it is not part of the runtime Docker stack.

## Stable local stack and GitHub feature-test stack

The Windows development machine keeps two independent Compose projects:

```text
mpay
├── postgres
├── redis
├── pgadmin
├── backend
└── admin-web

mpay-github
├── postgres
├── redis
├── pgadmin
├── backend
└── admin-web
```

Both use the same host ports:

- Admin Web: `3000`
- Backend API: `8080`
- pgAdmin: `5050`

Only one stack should be running at a time.

The application images are different between the two stacks when using the GitHub workflow. The GitHub test stack pulls backend and Admin Web images from GHCR; it does not build them locally.

The development PostgreSQL and Redis data volumes are intentionally reused. Do not run `docker compose down -v`.

## Feature branch workflow

All new development should use a feature branch:

```text
main
  ↓
feature/<feature-name>
  ↓
implement + push
  ↓
GitHub Actions builds feature images
  ↓
pull images into mpay-github
  ↓
local testing
  ↓
final approval
  ↓
Pull Request → main
```

Examples:

```text
feature/recharge-history
feature/operator-detection
feature/admin-reports
feature/android-<feature-name>
```

The shared workflow also builds the Android debug APK for pushes to `main` and Android-specific feature branches.

## Quick Android command

From the repository root, on the exact commit you want to test:

```powershell
.\scripts\android-fetch-latest-apk.ps1
```

The helper reuses a cached APK when it belongs to the current commit and downloads a new GitHub Actions artifact only when necessary.

## Local URLs

From the development PC:

- Admin Web: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- pgAdmin: `http://localhost:5050`

From another device on the same Wi-Fi:

- Admin Web: `http://192.168.31.47:3000`
- Backend API: `http://192.168.31.47:8080`

## Android API

Default local physical-device URL:

```text
http://192.168.31.47:8080/
```

Release build against a production API:

```powershell
cd android
./gradlew.bat :app:assembleRelease -PmpayApiBaseUrl=https://admin.example.com/
```

The same URL can be supplied through the `MPAY_API_BASE_URL` environment variable.

## Production architecture

With DNS pointing `MPAY_PUBLIC_DOMAIN` to the server, enable the production Caddy profile:

```powershell
docker compose --profile production up -d --build
```

Set production environment values separately before deployment.

Caddy terminates HTTPS and routes:

- `/api/*` -> Spring Boot `backend:8080`
- everything else -> Next.js `admin-web:3000`

## Database and persistent data

The development PostgreSQL volume is external and intentionally reused:

```text
database_postgres_data
```

Redis also uses the existing external development volume configured in `docker-compose.yml`.

Feature testing should not delete or recreate these volumes. When a feature introduces a Flyway migration, the backend applies it at startup.

## Secrets

Do not put production secrets in Dockerfiles, Git, Android code, or the public Admin Web bundle.

The backend continues using:

```text
backend/config/application-secrets.yml
```

Keep that file local and never commit it.
