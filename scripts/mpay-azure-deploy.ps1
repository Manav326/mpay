# Secrets in .env and backend/config are intentionally ignored by Git and therefore
# are not copied during checkout updates. Git updates tracked files without touching
# those ignored runtime configuration files, and the required files are validated below.
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



function Get-StorageFreeBytes {
    param([string]$Path = "/var/lib/containerd")

    $lines = @(& df -Pk $Path 2>$null)
    if ($LASTEXITCODE -ne 0 -or $lines.Count -lt 2) {
        $lines = @(& df -Pk "/" 2>$null)
    }
    if ($LASTEXITCODE -ne 0 -or $lines.Count -lt 2) {
        throw "Could not determine available filesystem storage for $Path."
    }

    $fields = ($lines[1] -split "\s+") | Where-Object { $_ -ne "" }
    if ($fields.Count -lt 4 -or $fields[3] -notmatch "^[0-9]+$") {
        throw "Could not parse available filesystem storage for $Path."
    }

    return ([int64]$fields[3] * 1024)
}

function Get-StorageFreeGb {
    return [math]::Round((Get-StorageFreeBytes) / 1GB, 2)
}

function Show-DockerStorage {
    Write-Host "Docker storage status:" -ForegroundColor DarkGray
    & docker system df
    Write-Host "Filesystem free space: $(Get-StorageFreeGb) GB" -ForegroundColor DarkGray
}

function Invoke-SafeDockerRetentionCleanup {
    Write-Host "Low Docker storage detected. Removing unused Docker resources; volumes are NOT pruned." -ForegroundColor Yellow

    # Never use 'docker system prune --volumes' here. The Compose deployment owns
    # PostgreSQL/Redis/media volumes and those must survive deployment cleanup.
    Invoke-CommandChecked "docker" @("container", "prune", "-f")
    Invoke-CommandChecked "docker" @("image", "prune", "-af")
    Invoke-CommandChecked "docker" @("builder", "prune", "-af")
}

function Ensure-DockerStorage {
    param([int64]$MinimumFreeBytes = 4GB)

    $freeBytes = Get-StorageFreeBytes
    Write-Host "Docker filesystem free space: $([math]::Round($freeBytes / 1GB, 2)) GB" -ForegroundColor DarkGray

    if ($freeBytes -lt $MinimumFreeBytes) {
        Show-DockerStorage
        Invoke-SafeDockerRetentionCleanup
        $freeBytes = Get-StorageFreeBytes
        Write-Host "Docker filesystem free space after cleanup: $([math]::Round($freeBytes / 1GB, 2)) GB" -ForegroundColor DarkGray
    }

    if ($freeBytes -lt $MinimumFreeBytes) {
        Show-DockerStorage
        throw "Insufficient Docker filesystem storage. At least $([math]::Round($MinimumFreeBytes / 1GB, 2)) GB must remain free after safe cleanup. No Docker volumes were pruned."
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

        # Read the authoritative remote branch SHA directly from the remote. Do not
        # depend on refs/remotes/origin/<branch>, because this VM may have been created
        # from a single-branch clone where remote-tracking refs are incomplete.
        $lsRemoteOutput = @(git ls-remote origin "refs/heads/$Branch")
        if ($LASTEXITCODE -ne 0 -or $lsRemoteOutput.Count -eq 0) {
            throw "Remote branch refs/heads/$Branch was not found."
        }

        $remoteBranchSha = (($lsRemoteOutput[0] -split "\s+")[0]).Trim()
        if ($remoteBranchSha -notmatch "^[0-9a-f]{40}$") {
            throw "Could not determine the remote SHA for refs/heads/$Branch."
        }

        # Fetch the exact branch into FETCH_HEAD. This works for both normal and
        # single-branch clones without relying on remote-tracking branch configuration.
        Invoke-Git @("fetch", "origin", "refs/heads/$Branch")

        $currentBranch = Get-CurrentBranch
        if ($currentBranch -ne $Branch) {
            $localBranchExists = git show-ref --verify --quiet "refs/heads/$Branch"
            if ($LASTEXITCODE -eq 0) {
                Invoke-Git @("checkout", $Branch)
            }
            else {
                Invoke-Git @("checkout", "-b", $Branch, "FETCH_HEAD")
            }
        }

        Invoke-Git @("merge", "--ff-only", "FETCH_HEAD")

        $postPullSha = (git rev-parse HEAD).Trim()
        if ($postPullSha -ne $remoteBranchSha) {
            throw "Deployment checkout does not match remote/$Branch after fetch. Local=$postPullSha Remote=$remoteBranchSha"
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

    Write-Host "Checking Docker storage before image pull..." -ForegroundColor Yellow
    Ensure-DockerStorage

    Write-Host "Pulling exact commit images..." -ForegroundColor Yellow
    Invoke-Compose @("pull", "backend", "admin-web", "coturn")

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

    Write-Host "Starting backend, Admin Web, Caddy and TURN..." -ForegroundColor Yellow
    Invoke-Compose @("up", "-d", "backend", "admin-web", "caddy", "coturn")

    Write-Host ""
    Invoke-Compose @("ps")

    # After the new stack is healthy, running containers keep their images alive.
    # Remove only resources no longer referenced by the running/stopped stack.
    Write-Host "Applying post-deployment Docker retention cleanup..." -ForegroundColor Yellow
    Invoke-SafeDockerRetentionCleanup
    Show-DockerStorage

    Write-Host ""
    Write-Host "Admin Web : https://mpay.thinkwithsujeet.in" -ForegroundColor Green
    Write-Host "Storefront : https://store.thinkwithsujeet.in" -ForegroundColor Green
    Write-Host "Backend   : https://api.mpay.thinkwithsujeet.in" -ForegroundColor Green
    Write-Host "TURN      : turn.mpay.thinkwithsujeet.in:3478" -ForegroundColor Green
    Write-Host "Commit    : $commitSha" -ForegroundColor Green
}
finally {
    Set-Location $originalLocation
}
