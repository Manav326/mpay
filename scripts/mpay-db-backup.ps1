param(
    [string]$ComposeProject = "mpay-github",
    [string]$BackupDirectory = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $repoRoot "backups"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $BackupDirectory "mpay-$timestamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

function Invoke-Docker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker failed: $($Arguments -join ' ')"
    }
}

$postgresContainer = (& docker compose -p $ComposeProject ps -q postgres).Trim()
$backendContainer = (& docker compose -p $ComposeProject ps -q backend).Trim()

if ([string]::IsNullOrWhiteSpace($postgresContainer)) { throw "PostgreSQL container not found for Compose project '$ComposeProject'." }
if ([string]::IsNullOrWhiteSpace($backendContainer)) { throw "Backend container not found for Compose project '$ComposeProject'." }

$envLines = & docker inspect $postgresContainer --format '{{range .Config.Env}}{{println .}}{{end}}'
$dbName = (($envLines | Where-Object { $_ -like "POSTGRES_DB=*" } | Select-Object -First 1) -split "=", 2)[1]
$dbUser = (($envLines | Where-Object { $_ -like "POSTGRES_USER=*" } | Select-Object -First 1) -split "=", 2)[1]

if ([string]::IsNullOrWhiteSpace($dbName) -or [string]::IsNullOrWhiteSpace($dbUser)) { throw "Could not determine PostgreSQL database/user." }

Write-Host "Backing up PostgreSQL database '$dbName'..." -ForegroundColor Yellow
$containerDumpPath = "/tmp/mpay-postgres.dump"
$hostDumpPath = Join-Path $backupRoot "postgres.dump"

& docker exec $postgresContainer sh -c "pg_dump -U '$dbUser' -d '$dbName' -Fc --no-owner --no-acl > '$containerDumpPath'"
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed." }

& docker exec $postgresContainer pg_restore --list $containerDumpPath *> $null
if ($LASTEXITCODE -ne 0) {
    & docker exec $postgresContainer rm -f $containerDumpPath *> $null
    throw "The PostgreSQL dump failed validation."
}

if (Test-Path $hostDumpPath) { Remove-Item -Force $hostDumpPath }
& docker cp "$($postgresContainer):$containerDumpPath" $hostDumpPath
if ($LASTEXITCODE -ne 0) {
    & docker exec $postgresContainer rm -f $containerDumpPath *> $null
    throw "Could not copy PostgreSQL dump from the container."
}

& docker exec $postgresContainer rm -f $containerDumpPath
if ($LASTEXITCODE -ne 0) { throw "Could not clean up temporary PostgreSQL dump." }

Write-Host "Backing up profile/rental media..." -ForegroundColor Yellow
Invoke-Docker @("cp", "$($backendContainer):/app/data/profile-images", (Join-Path $backupRoot "profile-images"))
Invoke-Docker @("cp", "$($backendContainer):/app/data/rental-images", (Join-Path $backupRoot "rental-images"))

$sourceCommit = (git -C $repoRoot rev-parse HEAD 2>$null).Trim()
$manifest = @{
    createdAt = (Get-Date).ToString("o")
    composeProject = $ComposeProject
    database = $dbName
    databaseUser = $dbUser
    sourceCommit = $sourceCommit
    includes = @("postgres.dump", "profile-images", "rental-images")
    excludes = @("redis", "caddy certificates", "backend/config secrets")
} | ConvertTo-Json -Depth 4

Set-Content -Path (Join-Path $backupRoot "manifest.json") -Value $manifest -Encoding UTF8
Write-Host ""
Write-Host "Backup created:" -ForegroundColor Green
Write-Host $backupRoot -ForegroundColor Cyan
