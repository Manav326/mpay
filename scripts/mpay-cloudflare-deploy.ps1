[CmdletBinding()]
param(
    [string]$EnvFile = ".env.cloudflare",
    [string]$ComposeProject = "mpay-production",
    [string]$CloudflareNetwork = "cloudflare-edge",
    [string]$CloudflareConnectorContainer = "quantaedge-ingress-cloudflared",
    [string]$RestoreBackup = "",
    [switch]$AllowFreshInstall,
    [switch]$ConfirmRestoreOverwrite,
    [switch]$SkipPublicSmokeTest
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$originalLocation = (Get-Location).Path
Set-Location $repoRoot

$envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $repoRoot $EnvFile }
if (-not (Test-Path $envPath)) {
    throw "Missing $envPath. Copy .env.cloudflare.example to .env.cloudflare and fill in every production value first."
}
$overlayPath = Join-Path $repoRoot "docker-compose.cloudflare.yml"
$secretPath = $null
$backupScript = Join-Path $PSScriptRoot "mpay-db-backup.ps1"
$restoreScript = Join-Path $PSScriptRoot "mpay-db-restore.ps1"

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed: $($Arguments -join ' ')"
    }
}

function Invoke-Git {
    param([string[]]$Arguments)
    Invoke-Checked "git" $Arguments
}

function Get-EnvValue {
    param([string]$Name)
    $pattern = "^\s*" + [regex]::Escape($Name) + "\s*=\s*(.*)$"
    $line = Get-Content -Path $envPath | Where-Object { $_ -match $pattern } | Select-Object -Last 1
    if ($null -eq $line) { return "" }
    $value = [regex]::Match($line, $pattern).Groups[1].Value.Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    return $value
}

# Derive every public origin from the one tracked, non-secret hostname contract.
# GitHub builds, Android builds and this local deployment all read this exact file.
$publicHostConfigPath = Join-Path $repoRoot "config/public-hosts.env"
if (-not (Test-Path $publicHostConfigPath)) {
    throw "Missing $publicHostConfigPath. Restore the tracked single-source mPay hostname configuration."
}
$publicConfigTool = Join-Path $PSScriptRoot "mpay-public-config.py"
$publicConfigOutput = & python $publicConfigTool --env-file $publicHostConfigPath --format json
if ($LASTEXITCODE -ne 0) {
    throw "Could not derive mPay public URLs from $publicHostConfigPath."
}
try {
    $script:publicConfig = ($publicConfigOutput -join [Environment]::NewLine) | ConvertFrom-Json
} catch {
    throw "The mPay public URL renderer returned invalid JSON: $($_.Exception.Message)"
}
foreach ($property in $script:publicConfig.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, "Process")
}

function Get-ProductionConfigDirectory {
    $configured = Get-EnvValue "MPAY_PRODUCTION_CONFIG_DIR"
    if ([string]::IsNullOrWhiteSpace($configured)) { $configured = "./backend/config/cloudflare-production" }
    if ([System.IO.Path]::IsPathRooted($configured)) { return $configured }
    return Join-Path $repoRoot $configured
}

$secretPath = Join-Path (Get-ProductionConfigDirectory) "application-secrets.yml"

function Assert-ProductionEnvironment {
    if (-not (Test-Path $envPath)) {
        throw "Missing $envPath. Copy .env.cloudflare.example to .env.cloudflare and fill in every production value first."
    }
    if (-not (Test-Path $secretPath)) {
        throw "Missing $secretPath. Configure production-only backend credentials before deployment."
    }

    $databasePassword = Get-EnvValue "MPAY_DB_PASSWORD"
    if ($databasePassword.Length -lt 24 -or $databasePassword -match "(?i)replace|recharge|password|changeme") {
        throw "MPAY_DB_PASSWORD must be a unique random value with at least 24 characters; the example/default value is not accepted."
    }

    $paymentProviders = (Get-EnvValue "MPAY_PAYMENT_GATEWAY_PROVIDERS").ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($paymentProviders) -or $paymentProviders -match "mock" -or $paymentProviders -notmatch "razorpay|payu") {
        throw "MPAY_PAYMENT_GATEWAY_PROVIDERS must select at least one real Razorpay/PayU gateway and must not include mock."
    }
    $withdrawalProviders = (Get-EnvValue "MPAY_WITHDRAWAL_PROVIDER_ORDER").ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($withdrawalProviders) -or $withdrawalProviders -match "mock" -or $withdrawalProviders -notmatch "razorpay|payu") {
        throw "MPAY_WITHDRAWAL_PROVIDER_ORDER must select real Razorpay/PayU payout providers and must not include mock."
    }
    $rechargeProviders = (Get-EnvValue "MPAY_RECHARGE_EXECUTION_PROVIDERS").ToLowerInvariant()
    if ($rechargeProviders -notmatch "(^|,)\\s*payu\\s*(,|$)" -or $rechargeProviders -match "mock") {
        throw "MPAY_RECHARGE_EXECUTION_PROVIDERS must include the implemented live PayU recharge execution provider and must not include mock."
    }
    $otpProvider = (Get-EnvValue "MPAY_AUTH_OTP_PROVIDER").ToLowerInvariant()
    if ($otpProvider -ne "way2api") {
        throw "MPAY_AUTH_OTP_PROVIDER must be way2api; Twilio is only used by the separate password-reset OTP flow."
    }

    $webApiOrigin = [string]$script:publicConfig.MPAY_API_PUBLIC_URL
    if ([string]::IsNullOrWhiteSpace($webApiOrigin)) {
        throw "The generated mPay API origin is missing; check MPAY_PUBLIC_ROOT_URL."
    }
    $corsOrigins = @([string]$script:publicConfig.MPAY_CORS_ALLOWED_ORIGINS)
    if ($corsOrigins -notcontains [string]$script:publicConfig.MPAY_WEB_PUBLIC_URL) {
        throw "The generated mPay CORS origin does not match the public web origin."
    }

    if ((Get-EnvValue "MPAY_PAYU_ENVIRONMENT").ToLowerInvariant() -ne "production") {
        throw "MPAY_PAYU_ENVIRONMENT must be set to production. UAT/test credentials are not accepted for public deployment."
    }
    if ((Get-EnvValue "MPAY_PAYU_PG_PRODUCTION").ToLowerInvariant() -ne "true") {
        throw "MPAY_PAYU_PG_PRODUCTION must be true only after live PayU credentials are approved and configured."
    }

    $razorpayPayoutEnabled = (Get-EnvValue "MPAY_RAZORPAY_PAYOUT_ENABLED").ToLowerInvariant() -eq "true"
    $payuPayoutEnabled = (Get-EnvValue "MPAY_PAYU_PAYOUT_ENABLED").ToLowerInvariant() -eq "true"
    if (-not $razorpayPayoutEnabled -and -not $payuPayoutEnabled) {
        throw "At least one live payout provider must be configured and enabled. Mock withdrawals are disabled; do not deploy a customer withdrawal flow that cannot execute."
    }
    if ($razorpayPayoutEnabled -and $withdrawalProviders -notmatch "(^|,)\\s*razorpay\\s*(,|$)") {
        throw "Razorpay payouts are enabled but razorpay is missing from MPAY_WITHDRAWAL_PROVIDER_ORDER."
    }
    if ($payuPayoutEnabled -and $withdrawalProviders -notmatch "(^|,)\\s*payu\\s*(,|$)") {
        throw "PayU payouts are enabled but payu is missing from MPAY_WITHDRAWAL_PROVIDER_ORDER."
    }
    if ($razorpayPayoutEnabled) {
        foreach ($name in @(
            "MPAY_RAZORPAY_PAYOUT_KEY_ID",
            "MPAY_RAZORPAY_PAYOUT_KEY_SECRET",
            "MPAY_RAZORPAY_PAYOUT_ACCOUNT_NUMBER",
            "MPAY_RAZORPAY_PAYOUT_CONTACT_ID",
            "MPAY_RAZORPAY_PAYOUT_WEBHOOK_SECRET"
        )) {
            $value = Get-EnvValue $name
            if ([string]::IsNullOrWhiteSpace($value) -or $value -match "(?i)replace|test|your_") {
                throw "$name must contain the approved live RazorpayX payout configuration."
            }
        }
    }
    if ($payuPayoutEnabled) {
        if ((Get-EnvValue "MPAY_PAYU_PAYOUT_ENVIRONMENT").ToLowerInvariant() -ne "production") {
            throw "MPAY_PAYU_PAYOUT_ENVIRONMENT must be production when PayU payout is enabled."
        }
        foreach ($name in @(
            "MPAY_PAYU_PAYOUT_CLIENT_ID",
            "MPAY_PAYU_PAYOUT_CLIENT_SECRET",
            "MPAY_PAYU_PAYOUT_MERCHANT_ID",
            "MPAY_PAYU_PAYOUT_WEBHOOK_AUTHORIZATION"
        )) {
            $value = Get-EnvValue $name
            if ([string]::IsNullOrWhiteSpace($value) -or $value -match "(?i)replace|test|uat|your_") {
                throw "$name must contain the approved live PayU payout configuration."
            }
        }
        foreach ($name in @("MPAY_PAYU_PAYOUT_AUTH_BASE_URL", "MPAY_PAYU_PAYOUT_API_BASE_URL")) {
            $value = Get-EnvValue $name
            $uri = $null
            if (-not [Uri]::TryCreate($value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne "https" -or $value -match "(?i)replace|test|uat|sandbox") {
                throw "$name must be the approved live HTTPS PayU payout endpoint."
            }
        }
    }

    foreach ($name in @(
        "MPAY_PAYU_AUTH_BASE_URL",
        "MPAY_PAYU_NBC_BASE_URL",
        "MPAY_PAYU_PG_VERIFY_URL",
        "MPAY_PAYU_PG_SUCCESS_URL",
        "MPAY_PAYU_PG_FAILURE_URL"
    )) {
        $value = Get-EnvValue $name
        $uri = $null
        if (-not [Uri]::TryCreate($value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne "https" -or $value -match "(?i)replace|uat|sandbox|test") {
            throw "$name must be the approved HTTPS production URL; UAT/test/placeholder URLs are blocked."
        }
    }

    foreach ($name in @("MPAY_BACKEND_BIND_ADDRESS", "MPAY_ADMIN_WEB_BIND_ADDRESS")) {
        $value = (Get-EnvValue $name).ToLowerInvariant()
        if ($value -notin @("127.0.0.1", "localhost")) {
            throw "$name must bind to loopback only. Public traffic must enter through the existing Cloudflare Tunnel."
        }
    }

    $secrets = Get-Content -Path $secretPath -Raw
    if ($secrets -match "(?i)YOUR_|REPLACE_WITH|CHANGEME|demo-otp-enabled\s*:\s*true|provider\s*:\s*mock|execution-provider\s*:\s*mock|environment\s*:\s*(test|sandbox|uat)") {
        throw "backend/config/application-secrets.yml still contains a placeholder, mock, demo-OTP, or test/sandbox setting."
    }
    $jwtMatch = [regex]::Match($secrets, '(?m)^\s*jwt-secret\s*:\s*(.+)$')
    $jwtValue = if ($jwtMatch.Success) { $jwtMatch.Groups[1].Value.Trim().Trim('"').Trim("'") } else { "" }
    if ($jwtValue.Length -lt 32) {
        throw "The backend secrets file must contain a unique app.security.jwt-secret of at least 32 characters."
    }
    foreach ($key in @("api-key", "key-id", "key-secret", "client-id", "client-secret", "agent-id", "merchant-key", "merchant-salt")) {
        $settingMatch = [regex]::Match($secrets, "(?m)^\s*" + [regex]::Escape($key) + "\s*:\s*(.+)$")
        $settingValue = if ($settingMatch.Success) { $settingMatch.Groups[1].Value.Trim().Trim('"').Trim("'") } else { "" }
        if ([string]::IsNullOrWhiteSpace($settingValue)) {
            throw "The backend secrets file is missing a non-empty provider setting: $key."
        }
    }
}

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose @script:composeBaseArgs @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed: $($Arguments -join ' ')"
    }
}

function Test-DockerVolume {
    param([string]$Name)
    & docker volume inspect $Name *> $null
    return ($LASTEXITCODE -eq 0)
}

try {
    if (-not (Test-Path $overlayPath)) { throw "Missing docker-compose.cloudflare.yml." }

    $branchName = (git branch --show-current).Trim()
    if ($branchName -ne "main") {
        throw "Production deployment must run from main. Current branch: '$branchName'. Merge the tested feature PR and switch to main first."
    }

    Invoke-Git @("fetch", "origin", "main")
    $workingChanges = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect the Git working tree." }
    if ($workingChanges.Count -gt 0) {
        throw "Working tree is not clean. Commit or stash changes before production deployment."
    }

    $commitSha = (git rev-parse HEAD).Trim()
    $remoteSha = (git rev-parse origin/main).Trim()
    if ($commitSha -notmatch "^[0-9a-f]{40}$" -or $commitSha -ne $remoteSha) {
        throw "Local main is not exactly synchronized with origin/main. Run git pull --ff-only origin main and retry."
    }

    Assert-ProductionEnvironment

    $networkJson = & docker network inspect $CloudflareNetwork 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker network '$CloudflareNetwork' does not exist. Do not create another tunnel; verify the existing Cloudflare connector/network first."
    }
    $networkInfo = ($networkJson -join [Environment]::NewLine) | ConvertFrom-Json
    $networkMembers = @()
    if ($networkInfo.Count -gt 0 -and $networkInfo[0].Containers) {
        $networkMembers = @($networkInfo[0].Containers.PSObject.Properties | ForEach-Object { $_.Value.Name })
    }
    if ($networkMembers -notcontains $CloudflareConnectorContainer) {
        throw "The existing connector '$CloudflareConnectorContainer' is not attached to '$CloudflareNetwork'. No connector/network changes were made."
    }

    # mPay's product-qualified DNS aliases must not be claimed by another Compose project.
    $requiredIngressAliases = @("mpay-api", "mpay-admin-web")
    if ($networkInfo.Count -gt 0 -and $networkInfo[0].Containers) {
        foreach ($member in $networkInfo[0].Containers.PSObject.Properties) {
            $memberInspection = & docker inspect $member.Name 2>$null
            if ($LASTEXITCODE -ne 0) {
                throw "Could not inspect a member of '$CloudflareNetwork'; refusing deployment without an alias-collision check."
            }
            $memberContainer = (($memberInspection -join [Environment]::NewLine) | ConvertFrom-Json)[0]
            $networkEntry = $memberContainer.NetworkSettings.Networks.PSObject.Properties[$CloudflareNetwork]
            if ($null -eq $networkEntry) { continue }
            $memberAliases = @($networkEntry.Value.Aliases)
            $ownerProject = [string]$memberContainer.Config.Labels.'com.docker.compose.project'
            foreach ($alias in $requiredIngressAliases) {
                if ($memberAliases -contains $alias -and $ownerProject -ne $ComposeProject) {
                    throw "Shared-network DNS alias '$alias' is already owned by container '$($memberContainer.Name)' in Compose project '$ownerProject'. Resolve that collision before deploying mPay."
                }
            }
        }
    }

    $env:MPAY_IMAGE_TAG = "sha-$commitSha"
    $env:MPAY_ADMIN_WEB_TAG = "azure-sha-$commitSha"
    $env:MPAY_BACKEND_IMAGE = "ghcr.io/manav326/mpay-backend"
    $env:MPAY_ADMIN_WEB_IMAGE = "ghcr.io/manav326/mpay-admin-web"

    $script:composeBaseArgs = @(
        "--env-file", $envPath,
        "-f", "docker-compose.yml",
        "-f", "docker-compose.cloudflare.yml",
        "-p", $ComposeProject
    )

    Write-Host ""
    Write-Host "mPay local + Cloudflare production deployment" -ForegroundColor Cyan
    Write-Host "Branch / commit : main / $commitSha" -ForegroundColor Cyan
    Write-Host "API image       : ghcr.io/manav326/mpay-backend:sha-$commitSha" -ForegroundColor Cyan
    Write-Host "Admin Web image : ghcr.io/manav326/mpay-admin-web:azure-sha-$commitSha" -ForegroundColor Cyan
    Write-Host "Tunnel network  : $CloudflareNetwork (existing connector verified)" -ForegroundColor Cyan
    Write-Host ""

    Invoke-Compose @("config", "--quiet")
    $resolvedImages = @(& docker compose @script:composeBaseArgs config --images)
    if ($LASTEXITCODE -ne 0) { throw "Could not resolve production image references." }
    if ($resolvedImages -notcontains "ghcr.io/manav326/mpay-backend:sha-$commitSha") {
        throw "Compose did not resolve the backend image to this exact main commit."
    }
    if ($resolvedImages -notcontains "ghcr.io/manav326/mpay-admin-web:azure-sha-$commitSha") {
        throw "Compose did not resolve the Admin Web image to this exact main commit."
    }

    $postgresVolume = "mpay_production_postgres_data"
    $postgresVolumeExists = Test-DockerVolume $postgresVolume
    $restoreProvided = -not [string]::IsNullOrWhiteSpace($RestoreBackup)

    if ($restoreProvided -and -not (Test-Path $RestoreBackup)) {
        throw "Restore backup directory not found: $RestoreBackup"
    }
    if ($restoreProvided -and $postgresVolumeExists -and -not $ConfirmRestoreOverwrite) {
        throw "The production database volume already exists. Pass -ConfirmRestoreOverwrite only after verifying the selected backup; the restore replaces database contents."
    }
    if (-not $postgresVolumeExists -and -not $restoreProvided -and -not $AllowFreshInstall) {
        throw "No production database volume exists. Supply -RestoreBackup <verified-backup-folder>, or explicitly confirm a genuinely new install with -AllowFreshInstall."
    }

    $existingPostgres = (& docker compose @script:composeBaseArgs ps -q postgres 2>$null).Trim()
    $existingApi = (& docker compose @script:composeBaseArgs ps -q mpay-api 2>$null).Trim()
    if ($postgresVolumeExists -and -not $restoreProvided) {
        if ([string]::IsNullOrWhiteSpace($existingPostgres) -or [string]::IsNullOrWhiteSpace($existingApi)) {
            throw "The production database volume exists but its current mPay API/PostgreSQL stack is not running. Start it and take a verified backup before updating; the script will not guess at the data state."
        }
    }

    Write-Host "Pulling immutable images for this exact main commit..." -ForegroundColor Yellow
    Invoke-Compose @("pull", "mpay-api", "mpay-admin-web")

    if ($postgresVolumeExists -and -not [string]::IsNullOrWhiteSpace($existingApi)) {
        try {
            Write-Host "Briefly stopping mPay Web/API to freeze database and media writes..." -ForegroundColor Yellow
            Invoke-Compose @("stop", "mpay-api", "mpay-admin-web")
            Write-Host "Creating a consistent database + profile/rental-media backup before the update..." -ForegroundColor Yellow
            & $backupScript -ComposeProject $ComposeProject -ComposeOverlay $overlayPath -EnvFile $envPath -BackupDirectory (Join-Path $repoRoot "backups/cloudflare-production")
        } catch {
            try {
                Invoke-Compose @("start", "mpay-api", "mpay-admin-web")
            } catch {
                Write-Warning "The pre-update backup failed and the previous application containers could not be restarted automatically. Inspect docker compose ps/logs."
            }
            throw "Automatic pre-deployment backup failed. The update was not started. $($_.Exception.Message)"
        }
    }

    Write-Host "Starting production PostgreSQL and Redis..." -ForegroundColor Yellow
    Invoke-Compose @("up", "-d", "postgres", "redis")

    if ($restoreProvided) {
        Write-Host "Restoring the verified database and media backup..." -ForegroundColor Yellow
        try {
            & $restoreScript -BackupDirectory $RestoreBackup -ComposeProject $ComposeProject -ComposeOverlay $overlayPath -EnvFile $envPath
        } catch {
            throw "Database/media restore failed. The backup files and persistent volumes have been preserved. $($_.Exception.Message)"
        }
    } else {
        Write-Host "Starting mPay API and Admin Web without rebuilding local images..." -ForegroundColor Yellow
        Invoke-Compose @("up", "-d", "--no-build", "mpay-api", "mpay-admin-web")
    }

    $backendPort = Get-EnvValue "MPAY_BACKEND_HOST_PORT"
    if ([string]::IsNullOrWhiteSpace($backendPort)) { $backendPort = "18080" }
    $webPort = Get-EnvValue "MPAY_ADMIN_WEB_HOST_PORT"
    if ([string]::IsNullOrWhiteSpace($webPort)) { $webPort = "13000" }

    $backendReady = $false
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/actuator/health" -TimeoutSec 5
            if ($health.status -eq "UP") { $backendReady = $true; break }
        } catch { }
        Start-Sleep -Seconds 5
    }
    if (-not $backendReady) {
        Invoke-Compose @("ps")
        throw "mPay API did not reach /actuator/health UP. The pre-deployment backup is retained; inspect container logs before attempting rollback."
    }

    try {
        $webResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$webPort/" -UseBasicParsing -TimeoutSec 20
        if ($webResponse.StatusCode -lt 200 -or $webResponse.StatusCode -ge 400) {
            throw "Admin Web returned HTTP $($webResponse.StatusCode)."
        }
        $androidRelease = Invoke-RestMethod -Uri "http://127.0.0.1:$webPort/api/download/android/metadata" -TimeoutSec 20
        if ($androidRelease.available -ne $true -or [int64]$androidRelease.sizeBytes -le 0) {
            throw "The latest signed Android APK is not published yet. Wait for the main-branch Android release workflow to finish before enabling the public website."
        }
        Write-Host "Android release: v$($androidRelease.version), $([math]::Round($androidRelease.sizeBytes / 1MB, 1)) MB" -ForegroundColor Cyan
    } catch {
        throw "Admin Web local smoke test failed: $($_.Exception.Message)"
    }

    if (-not $SkipPublicSmokeTest) {
        $webOrigin = [string]$script:publicConfig.MPAY_WEB_PUBLIC_URL
        $apiOrigin = [string]$script:publicConfig.MPAY_API_PUBLIC_URL
        try {
            $null = Invoke-WebRequest -Uri $webOrigin -UseBasicParsing -TimeoutSec 25
            $publicHealth = Invoke-RestMethod -Uri "$apiOrigin/actuator/health" -TimeoutSec 25
            if ($publicHealth.status -ne "UP") { throw "Public API health did not return UP." }
            $publicAndroidRelease = Invoke-RestMethod -Uri "$webOrigin/api/download/android/metadata" -TimeoutSec 25
            if ($publicAndroidRelease.available -ne $true -or [int64]$publicAndroidRelease.sizeBytes -le 0) {
                throw "Public homepage cannot resolve the signed Android release asset."
            }
        } catch {
            throw "Local containers are healthy, but the public Cloudflare route smoke test failed. Verify Tunnel hostnames point to mpay.quantaedge.in → mpay-admin-web:3000 and api-mpay.quantaedge.in → mpay-api:8080, then rerun the script. $($_.Exception.Message)"
        }
    }

    Write-Host ""
    Invoke-Compose @("ps")
    Write-Host ""
    Write-Host "mPay local production services are healthy." -ForegroundColor Green
    Write-Host "Public Web : $($script:publicConfig.MPAY_WEB_PUBLIC_URL)" -ForegroundColor Green
    Write-Host "Public API : $($script:publicConfig.MPAY_API_PUBLIC_URL)" -ForegroundColor Green
    Write-Host "TURN host  : $($script:publicConfig.MPAY_TURN_HOST) (direct UDP/TCP; not an HTTP Tunnel route)" -ForegroundColor Green
    Write-Host "Local Web  : http://127.0.0.1:$webPort" -ForegroundColor DarkGray
    Write-Host "Local API  : http://127.0.0.1:$backendPort" -ForegroundColor DarkGray
    Write-Host "Commit     : $commitSha" -ForegroundColor Green
    Write-Host "No global Docker prune, volume deletion, or Cloudflare connector modification was performed." -ForegroundColor Green
}
finally {
    Set-Location $originalLocation
}
