param(
  [string]$Branch = "",
  [switch]$ForceDownload
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

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
$safeBranch = ($Branch.ToLowerInvariant() -replace '[^a-z0-9_.-]', '-')
$artifactName = "mpay-android-$safeBranch"
$packageName = "com.recharge.client"
$headSha = (git rev-parse HEAD).Trim()

$targetDir = Join-Path $repoRoot "android\app\build\outputs\apk\debug"
$targetApk = Join-Path $targetDir "app-debug.apk"
$cacheCommitFile = "$targetApk.commit"
$tempDir = Join-Path $repoRoot ".android-apk-download"

Write-Host ""
Write-Host "mPay Android APK retrieval" -ForegroundColor Cyan
Write-Host "Branch    : $Branch"
Write-Host "Commit    : $headSha"
Write-Host "ADB       : $adbPath"
Write-Host ""

New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$useCachedApk = $false
$cachedCommit = ""

if (-not $ForceDownload -and (Test-Path $targetApk)) {
    if (Test-Path $cacheCommitFile) {
        $cachedCommit = (Get-Content $cacheCommitFile -Raw).Trim()
    }

    if ($cachedCommit -eq $headSha) {
        $useCachedApk = $true
        Write-Host "Cached APK matches the current commit. No GitHub download is needed." -ForegroundColor Green
    } elseif ([string]::IsNullOrWhiteSpace($cachedCommit)) {
        Write-Host "An APK already exists at the Gradle output location, but its source commit is unknown." -ForegroundColor Yellow
        Write-Host "This can happen for APKs downloaded by an older version of this helper." -ForegroundColor Yellow
        $reuseAnswer = Read-Host "Use this existing APK for the current commit without downloading again? (Y/N)"

        if ($reuseAnswer -match '^(Y|YES)$') {
            Set-Content -Path $cacheCommitFile -Value $headSha -Encoding UTF8
            $useCachedApk = $true
            Write-Host "Using the existing APK and recording it as the cached APK for $headSha." -ForegroundColor Green
        }
    } elseif (-not [string]::IsNullOrWhiteSpace($cachedCommit)) {
        Write-Host "A cached APK exists, but it belongs to commit $cachedCommit." -ForegroundColor Yellow
        Write-Host "The current commit is $headSha, so a fresh APK will be downloaded." -ForegroundColor Yellow
    }
}

if ($ForceDownload) {
    Write-Host "ForceDownload requested; the cached APK will be replaced." -ForegroundColor Yellow
}

try {
    if (-not $useCachedApk) {
        if (Test-Path $tempDir) {
            Remove-Item $tempDir -Recurse -Force
        }
        New-Item -ItemType Directory -Path $tempDir | Out-Null

        # Prefer GitHub CLI when available. If it is not installed, fall back to the
        # GitHub REST API using a token from the environment or Git Credential Manager.
        # This keeps the helper usable on machines that have Git configured but not gh.
        $githubToken = $env:GH_TOKEN
        if ([string]::IsNullOrWhiteSpace($githubToken)) {
            $githubToken = $env:GITHUB_TOKEN
        }

        if (-not (Get-Command gh -ErrorAction SilentlyContinue) -and [string]::IsNullOrWhiteSpace($githubToken)) {
            try {
                $credentialInput = "protocol=https" + [Environment]::NewLine + "host=github.com" + [Environment]::NewLine + [Environment]::NewLine
                $credentialOutput = $credentialInput | git credential fill 2>$null
                $credential = @{}
                foreach ($line in $credentialOutput) {
                    if ($line -match '^([^=]+)=(.*)$') {
                        $credential[$matches[1]] = $matches[2]
                    }
                }

                if ($credential.ContainsKey('password') -and -not [string]::IsNullOrWhiteSpace($credential['password'])) {
                    $githubToken = $credential['password']
                }
            } catch {
                # Credential Manager may not be configured; the explicit error below is clearer.
            }
        }

        if (Get-Command gh -ErrorAction SilentlyContinue) {
            Write-Host "Finding the latest Android artifact build for this branch..." -ForegroundColor Yellow
            $runJson = gh run list --workflow $workflow --branch $Branch --limit 30 --json databaseId,status,conclusion,headSha,createdAt,event
            if ($LASTEXITCODE -ne 0) {
                throw "Could not query GitHub Actions runs."
            }

            $runs = $runJson | ConvertFrom-Json

            $run = $runs | Sort-Object createdAt -Descending | Select-Object -First 1

            if (-not $run) {
                throw "No GitHub Actions run exists yet for branch $Branch."
            }

            $runId = [string]$run.databaseId
            $artifactZip = $null
        } else {
            if ([string]::IsNullOrWhiteSpace($githubToken)) {
                throw "GitHub CLI (gh) is not installed and no GitHub token could be obtained from GH_TOKEN, GITHUB_TOKEN, or Git Credential Manager. Install gh from https://cli.github.com/ and run 'gh auth login', or configure a GitHub token in one of those supported locations."
            }

            Write-Host "GitHub CLI is not installed; using GitHub REST API with the existing GitHub credential..." -ForegroundColor Yellow
            $apiHeaders = @{
                Authorization = "Bearer $githubToken"
                Accept = "application/vnd.github+json"
                "X-GitHub-Api-Version" = "2022-11-28"
            }

            $workflowPath = [uri]::EscapeDataString(".github/workflows/docker-compose.yml")
            $branchQuery = [uri]::EscapeDataString($Branch)
            $runsUrl = "https://api.github.com/repos/Manav326/mpay/actions/workflows/$workflowPath/runs?branch=$branchQuery&per_page=30"
            try {
                $runsResponse = Invoke-RestMethod -Uri $runsUrl -Headers $apiHeaders -Method Get
            } catch {
                throw "Could not query GitHub Actions through the REST API. Check that the GitHub credential used by Git is still valid and has access to Actions artifacts. $($_.Exception.Message)"
            }

            $run = $runsResponse.workflow_runs | Sort-Object created_at -Descending | Select-Object -First 1

            if (-not $run) {
                throw "No GitHub Actions run exists yet for branch $Branch."
            }

            $runId = [string]$run.id
            Write-Host "Found latest Actions run $runId for branch $Branch." -ForegroundColor Green

            $artifactsUrl = "https://api.github.com/repos/Manav326/mpay/actions/runs/$runId/artifacts?per_page=100"
            try {
                $artifactsResponse = Invoke-RestMethod -Uri $artifactsUrl -Headers $apiHeaders -Method Get
            } catch {
                throw "Could not list GitHub Actions artifacts for run $runId. $($_.Exception.Message)"
            }

            $artifact = $artifactsResponse.artifacts | Where-Object {
                $_.name -eq $artifactName -and -not $_.expired
            } | Sort-Object created_at -Descending | Select-Object -First 1

            if (-not $artifact) {
                throw "The successful run $runId does not have a non-expired '$artifactName' artifact."
            }

            $artifactZip = Join-Path $tempDir "$artifactName.zip"
            Write-Host "Downloading $artifactName from Actions run $runId..." -ForegroundColor Yellow
            try {
                Invoke-WebRequest -Uri "https://api.github.com/repos/Manav326/mpay/actions/artifacts/$($artifact.id)/zip" -Headers $apiHeaders -OutFile $artifactZip -UseBasicParsing
            } catch {
                throw "GitHub Actions artifact download failed for artifact $($artifact.id). $($_.Exception.Message)"
            }
        }

        if (Get-Command gh -ErrorAction SilentlyContinue) {
            Write-Host "Downloading $artifactName from Actions run $runId..." -ForegroundColor Yellow
            gh run download $runId --name $artifactName --dir $tempDir
            if ($LASTEXITCODE -ne 0) {
                throw "GitHub Actions artifact download failed for run $runId."
            }
        } else {
            Write-Host "Extracting the REST API artifact archive..." -ForegroundColor Yellow
            try {
                Expand-Archive -Path $artifactZip -DestinationPath $tempDir -Force
            } catch {
                throw "Could not extract the GitHub Actions artifact archive. $($_.Exception.Message)"
            }
        }

        $downloadedApk = Get-ChildItem -Path $tempDir -Filter "*.apk" -File -Recurse | Select-Object -First 1
        if (-not $downloadedApk) {
            throw "Downloaded artifact does not contain an APK."
        }

        Copy-Item $downloadedApk.FullName $targetApk -Force
        Set-Content -Path $cacheCommitFile -Value $headSha -Encoding UTF8

        $sourceRunId = $runId
    } else {
        $sourceRunId = "cached-local"
    }

    Write-Host ""
    Write-Host "APK ready:" -ForegroundColor Green
    Write-Host $targetApk -ForegroundColor Green
    Write-Host ""
    Write-Host "Source              : $sourceRunId"
    Write-Host "Commit recorded     : $headSha"
    Write-Host "Size                : $([math]::Round((Get-Item $targetApk).Length / 1MB, 2)) MB"
    Write-Host ""

    $installAnswer = Read-Host "Install this APK on a connected Android device now? (Y/N)"
    if ($installAnswer -notmatch '^(Y|YES)$') {
        Write-Host "APK is ready. Installation skipped." -ForegroundColor Yellow
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
