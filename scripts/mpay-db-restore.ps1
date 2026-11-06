param(
    [Parameter(Mandatory=$true)]
    [string]$BackupDirectory,
    [string]$ComposeProject = "mpay-production",
    [string]$ComposeOverlay = "",
    [string]$EnvFile = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$originalLocation = (Get-Location).Path
Set-Location $repoRoot

if (-not (Test-Path (Join-Path $BackupDirectory "postgres.dump"))) { throw "postgres.dump not found in backup directory." }
if (-not (Test-Path (Join-Path $BackupDirectory "manifest.json"))) { throw "manifest.json not found in backup directory." }

if (-not (Test-Path $BackupDirectory)) { throw "Backup directory not found: $BackupDirectory" }

$baseCompose = Join-Path $repoRoot "docker-compose.yml"
$composeArgs = @("-f", $baseCompose)
if (-not [string]::IsNullOrWhiteSpace($ComposeOverlay)) {
    $overlayPath = if ([System.IO.Path]::IsPathRooted($ComposeOverlay)) { $ComposeOverlay } else { Join-Path $repoRoot $ComposeOverlay }
    if (-not (Test-Path $overlayPath)) { throw "Compose overlay not found: $overlayPath" }
    $composeArgs += @("-f", $overlayPath)
}
if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    $envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $repoRoot $EnvFile }
    if (-not (Test-Path $envPath)) { throw "Compose env file not found: $envPath" }
    $composeArgs = @("--env-file", $envPath) + $composeArgs
}
$composeArgs += @("-p", $ComposeProject)

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose @composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
}

function Invoke-Docker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker failed: $($Arguments -join ' ')" }
}

Write-Host "Starting isolated PostgreSQL..." -ForegroundColor Yellow
Invoke-Compose @("up", "-d", "postgres")

for ($i = 1; $i -le 60; $i++) {
    $postgresContainer = (& docker compose @composeArgs ps -q postgres).Trim()
    if (-not [string]::IsNullOrWhiteSpace($postgresContainer)) {
        $health = (& docker inspect $postgresContainer --format '{{.State.Health.Status}}').Trim()
        if ($health -eq "healthy") { break }
    }
    if ($i -eq 60) { throw "PostgreSQL did not become healthy." }
    Start-Sleep -Seconds 2
}

Invoke-Compose @("stop", "mpay-api", "mpay-admin-web")

$postgresContainer = (& docker compose @composeArgs ps -q postgres).Trim()
$envLines = & docker inspect $postgresContainer --format '{{range .Config.Env}}{{println .}}{{end}}'
$dbName = (($envLines | Where-Object { $_ -like "POSTGRES_DB=*" } | Select-Object -First 1) -split "=", 2)[1]
$dbUser = (($envLines | Where-Object { $_ -like "POSTGRES_USER=*" } | Select-Object -First 1) -split "=", 2)[1]

Write-Host "Restoring PostgreSQL database '$dbName'..." -ForegroundColor Yellow
$hostDumpPath = Join-Path $BackupDirectory "postgres.dump"
$containerDumpPath = "/tmp/mpay-postgres.dump"

& docker cp $hostDumpPath "$($postgresContainer):$containerDumpPath"
if ($LASTEXITCODE -ne 0) { throw "Could not copy PostgreSQL dump into the container." }

try {
    & docker exec $postgresContainer pg_restore -U $dbUser -d $dbName --clean --if-exists --no-owner --no-acl $containerDumpPath
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed." }
}
finally {
    & docker exec $postgresContainer rm -f $containerDumpPath *> $null
}

Write-Host "Creating the API container without starting application traffic..." -ForegroundColor Yellow
Invoke-Compose @("create", "--no-build", "--force-recreate", "mpay-api")

$apiContainer = (& docker compose @composeArgs ps --all -q mpay-api).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($apiContainer)) {
    throw "Backend container could not be created for offline media restoration."
}

foreach ($media in @("profile-images", "rental-images")) {
    $source = Join-Path $BackupDirectory $media
    if (-not (Test-Path $source)) {
        throw "The backup is missing required media directory '$media'. Restore aborted before starting application traffic."
    }
    Write-Host "Restoring $media into the stopped API volume..." -ForegroundColor Yellow
    Invoke-Docker @("cp", "$($source)/.", "$($apiContainer):/app/data/$media")
}

Write-Host "Starting API after database and media are restored..." -ForegroundColor Yellow
Invoke-Compose @("up", "-d", "--no-build", "mpay-api")

$apiContainer = (& docker compose @composeArgs ps -q mpay-api).Trim()
if ([string]::IsNullOrWhiteSpace($apiContainer)) { throw "Backend container not found after startup." }

Write-Host "Restoring media ownership..." -ForegroundColor Yellow
& docker exec -u 0 $apiContainer sh -c "chown -R 10001:10001 /app/data/profile-images /app/data/rental-images"
if ($LASTEXITCODE -ne 0) { throw "Could not restore media ownership." }

Write-Host "Starting Admin Web after API and media are ready..." -ForegroundColor Yellow
Invoke-Compose @("up", "-d", "--no-build", "mpay-admin-web")

Write-Host "Database and media restore completed." -ForegroundColor Green
Set-Location $originalLocation
