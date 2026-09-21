$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ([string]::IsNullOrWhiteSpace($branch)) {
    throw "Could not determine the current Git branch."
}

if ($branch -eq "main") {
    $imageTag = "latest"
} else {
    $featureName = $branch -replace '^feature/', ''
    $safeFeatureName = $featureName.ToLower() -replace '[^a-z0-9._-]', '-'
    $imageTag = "feature-$safeFeatureName-latest"
}

$env:MPAY_BACKEND_IMAGE = "ghcr.io/manav326/mpay-backend"
$env:MPAY_ADMIN_WEB_IMAGE = "ghcr.io/manav326/mpay-admin-web"
$env:MPAY_IMAGE_TAG = $imageTag

$backendImage = "$($env:MPAY_BACKEND_IMAGE):$imageTag"
$adminWebImage = "$($env:MPAY_ADMIN_WEB_IMAGE):$imageTag"

Write-Host ""
Write-Host "mPay GitHub test environment" -ForegroundColor Cyan
Write-Host "Branch    : $branch"
Write-Host "Image tag : $imageTag"
Write-Host "Backend   : $backendImage"
Write-Host "Admin Web : $adminWebImage"
Write-Host ""

Write-Host "Pulling exact branch images from GHCR via Compose..." -ForegroundColor Yellow
docker compose -p mpay-github pull backend admin-web
if ($LASTEXITCODE -ne 0) { throw "Docker image pull failed." }

Write-Host "Starting/reusing PostgreSQL, Redis and pgAdmin..." -ForegroundColor Yellow
docker compose -p mpay-github up -d postgres redis pgadmin
if ($LASTEXITCODE -ne 0) { throw "Infrastructure startup failed." }

Write-Host "Starting backend and Admin Web without local build or dependency restart..." -ForegroundColor Yellow
docker compose -p mpay-github up -d --no-build --no-deps backend admin-web
if ($LASTEXITCODE -ne 0) { throw "Application startup failed." }

Write-Host ""
Write-Host "Current mpay-github status:" -ForegroundColor Green
docker compose -p mpay-github ps
