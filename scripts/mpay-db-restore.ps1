param(
    [Parameter(Mandatory=$true)]
    [string]$BackupDirectory,
    [string]$ComposeProject = "mpay-azure-dev-local",
    [string]$ComposeOverlay = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path (Join-Path $BackupDirectory "postgres.dump"))) { throw "postgres.dump not found in backup directory." }
if (-not (Test-Path (Join-Path $BackupDirectory "manifest.json"))) { throw "manifest.json not found in backup directory." }

$composeArgs = @("-f", "docker-compose.yml")
if (-not [string]::IsNullOrWhiteSpace($ComposeOverlay)) {
    $composeArgs += @("-f", $ComposeOverlay)
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

$postgresContainer = (& docker compose @composeArgs ps -q postgres).Trim()
$envLines = & docker inspect $postgresContainer --format '{{range .Config.Env}}{{println .}}{{end}}'
$dbName = (($envLines | Where-Object { $_ -like "POSTGRES_DB=*" } | Select-Object -First 1) -split "=", 2)[1]
$dbUser = (($envLines | Where-Object { $_ -like "POSTGRES_USER=*" } | Select-Object -First 1) -split "=", 2)[1]

Write-Host "Restoring PostgreSQL database '$dbName'..." -ForegroundColor Yellow
Get-Content (Join-Path $BackupDirectory "postgres.dump") -AsByteStream -ReadCount 0 |
    & docker exec -i $postgresContainer pg_restore -U $dbUser -d $dbName --clean --if-exists --no-owner --no-acl
if ($LASTEXITCODE -ne 0) { throw "pg_restore failed." }

Write-Host "Starting backend and Admin Web..." -ForegroundColor Yellow
Invoke-Compose @("up", "-d", "backend", "admin-web")

$backendContainer = (& docker compose @composeArgs ps -q backend).Trim()
if ([string]::IsNullOrWhiteSpace($backendContainer)) { throw "Backend container not found after startup." }

foreach ($media in @("profile-images", "rental-images")) {
    $source = Join-Path $BackupDirectory $media
    if (Test-Path $source) {
        Write-Host "Restoring $media..." -ForegroundColor Yellow
        Invoke-Docker @("cp", "$($source)/.", "$($backendContainer):/app/data/$media")
    }
}

Write-Host "Restoring media ownership..." -ForegroundColor Yellow
& docker exec -u 0 $backendContainer sh -c "chown -R 10001:10001 /app/data/profile-images /app/data/rental-images"
if ($LASTEXITCODE -ne 0) { throw "Could not restore media ownership." }

Write-Host "Database and media restore completed." -ForegroundColor Green
