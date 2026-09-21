param(
  [string]$Branch = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is required. Install it from https://cli.github.com/ and run 'gh auth login'."
}

if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = (git rev-parse --abbrev-ref HEAD).Trim()
}

if ([string]::IsNullOrWhiteSpace($Branch) -or $Branch -eq "HEAD") {
    throw "Could not determine the current Git branch."
}

$workflow = ".github/workflows/android-apk.yml"
$headSha = (git rev-parse HEAD).Trim()

Write-Host ""
Write-Host "mPay Android APK retrieval" -ForegroundColor Cyan
Write-Host "Branch    : $Branch"
Write-Host "Commit    : $headSha"
Write-Host ""

Write-Host "Finding a successful GitHub Actions build for this exact commit..." -ForegroundColor Yellow
$runJson = gh run list --workflow $workflow --branch $Branch --limit 30 --json databaseId,status,conclusion,headSha,createdAt
if ($LASTEXITCODE -ne 0) {
    throw "Could not query GitHub Actions runs."
}

$runs = $runJson | ConvertFrom-Json
$run = $runs | Where-Object {
    $_.headSha -eq $headSha -and
    $_.status -eq "completed" -and
    $_.conclusion -eq "success"
} | Sort-Object createdAt -Descending | Select-Object -First 1

if (-not $run) {
    throw "No successful Android APK build exists yet for commit $headSha. Push the commit and wait for the Android APK CI workflow to finish successfully."
}

$targetDir = Join-Path $repoRoot "android\app\build\outputs\apk\debug"
$targetApk = Join-Path $targetDir "app-debug.apk"
$tempDir = Join-Path $repoRoot ".android-apk-download"

if (Test-Path $tempDir) {
    Remove-Item $tempDir -Recurse -Force
}
New-Item -ItemType Directory -Path $tempDir | Out-Null
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

try {
    Write-Host "Downloading artifact from Actions run $($run.databaseId)..." -ForegroundColor Yellow
    gh run download $run.databaseId --name mpay-android-debug-apk --dir $tempDir
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub Actions artifact download failed."
    }

    $downloadedApk = Get-ChildItem -Path $tempDir -Filter "*.apk" -File -Recurse | Select-Object -First 1
    if (-not $downloadedApk) {
        throw "Downloaded artifact does not contain an APK."
    }

    Copy-Item $downloadedApk.FullName $targetApk -Force

    Write-Host ""
    Write-Host "APK ready:" -ForegroundColor Green
    Write-Host $targetApk -ForegroundColor Green
    Write-Host ""
    Write-Host "GitHub Actions run: $($run.databaseId)"
    Write-Host "Commit             : $($run.headSha)"
    Write-Host "Size               : $([math]::Round((Get-Item $targetApk).Length / 1MB, 2)) MB"
    Write-Host ""
    Write-Host "Install directly with ADB using:" -ForegroundColor Cyan
    Write-Host ('adb install -r "' + $targetApk + '"')
} finally {
    if (Test-Path $tempDir) {
        Remove-Item $tempDir -Recurse -Force
    }
}
