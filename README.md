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
├── backend
└── admin-web

mpay-github
├── postgres
├── redis
├── backend
└── admin-web
```

Both use the same host ports:

- Admin Web: `3000`
- Backend API: `8080`

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
```

### What GitHub Actions does

A push to `feature/**`:

1. Validates `docker-compose.yml`.
2. Builds the backend and Admin Web Docker images in GitHub.
3. Publishes feature images to GHCR.
4. Uses a stable branch tag such as:
   `feature-recharge-history-latest`.
5. Also publishes an immutable commit tag:
   `sha-<commit>`.

A pull request targeting `main` builds the images for validation but does not publish PR images.

A push to `main` publishes:

- `latest`
- `sha-<commit>`

## Local GitHub feature testing

The GitHub-built images are the images used by the `mpay-github` environment.

### First-time GHCR login

If GHCR requires authentication for your account, run:

```powershell
docker login ghcr.io -u Manav326
```

Use a GitHub token with package read access. Never commit the token.

### Switch away from the stable stack

Because both stacks use ports 3000 and 8080, stop the stable stack without deleting it:

```powershell
docker compose -p mpay stop
```

### Checkout a feature branch

```powershell
git checkout feature/<feature-name>
git pull origin feature/<feature-name>
```

### Pull and run the GitHub-built images

From the repository root:

```powershell
.\scripts\mpay-github-update.ps1
```

The script automatically derives the GHCR tag from the current branch.

For example:

```text
feature/recharge-history
        ↓
feature-recharge-history-latest
```

It then:

```text
1. docker compose pull backend admin-web
2. start/reuse postgres and redis
3. start backend/admin-web with --no-build --no-deps
```

This means the local machine does not run the Gradle build or Next.js build for every feature update, and PostgreSQL/Redis are not restarted just because the application code changed.

### Manual fast update

The equivalent commands are:

```powershell
docker compose -p mpay-github pull backend admin-web
docker compose -p mpay-github up -d --no-build --no-deps backend admin-web
```

## Switching back to the stable application

```powershell
docker compose -p mpay-github stop
docker compose -p mpay up -d
```

Do not use `down -v` for routine switching.

## Local development configuration

The root `.env` intentionally remains a development configuration.

The default application images are:

```text
MPAY_BACKEND_IMAGE=ghcr.io/manav326/mpay-backend
MPAY_ADMIN_WEB_IMAGE=ghcr.io/manav326/mpay-admin-web
MPAY_IMAGE_TAG=latest
```

The `mpay-github-update.ps1` helper overrides `MPAY_IMAGE_TAG` for the current feature branch, so you do not need to edit `.env` every time.

The backend continues using the local development secret file:

```text
backend/config/application-secrets.yml
```

Keep that file local and never commit it.

## Local URLs

From the development PC:

- Admin Web: `http://localhost:3000`
- Backend API: `http://localhost:8080`

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

The Docker image excludes this file and mounts it read-only at runtime.
