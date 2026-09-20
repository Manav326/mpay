# mPay Unified Development + Docker Deployment

This repository groups the current mPay backend, Admin Web, and Android client under one parent folder.

## Runtime model

- PostgreSQL: Docker service `postgres`
- Redis: Docker service `redis` (currently provisioned for the scalable architecture; the current backend does not yet require Redis for core requests)
- Spring Boot backend: Docker service `backend`
- Next.js Admin Web: Docker service `admin-web`
- Android: standalone Android Studio project; it is not part of the runtime Docker stack

## Local Windows + Android LAN

The current Windows PC LAN IP is `192.168.31.47`.

1. Put your real backend secrets at `backend/config/application-secrets.yml` using the existing file you already maintain. Never commit it.
2. Copy the root environment template:

```powershell
Copy-Item .env.example .env
```

3. Start the complete stack:

```powershell
docker compose up -d --build
```

Access from the PC:

- Admin Web: `http://localhost:3000`
- Backend API: `http://localhost:8080`

Access from another device on the same Wi-Fi:

- Admin Web: `http://192.168.31.47:3000`
- Backend API: `http://192.168.31.47:8080`

The Android project defaults to the LAN backend URL and can also be overridden at build time.

## Android API URL configuration

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

Set `.env` approximately as follows:

```text
MPAY_PUBLIC_DOMAIN=admin.example.com
MPAY_WEB_API_BASE_URL=https://admin.example.com
MPAY_CORS_ALLOWED_ORIGINS=https://admin.example.com
```

Caddy terminates HTTPS and routes:

- `/api/*` -> Spring Boot `backend:8080`
- everything else -> Next.js `admin-web:3000`

Therefore the Android app can use:

```text
https://admin.example.com/
```

without hard-coded private addresses in the production build.

## Existing PostgreSQL migration

The new Compose file uses a persistent named volume, so data survives container recreation. Before switching from your existing standalone PostgreSQL container, export that database first and restore it into the new `postgres` service. Do not delete the old PostgreSQL volume until the new stack is verified.

Typical export from the old container:

```powershell
docker exec <old-postgres-container> pg_dump -U recharge -d recharge -Fc > .\backup\mpay-recharge.dump
```

After the new Compose stack is running:

```powershell
docker cp .\backup\mpay-recharge.dump $(docker compose ps -q postgres):/tmp/mpay-recharge.dump
docker compose exec postgres pg_restore -U recharge -d recharge --clean --if-exists /tmp/mpay-recharge.dump
```

Run the restore only after taking a backup of any new data in the new volume.

Redis is normally treated as replaceable cache/session state. The current backend has no critical persistent dependency on Redis yet.

## Windows Firewall

For same-Wi-Fi Android testing, allow inbound TCP ports `3000` and `8080` on the Windows private network profile.

## Secrets

Do not put production secrets in Dockerfiles, Git, Android code, or the public Admin Web bundle.

The backend continues using the external file:

```text
backend/config/application-secrets.yml
```

The Docker image explicitly excludes that file and mounts it read-only at runtime.
