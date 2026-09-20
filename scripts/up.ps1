$ErrorActionPreference = 'Stop'
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
docker compose up -d --build
Write-Host "mPay stack started." -ForegroundColor Green
Write-Host "Admin Web : http://localhost:3000" -ForegroundColor Cyan
Write-Host "API       : http://localhost:8080" -ForegroundColor Cyan
Write-Host "LAN Web   : http://192.168.31.47:3000" -ForegroundColor Cyan
Write-Host "LAN API   : http://192.168.31.47:8080" -ForegroundColor Cyan
