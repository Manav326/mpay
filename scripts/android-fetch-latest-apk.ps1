param(
  [string]$Branch = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is required. Install it from https://cli.github.com/ and run 'gh auth login'."
}

$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $adbPath = $adbCommand.Source
    } else {
        throw "ADB was not found. Expected: $adbPath"
    }
}

if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = (git rev-parse --abbrev-ref HEAD).Trim()
}

if ([string]::IsNullOrWhiteSpace($Branch) -or $Branch -eq "HEAD") {
    throw "Could not determine the current Git branch."
}

$workflow = "Docker Compose CI"
$artifactName = "mpay-android-debug-apk"
$packageName = "com.recharge.client"
$headSha = (git rev-parse HEAD).Trim()

Write-Host ""
Write-Host "mPay Android APK retrieval" -ForegroundColor Cyan
Write-Host "Branch    : $Branch"
Write-Host "Commit    : $headSha"
Write-Host "ADB       : $adbPath"
Write-Host ""

Write-Host "Finding a successful Android artifact build for this exact commit..." -ForegroundColor Yellow
$runJson = gh run list --workflow $workflow --branch $Branch --limit 30 --json databaseId,status,conclusion,headSha,createdAt,event
if ($LASTEXITCODE -ne 0) {
    throw "Could not query GitHub Actions runs."
}

$runs = $runJson | ConvertFrom-Json

# The Android job is intentionally restricted to push events in Docker Compose CI.
# PR validation runs can be successful but do not produce the APK artifact.
$run = $runs | Where-Object {
    $_.headSha -eq $headSha -and
    $_.status -eq "completed" -and
    $_.conclusion -eq "success" -and
    $_.event -eq "push"
} | Sort-Object createdAt -Descending | Select-Object -First 1

if (-not $run) {
    throw "No successful push build with an Android APK artifact exists yet for commit $headSha."
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
    Write-Host "Downloading $artifactName from Actions run $($run.databaseId)..." -ForegroundColor Yellow
    gh run download $run.databaseId --name $artifactName --dir $tempDir
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub Actions artifact download failed for run $($run.databaseId)."
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

    $installAnswer = Read-Host "Install this APK on a connected Android device now? (Y/N)"
    if ($installAnswer -notmatch '^(Y|YES)$') {
        Write-Host "APK download complete. Installation skipped." -ForegroundColor Yellow
        exit 0
    }

    Write-Host "Checking connected Android devices..." -ForegroundColor Yellow
    $deviceLines = & $adbPath devices 2>$null
    $devices = @(
        $deviceLines | ForEach-Object {
            if ($_ -match '^(\S+)\s+(device)$') {
                [PSCustomObject]@{
                    Serial = $matches[1]
                    State = $matches[2]
                }
            }
        }
    )

    if ($devices.Count -eq 0) {
        Write-Host ""
        Write-Host "No Android device is currently available to ADB." -ForegroundColor Red
        Write-Host "APK remains here: $targetApk"
        exit 0
    }

    $selectedSerial = $null

    if ($devices.Count -eq 1) {
        $selectedSerial = $devices[0].Serial
        Write-Host "One device found: $selectedSerial" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "Multiple Android devices found:" -ForegroundColor Cyan
        for ($i = 0; $i -lt $devices.Count; $i++) {
            Write-Host ("[{0}] {1}" -f ($i + 1), $devices[$i].Serial)
        }

        do {
            $choiceText = Read-Host "Choose the device number"
            $choice = 0
            $valid = [int]::TryParse($choiceText, [ref]$choice) -and $choice -ge 1 -and $choice -le $devices.Count

            if (-not $valid) {
                Write-Host "Invalid choice. Enter a number from 1 to $($devices.Count)." -ForegroundColor Yellow
            }
        } while (-not $valid)

        $selectedSerial = $devices[$choice - 1].Serial
    }

    Write-Host ""
    Write-Host "Installing APK on $selectedSerial..." -ForegroundColor Yellow
    $installOutput = @(& $adbPath -s $selectedSerial install -r $targetApk 2>&1)
    $installExitCode = $LASTEXITCODE
    $installOutput | ForEach-Object { Write-Host $_ }

    if ($installExitCode -ne 0) {
        $installText = $installOutput -join [Environment]::NewLine

        if ($installText -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match") {
            Write-Host ""
            Write-Host "The existing $packageName app is signed with a different key." -ForegroundColor Yellow
            Write-Host "Android requires the existing app to be uninstalled before this CI-signed APK can be installed." -ForegroundColor Yellow
            Write-Host "Uninstalling the existing app removes its local app data." -ForegroundColor Yellow

            $uninstallAnswer = Read-Host "Uninstall $packageName and install the CI APK now? (Y/N)"
            if ($uninstallAnswer -notmatch '^(Y|YES)$') {
                throw "Installation stopped because the existing app uses a different signing key."
            }

            Write-Host "Uninstalling $packageName..." -ForegroundColor Yellow
            & $adbPath -s $selectedSerial uninstall $packageName
            if ($LASTEXITCODE -ne 0) {
                throw "Could not uninstall $packageName from device $selectedSerial."
            }

            Write-Host "Installing CI APK..." -ForegroundColor Yellow
            & $adbPath -s $selectedSerial install $targetApk
            if ($LASTEXITCODE -ne 0) {
                throw "APK installation failed on device $selectedSerial after uninstall."
            }
        } else {
            throw "APK installation failed on device $selectedSerial."
        }
    }

    Write-Host ""
    Write-Host "mPay APK installed successfully on $selectedSerial." -ForegroundColor Green
} finally {
    if (Test-Path $tempDir) {
        Remove-Item $tempDir -Recurse -Force
    }
}
