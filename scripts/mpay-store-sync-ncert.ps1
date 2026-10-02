param(
  [string]$DeployDirectory = "/opt/mpay",
  [string]$ManifestPath = "",
  [switch]$Force
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
  $ManifestPath = Join-Path $DeployDirectory "scripts/mpay-store-ncert-assets.json"
}

$ContentRoot = Join-Path $DeployDirectory "store-content"
$NcertRoot = Join-Path $ContentRoot "ncert"
$InstalledManifestPath = Join-Path $ContentRoot "ncert-assets.installed.json"

if (-not (Test-Path $ManifestPath)) {
  throw "NCERT asset manifest was not found: $ManifestPath"
}

$manifest = Get-Content -Raw -Path $ManifestPath | ConvertFrom-Json
if ($manifest.version -ne 1) {
  throw "Unsupported NCERT asset manifest version: $($manifest.version)"
}

New-Item -ItemType Directory -Force -Path $NcertRoot | Out-Null
$results = @()

foreach ($book in $manifest.books) {
  $destination = Join-Path $NcertRoot $book.filename
  $temporary = "$destination.part"

  Write-Host ""
  Write-Host "NCERT: $($book.title)" -ForegroundColor Cyan
  Write-Host "Source: $($book.url)"
  Write-Host "Target: $destination"

  if ((Test-Path $destination) -and -not $Force) {
    $existing = Get-Item $destination
    if ($existing.Length -ge [int64]$book.minBytes) {
      Write-Host "Already installed; keeping existing asset." -ForegroundColor DarkGray
      $results += [pscustomobject]@{
        slug = $book.slug
        title = $book.title
        filename = $book.filename
        bytes = $existing.Length
        sha256 = (Get-FileHash -Algorithm SHA256 -Path $destination).Hash.ToLowerInvariant()
        status = "existing"
      }
      continue
    }
    Remove-Item -Force $destination
  }

  if (Test-Path $temporary) { Remove-Item -Force $temporary }

  $requestHeaders = @{
    "Accept" = "application/pdf,*/*"
    "User-Agent" = "mPayStudyAssetSync/1.0 (+https://store.thinkwithsujeet.in)"
  }
  Invoke-WebRequest -Uri $book.url -Headers $requestHeaders -OutFile $temporary

  $downloaded = Get-Item $temporary
  if ($downloaded.Length -lt [int64]$book.minBytes) {
    Remove-Item -Force $temporary
    throw "Downloaded asset is unexpectedly small for $($book.slug): $($downloaded.Length) bytes."
  }

  $stream = [System.IO.File]::OpenRead($temporary)
  try {
    $magic = New-Object byte[] 5
    [void]$stream.Read($magic, 0, 5)
  } finally {
    $stream.Dispose()
  }

  $signature = [System.Text.Encoding]::ASCII.GetString($magic)
  if ($signature -ne "%PDF-") {
    Remove-Item -Force $temporary
    throw "Downloaded $($book.slug) is not a PDF (signature: $signature)."
  }

  Move-Item -Force $temporary $destination
  $final = Get-Item $destination
  $hash = (Get-FileHash -Algorithm SHA256 -Path $destination).Hash.ToLowerInvariant()

  $results += [pscustomobject]@{
    slug = $book.slug
    title = $book.title
    filename = $book.filename
    bytes = $final.Length
    sha256 = $hash
    status = "downloaded"
  }

  Write-Host "Installed $($final.Length) bytes ($hash)." -ForegroundColor Green
}

$installed = [pscustomobject]@{
  manifestVersion = $manifest.version
  installedAtUtc = [DateTime]::UtcNow.ToString("o")
  source = $manifest.source
  books = $results
}

$installed | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -Path $InstalledManifestPath

Write-Host ""
Write-Host "NCERT Store assets are ready in:" -ForegroundColor Green
Write-Host "  $NcertRoot"
Write-Host "Installed manifest:" -ForegroundColor Green
Write-Host "  $InstalledManifestPath"
