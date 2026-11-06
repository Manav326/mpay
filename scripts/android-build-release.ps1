[CmdletBinding()]
param(
    # Optional local/dev override. Leave blank for the canonical production URL
    # from config/public-hosts.env.
    [string]$ApiBaseUrl = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$publicHostConfigPath = Join-Path $repoRoot "config/public-hosts.env"
$publicConfigTool = Join-Path $PSScriptRoot "mpay-public-config.py"

Push-Location (Join-Path $repoRoot "android")
try {
    if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
        if (-not (Test-Path $publicHostConfigPath)) {
            throw "Missing $publicHostConfigPath. Configure the canonical mPay root URL before creating a production release."
        }
        $pythonCommand = if (Get-Command python3 -ErrorAction SilentlyContinue) { "python3" } elseif (Get-Command python -ErrorAction SilentlyContinue) { "python" } else { throw "Python 3 is required to render the shared mPay public URL configuration." }
        $output = & $pythonCommand $publicConfigTool --env-file $publicHostConfigPath --format json
        if ($LASTEXITCODE -ne 0) {
            throw "Could not derive the mPay production URLs from $publicHostConfigPath."
        }
        try {
            $publicConfig = ($output -join [Environment]::NewLine) | ConvertFrom-Json
        } catch {
            throw "The mPay public URL renderer returned invalid JSON: $($_.Exception.Message)"
        }
        $rootUrl = [string]$publicConfig.MPAY_PUBLIC_ROOT_URL
        if ([string]::IsNullOrWhiteSpace($rootUrl)) {
            throw "The renderer did not return MPAY_PUBLIC_ROOT_URL."
        }
        Write-Host "Building Android against canonical root $rootUrl" -ForegroundColor Cyan
        .\gradlew.bat :app:assembleRelease "-PmpayPublicRootUrl=$rootUrl"
    } else {
        Write-Warning "Using an explicit local/dev API override. This build will not follow config/public-hosts.env."
        .\gradlew.bat :app:assembleRelease "-PmpayApiBaseUrl=$ApiBaseUrl"
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle Android release build failed."
    }
} finally {
    Pop-Location
}
