param(
    [string]$DeployDirectory = "/opt/mpay",
    [string]$Branch = "main",
    [string]$CommitSha = "",
    [string]$RestoreBackup = ""
)

$ErrorActionPreference = "Stop"
$repoUrl = "https://github.com/Manav326/mpay.git"
$originalLocation = (Get-Location).Path

function Invoke-CommandChecked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed: $($Arguments -join ' ')"
    }
}

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose --env-file ".env" -f "docker-compose.yml" -f "docker-compose.azure.yml" --profile production -p "mpay" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

try {
    if (-not (Test-Path (Join-Path $DeployDirectory ".git"))) {
        $parent = Split-Path -Parent $DeployDirectory
        if (-not (Test-Path $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }
        Invoke-CommandChecked "git" @("clone", "--branch", $Branch, "--single-branch", $repoUrl, $DeployDirectory)
    }
    else {
        Set-Location $DeployDirectory

        $preserveRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mpay-azure-preserve-" + [Guid]::NewGuid().ToString("N"))
        $preserveEnv = Join-Path $preserveRoot ".env"
        $preserveConfig = Join-Path $preserveRoot "backend-config"

        New-Item -ItemType Directory -Force -Path $preserveRoot | Out-Null

        if (Test-Path ".env") {
            Copy-Item ".env" $preserveEnv -Force
        }
        if (Test-Path "backend/config") {
            Copy-Item "backend/config" $preserveConfig -Recurse -Force
        }

        Invoke-CommandChecked "git" @("fetch", "--prune", "origin", $Branch)
        Invoke-CommandChecked "git" @("checkout", $Branch)
        Invoke-CommandChecked "git" @("reset", "--hard", "origin/$Branch")

        if (Test-Path $preserveEnv) {
            Copy-Item $preserveEnv ".env" -Force
        }
        if (Test-Path $preserveConfig) {
            New-Item -ItemType Directory -Force -Path "backend/config" | Out-Null
            Copy-Item $preserveConfig "." -Recurse -Force
        }

        Remove-Item $preserveRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    Set-Location $DeployDirectory

    if (-not (Test-Path ".env")) {
        throw "Missing .env. Copy .env.azure.example to .env and fill in the database password and image tags first."
    }

    if (-not (Test-Path "backend/config/application-secrets.yml")) {
        throw "Missing backend/config/application-secrets.yml. Provide the external secret configuration before deployment."
    }

    if ([string]::IsNullOrWhiteSpace($CommitSha)) {
        $CommitSha = (git rev-parse HEAD).Trim()
    }
    else {
        Invoke-CommandChecked "git" @("fetch", "origin", $CommitSha)
        Invoke-CommandChecked "git" @("checkout", "--detach", $CommitSha)
    }

    if ($CommitSha -notmatch "^[0-9a-f]{40}$") {
        throw "CommitSha must be a 40-character Git SHA."
    }

    Write-Host "Validating Azure Compose configuration..." -ForegroundColor Yellow
    Invoke-Compose @("config", "--quiet")

    Write-Host "Deployment commit: $CommitSha" -ForegroundColor Green
    Write-Host "Pulling exact application images..." -ForegroundColor Yellow
    Invoke-Compose @("pull", "backend", "admin-web")

    Write-Host "Starting PostgreSQL and Redis..." -ForegroundColor Yellow
    Invoke-Compose @("up", "-d", "postgres", "redis")

    if (-not [string]::IsNullOrWhiteSpace($RestoreBackup)) {
        if (-not (Test-Path $RestoreBackup)) {
            throw "Restore backup directory not found: $RestoreBackup"
        }

        $restoreScript = Join-Path $DeployDirectory "scripts/mpay-db-restore.ps1"
        $restoreOverlay = Join-Path $DeployDirectory "docker-compose.azure.yml"
        & $restoreScript -BackupDirectory $RestoreBackup -ComposeProject "mpay" -ComposeOverlay $restoreOverlay
        if ($LASTEXITCODE -ne 0) {
            throw "Database/media restore failed."
        }
    }

    Write-Host "Starting backend, Admin Web and Caddy..." -ForegroundColor Yellow
    Invoke-Compose @("up", "-d", "backend", "admin-web", "caddy")

    Write-Host ""
    Invoke-Compose @("ps")
    Write-Host ""
    Write-Host "Admin Web : https://mpay.thinkwithsujeet.in" -ForegroundColor Cyan
    Write-Host "Backend   : https://api.mpay.thinkwithsujeet.in" -ForegroundColor Cyan
    Write-Host "Commit    : $CommitSha" -ForegroundColor Cyan
}
finally {
    Set-Location $originalLocation
}
