param(
    [string]$Branch = "perf/azure-deployment-optimization",
    [string]$DeployDirectory = "",
    [string]$RestoreBackup = "",
    [string]$ApiBaseUrl = "http://192.168.31.47:8080",
    [string]$CorsAllowedOrigins = "http://localhost:3000,http://127.0.0.1:3000,http://192.168.31.47:3000"
)

$ErrorActionPreference = "Stop"
$repoUrl = "https://github.com/Manav326/mpay.git"
$originalLocation = (Get-Location).Path
$sourceRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($DeployDirectory)) {
    $DeployDirectory = Join-Path (Split-Path -Parent $sourceRoot) "mpay-azure-dev-local"
}

function Invoke-Git {
    param([string[]]$Arguments)
    & git @Arguments
    if ($LASTEXITCODE -ne 0) { throw "git failed: $($Arguments -join ' ')" }
}

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
}

try {
if (-not (Test-Path (Join-Path $DeployDirectory ".git"))) {
    & git clone --branch $Branch --single-branch $repoUrl $DeployDirectory
    if ($LASTEXITCODE -ne 0) { throw "Could not clone deployment branch." }
}
else {
    Set-Location $DeployDirectory
    Invoke-Git @("fetch", "origin", $Branch)
    Invoke-Git @("checkout", $Branch)
    Invoke-Git @("reset", "--hard", "origin/$Branch")
}

Set-Location $DeployDirectory
$commitSha = (git rev-parse HEAD).Trim()
if ($commitSha -notmatch "^[0-9a-f]{40}$") { throw "Could not determine deployment commit SHA." }

$sourceConfig = Join-Path (Join-Path $sourceRoot "backend") "config"
$targetConfig = Join-Path (Join-Path $DeployDirectory "backend") "config"

if (Test-Path $sourceConfig) {
    if (Test-Path $targetConfig) { Remove-Item -Recurse -Force $targetConfig }
    Copy-Item -Recurse -Force $sourceConfig $targetConfig
}
elseif (-not (Test-Path $targetConfig)) { throw "backend/config is missing." }

function Test-PortAvailable {
    param([int]$Port)

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        return $true
    }
    catch {
        return $false
    }
    finally {
        if ($null -ne $listener) {
            $listener.Stop()
        }
    }
}

foreach ($port in @(3000, 8080)) {
    if (-not (Test-PortAvailable -Port $port)) {
        throw "Port $port is already in use. Stop the existing mPay stack first; this script will not stop or alter it."
    }
}

$env:MPAY_BACKEND_IMAGE = "ghcr.io/manav326/mpay-backend"
$env:MPAY_ADMIN_WEB_IMAGE = "ghcr.io/manav326/mpay-admin-web"
$env:MPAY_IMAGE_TAG = "sha-$commitSha"
$env:MPAY_WEB_API_BASE_URL = $ApiBaseUrl
$env:MPAY_CORS_ALLOWED_ORIGINS = $CorsAllowedOrigins

$composeFiles = @("-f", "docker-compose.yml", "-f", "docker-compose.local-deploy.yml")
$project = "mpay-azure-dev-local"

Write-Host "Deployment commit: $commitSha" -ForegroundColor Green
Write-Host "Pulling exact GHCR images: sha-$commitSha" -ForegroundColor Yellow
Invoke-Compose ($composeFiles + @("-p", $project, "pull", "backend", "admin-web"))
Invoke-Compose ($composeFiles + @("-p", $project, "up", "-d", "postgres", "redis"))

if (-not [string]::IsNullOrWhiteSpace($RestoreBackup)) {
    & (Join-Path (Join-Path $sourceRoot "scripts") "mpay-db-restore.ps1") -BackupDirectory $RestoreBackup -ComposeProject $project -ComposeOverlay (Join-Path $DeployDirectory "docker-compose.local-deploy.yml")
    if ($LASTEXITCODE -ne 0) { throw "Database/media restore failed." }
}
else {
    Invoke-Compose ($composeFiles + @("-p", $project, "up", "-d", "backend", "admin-web"))
}

Write-Host ""
Invoke-Compose ($composeFiles + @("-p", $project, "ps"))
Write-Host ""
Write-Host "Admin Web : http://localhost:3000" -ForegroundColor Cyan
Write-Host "Backend   : http://localhost:8080" -ForegroundColor Cyan
Write-Host "Commit    : $commitSha" -ForegroundColor Cyan

} finally {
    Set-Location $originalLocation
}
