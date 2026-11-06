# mPay Docker deployment

## Local

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

The four runtime services are `postgres`, `redis`, `mpay-api`, and `mpay-admin-web`.

Local URLs:

- `http://localhost:3000` -> Admin Web
- `http://localhost:8080` -> Backend API
- `http://192.168.31.47:3000` -> Admin Web from a Wi-Fi device
- `http://192.168.31.47:8080` -> API from Android on the same Wi-Fi

## Production

The selected production path is local Docker behind the existing central Cloudflare Tunnel. Public hostnames are not maintained in individual Compose files or secret-bearing environment files.

Change the single root URL in `config/public-hosts.env`, then let the shared renderer derive the public routes and build-time origins:

- `https://mpay.quantaedge.in` → mPay Admin Web service (`mpay-admin-web`)
- `https://api-mpay.quantaedge.in` → mPay API service (`mpay-api`)
- `turn-mpay.quantaedge.in` → direct TURN UDP/TCP endpoint; not an HTTP Tunnel route

Use the guarded deployment runbook in `docs/deployment/LOCAL_CLOUDFLARE_PRODUCTION.md`. Production uses the `mpay-production` Compose project, product-qualified app services and `mpay_production_*` data volumes to avoid cross-project naming collisions. It uses the existing `cloudflare-edge` network and loopback-only host diagnostics. Do not start a second tunnel or Caddy listener for this selected path.

## Android production build

```powershell
.\scripts\android-build-release.ps1
```

The release helper reads `config/public-hosts.env` and derives the app's API and web origins from the same root. Local development may still use an explicit `-ApiBaseUrl` override.

## Data persistence

PostgreSQL and Redis use named Docker volumes. PostgreSQL is the authoritative persistent data store. Redis is treated as replaceable cache state in the current application and is persisted only to make restarts gentler.

## Security

- Never expose PostgreSQL or Redis publicly.
- Never commit `backend/config/application-secrets.yml`.
- Use HTTPS in production.
- Restrict Windows/VPS firewall access to the public ports that you actually need.
- Rotate demo credentials and provider keys before production.
