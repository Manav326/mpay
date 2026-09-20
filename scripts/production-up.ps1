$ErrorActionPreference = 'Stop'
if (-not (Test-Path '.env')) { throw 'Create .env from .env.example and set MPAY_PUBLIC_DOMAIN, MPAY_WEB_API_BASE_URL and MPAY_CORS_ALLOWED_ORIGINS first.' }
docker compose --profile production up -d --build
