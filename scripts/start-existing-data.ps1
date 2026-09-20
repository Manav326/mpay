$ErrorActionPreference = "Stop"
Write-Host "Stopping the old database containers only (volumes are preserved)..." -ForegroundColor Yellow
docker stop database-postgres-1 database-redis-1 2>$null | Out-Null
Write-Host "Starting unified mPay stack using existing PostgreSQL/Redis volumes..." -ForegroundColor Cyan
docker compose up -d --build
Write-Host "Done. Check: docker compose ps" -ForegroundColor Green
