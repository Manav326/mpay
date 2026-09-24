param(
    [string]$DeployDirectory = "/opt/mpay",
    [string]$Branch = "",
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

function Invoke-Git {
    param([string[]]$Arguments)
    Invoke-CommandChecked "git" $Arguments
}

function Invoke-Compose {
    param([string[]]$Arguments)

    & docker compose --env-file ".env" -f "docker-compose.yml" -f "docker-compose.azure.yml" --profile production -p "mpay" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Get-CurrentBranch {
    $value = (git branch --show-current).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Deployment checkout is detached. Specify -Branch <branch> explicitly."
    }
    return $value
}

function Assert-CleanCheckout {
    $status = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect Git working tree."
    }
    if ($status.Count -gt 0) {
        throw "Deployment checkout has local changes. Commit/remove them before deployment:" + [Environment]::NewLine + ($status -join [Environment]::NewLine)
    }
}

try {
    if (-not (Test-Path (Join-Path $DeployDirectory ".git"))) {
        if ([string]::IsNullOrWhiteSpace($Branch)) {
            $Branch = "main"
        }

        $parent = Split-Path -Parent $DeployDirectory
        if (-not (Test-Path $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }

        Write-Host "Cloning branch '$Branch' into $DeployDirectory..." -ForegroundColor Yellow
        Invoke-Git @("clone", "--branch", $Branch, "--single-branch", $repoUrl, $DeployDirectory)
    }
    else {
        Set-Location $DeployDirectory

        if ([string]::IsNullOrWhiteSpace($Branch)) {
            $Branch = Get-CurrentBranch
        }

        Assert-CleanCheckout

        $preserveRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mpay-azure-preserve-" + [Guid]::NewGuid().ToString("N"))
        $preserveEnv = Join-Path $preserveRoot ".env"
        $preserveConfig = Join-Path $preserveRoot "backend-config"

        New-Item -ItemType Directory -Force -Path $preserveRoot | Out-Null

        try {
            if (Test-Path ".env") {
                Copy-Item ".env" $preserveEnv -Force
            }
            if (Test-Path "backend/config") {
                Copy-Item "backend/config" $preserveConfig -Recurse -Force
            }

            Invoke-Git @("fetch", "--prune", "origin", $Branch)

            $remoteBranchSha = (git rev-parse "origin/$Branch").Trim()
            if ($LASTEXITCODE -ne 0 -or $remoteBranchSha -notmatch "^[0-9a-f]{40}$") {
                throw "Remote branch origin/$Branch was not found."
            }

            $currentBranch = Get-CurrentBranch
            if ($currentBranch -ne $Branch) {
                $localBranchExists = git show-ref --verify --quiet "refs/heads/$Branch"
                if ($LASTEXITCODE -eq 0) {
                    Invoke-Git @("checkout", $Branch)
                }
                else {
                    Invoke-Git @("checkout", "--track", "-b", $Branch, "origin/$Branch")
                }
            }

            Invoke-Git @("pull", "--ff-only", "origin", $Branch)

            $postPullSha = (git rev-parse HEAD).Trim()
            $remoteHeadShaAfterPull = (git rev-parse "origin/$Branch").Trim()
            if ($postPullSha -ne $remoteHeadShaAfterPull) {
                throw "Deployment checkout does not match origin/$Branch after pull. Local=$postPullSha Remote=$remoteHeadShaAfterPull"
            }
        }
        finally {
            if (Test-Path $preserveEnv) {
                Copy-Item $preserveEnv ".env" -Force
            }
            if (Test-Path $preserveConfig) {
                New-Item -ItemType Directory -Force -Path "backend/config" | Out-Null
                Copy-Item $preserveConfig "." -Recurse -Force
            }
            Remove-Item $preserveRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    Set-Location $DeployDirectory

    if (-not (Test-Path ".env")) {
        throw "Missing .env. Copy .env.azure.example to .env and fill in the database password and domain values first."
    }

    if (-not (Test-Path "backend/config/application-secrets.yml")) {
        throw "Missing backend/config/application-secrets.yml. Provide the external secret configuration before deployment."
    }

    $commitSha = (git rev-parse HEAD).Trim()
    if ($commitSha -notmatch "^[0-9a-f]{40}$") {
        throw "Could not determine deployment commit SHA."
    }

    $remoteHeadSha = (git rev-parse "origin/$Branch").Trim()
    if ($remoteHeadSha -ne $commitSha) {
        throw "Deployment checkout is not synchronized with origin/$Branch. Local=$commitSha Remote=$remoteHeadSha"
    }

    $backendImage = "ghcr.io/manav326/mpay-backend"
    $adminWebImage = "ghcr.io/manav326/mpay-admin-web"
    $backendTag = "sha-$commitSha"
    $adminWebTag = "azure-sha-$commitSha"

    $env:MPAY_BACKEND_IMAGE = $backendImage
    $env:MPAY_ADMIN_WEB_IMAGE = $adminWebImage
    $env:MPAY_IMAGE_TAG = $backendTag
    $env:MPAY_ADMIN_WEB_TAG = $adminWebTag

    $expectedBackendImage = $backendImage + ":" + $backendTag
    $expectedAdminWebImage = $adminWebImage + ":" + $adminWebTag

    Write-Host ""
    Write-Host "mPay Azure deployment" -ForegroundColor Cyan
    Write-Host "Branch       : $Branch" -ForegroundColor Cyan
    Write-Host "Commit       : $commitSha" -ForegroundColor Cyan
    Write-Host "Backend      : $expectedBackendImage" -ForegroundColor Cyan
    Write-Host "Admin Web    : $expectedAdminWebImage" -ForegroundColor Cyan
    Write-Host ""

    Write-Host "Validating Azure Compose configuration..." -ForegroundColor Yellow
    Invoke-Compose @("config", "--quiet")

    $configuredImages = @(& docker compose --env-file ".env" -f "docker-compose.yml" -f "docker-compose.azure.yml" --profile production -p "mpay" config --images)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the resolved Docker image configuration."
    }
    if ($configuredImages -notcontains $expectedBackendImage) {
        throw "Compose resolved backend image does not match deployment commit. Expected $expectedBackendImage"
    }
    if ($configuredImages -notcontains $expectedAdminWebImage) {
        throw "Compose resolved Admin Web image does not match deployment commit. Expected $expectedAdminWebImage"
    }

    Write-Host "Pulling exact commit images..." -ForegroundColor Yellow
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
    Write-Host "Admin Web : https://mpay.thinkwithsujeet.in" -ForegroundColor Green
    Write-Host "Backend   : https://api.mpay.thinkwithsujeet.in" -ForegroundColor Green
    Write-Host "Commit    : $commitSha" -ForegroundColor Green
}
finally {
    Set-Location $originalLocation
}
