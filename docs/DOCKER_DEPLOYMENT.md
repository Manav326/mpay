# mPay Docker deployment

## Local

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

The four runtime services are `postgres`, `redis`, `backend`, and `admin-web`.

Local URLs:

- `http://localhost:3000` -> Admin Web
- `http://localhost:8080` -> Backend API
- `http://192.168.31.47:3000` -> Admin Web from a Wi-Fi device
- `http://192.168.31.47:8080` -> API from Android on the same Wi-Fi

## Production

Use one public hostname so the same URL serves both Admin Web and API:

```text
https://admin.example.com/       -> Next.js
https://admin.example.com/api/*  -> Spring Boot
```

Set:

```text
MPAY_PUBLIC_DOMAIN=admin.example.com
MPAY_WEB_API_BASE_URL=https://admin.example.com
MPAY_CORS_ALLOWED_ORIGINS=https://admin.example.com
```

Then:

```powershell
docker compose --profile production up -d --build
```

Caddy obtains/renews TLS certificates automatically when DNS for the hostname points to the Docker host and ports 80/443 are reachable.

## Android production build

```powershell
cd android
./gradlew.bat :app:assembleRelease -PmpayApiBaseUrl=https://admin.example.com/
```

The app does not need the internal Docker backend address.

## Data persistence

PostgreSQL and Redis use named Docker volumes. PostgreSQL is the authoritative persistent data store. Redis is treated as replaceable cache state in the current application and is persisted only to make restarts gentler.

## Security

- Never expose PostgreSQL or Redis publicly.
- Never commit `backend/config/application-secrets.yml`.
- Use HTTPS in production.
- Restrict Windows/VPS firewall access to the public ports that you actually need.
- Rotate demo credentials and provider keys before production.
