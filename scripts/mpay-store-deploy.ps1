param(
    [string]$DeployDirectory = "/opt/mpay",
    [string]$Branch = ""
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

    & docker compose --env-file ".env" -f "docker-compose.yml" -f "docker-compose.azure.yml" -f "docker-compose.store.yml" --profile production -p "mpay" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Assert-CleanCheckout {
    $status = @(git status --porcelain)
    if ($status.Count -gt 0) {
        throw "Deployment checkout has uncommitted changes. Refusing to overwrite them."
    }
}

try {
    $remoteBranchSha = $null

    if (-not (Test-Path (Join-Path $DeployDirectory ".git"))) {
        if ([string]::IsNullOrWhiteSpace($Branch)) {
            $Branch = "main"
        }

        if (Test-Path $DeployDirectory) {
            $entries = @(Get-ChildItem -Force $DeployDirectory)
            if ($entries.Count -gt 0) {
                throw "Deploy directory '$DeployDirectory' exists and is not empty."
            }
        }

        Write-Host "Cloning Store deployment checkout '$Branch' into $DeployDirectory..." -ForegroundColor Yellow
        Invoke-Git @("clone", "--branch", $Branch, "--single-branch", $repoUrl, $DeployDirectory)
        Set-Location $DeployDirectory
        $remoteBranchSha = (git rev-parse HEAD).Trim()
    }
    else {
        Set-Location $DeployDirectory

        if ([string]::IsNullOrWhiteSpace($Branch)) {
            $Branch = (git branch --show-current).Trim()
            if ([string]::IsNullOrWhiteSpace($Branch)) {
                throw "Could not determine the current Git branch."
            }
        }

        $remoteLine = (git ls-remote origin "refs/heads/$Branch").Trim()
        if ([string]::IsNullOrWhiteSpace($remoteLine)) {
            throw "Remote branch '$Branch' was not found."
        }

        $parts = $remoteLine -split "\s+"
        if ($parts.Count -lt 1 -or $parts[0] -notmatch "^[0-9a-f]{40}$") {
            throw "Could not determine the authoritative remote SHA for refs/heads/$Branch."
        }
        $remoteBranchSha = $parts[0].ToLowerInvariant()

        Assert-CleanCheckout
        Invoke-Git @("fetch", "origin", "refs/heads/$Branch")

        $currentBranch = (git branch --show-current).Trim()
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
            throw "Store checkout does not match remote/$Branch after fetch. Local=$postPullSha Remote=$remoteBranchSha"
        }
    }

    Set-Location $DeployDirectory

    if (-not (Test-Path ".env")) {
        throw "Missing .env. The Store deployment requires the existing Azure runtime environment file."
    }

    $commitSha = (git rev-parse HEAD).Trim()
    if ($commitSha -notmatch "^[0-9a-f]{40}$") {
        throw "Could not determine Store deployment commit SHA."
    }

    $composeConfig = @(
        "-f", "docker-compose.yml",
        "-f", "docker-compose.azure.yml",
        "-f", "docker-compose.store.yml",
        "--profile", "production",
        "-p", "mpay"
    )

    Write-Host ""
    Write-Host "mPay Store deployment" -ForegroundColor Green
    Write-Host "Branch       : $Branch"
    Write-Host "Commit       : $commitSha"
    Write-Host "Store Web    : ghcr.io/manav326/mpay-store-web:store-sha-$commitSha"

    $env:MPAY_STORE_WEB_IMAGE = "ghcr.io/manav326/mpay-store-web"
    $env:MPAY_STORE_WEB_TAG = "store-sha-$commitSha"
    $env:MPAY_STORE_CONTENT_DIR = Join-Path $DeployDirectory "store-content"

    New-Item -ItemType Directory -Force -Path (Join-Path $env:MPAY_STORE_CONTENT_DIR "ncert") | Out-Null

    Write-Host ""
    Write-Host "Validating Store Compose configuration..." -ForegroundColor Cyan
    & docker compose --env-file ".env" @composeConfig config | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Store Docker Compose configuration is invalid."
    }

    Write-Host "Pulling exact Store image only..." -ForegroundColor Cyan
    Invoke-Compose @("pull", "store-web")

    Write-Host "Starting Store container only..." -ForegroundColor Cyan
    Invoke-Compose @("up", "-d", "--no-deps", "store-web")

    $storeContainer = (& docker compose --env-file ".env" @composeConfig ps -q store-web).Trim()
    if ([string]::IsNullOrWhiteSpace($storeContainer)) {
        throw "Store container did not start."
    }

    $caddyContainer = (& docker compose --env-file ".env" @composeConfig ps -q caddy).Trim()
    if ([string]::IsNullOrWhiteSpace($caddyContainer)) {
        throw "Existing Caddy container is not running. Store deployment did not restart or create Caddy."
    }

    Write-Host "Reloading the existing Caddy configuration without restarting other services..." -ForegroundColor Cyan
    Invoke-CommandChecked "docker" @(
        "exec",
        $caddyContainer,
        "caddy",
        "reload",
        "--config",
        "/etc/caddy/Caddyfile",
        "--adapter",
        "caddyfile"
    )

    Write-Host ""
    Write-Host "Store deployment complete." -ForegroundColor Green
    Write-Host "Store URL : https://store.thinkwithsujeet.in"
    Write-Host ""
    Write-Host "Running Store/Caddy services:" -ForegroundColor DarkGray
    Invoke-Compose @("ps", "store-web", "caddy")
}
finally {
    Set-Location $originalLocation
}
