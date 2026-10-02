param(
  [Parameter(Mandatory = $true)]
  [string]$SourceDirectory,
  [string]$Image = "ghcr.io/manav326/mpay-store-content",
  [string]$Tag = "store-content-current",
  [string]$ManifestPath = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SourceDirectory -PathType Container)) {
  throw "Source directory does not exist: $SourceDirectory"
}

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
  $ManifestPath = Join-Path $PSScriptRoot "mpay-store-ncert-assets.json"
}

if (-not (Test-Path $ManifestPath)) {
  throw "Manifest not found: $ManifestPath"
}

$manifest = Get-Content -Raw -Path $ManifestPath | ConvertFrom-Json
if ($manifest.version -ne 1) {
  throw "Unsupported manifest version: $($manifest.version)"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mpay-store-content-" + [guid]::NewGuid().ToString("N"))
$seedRoot = Join-Path $tempRoot "ncert"

try {
  New-Item -ItemType Directory -Force -Path $seedRoot | Out-Null

  foreach ($book in $manifest.books) {
    $source = Join-Path $SourceDirectory $book.filename
    if (-not (Test-Path $source -PathType Leaf)) {
      throw "Missing authorized source asset: $source"
    }

    $item = Get-Item $source
    if ($item.Length -lt [int64]$book.minBytes) {
      throw "Asset is unexpectedly small: $source ($($item.Length) bytes)"
    }

    $stream = [System.IO.File]::OpenRead($source)
    try {
      $magic = New-Object byte[] 5
      [void]$stream.Read($magic, 0, 5)
    }
    finally {
      $stream.Dispose()
    }

    $signature = [System.Text.Encoding]::ASCII.GetString($magic)
    if ($signature -ne "%PDF-") {
      throw "Asset is not a PDF: $source"
    }

    Copy-Item -LiteralPath $source -Destination (Join-Path $seedRoot $book.filename) -Force
  }

  $tagged = "$Image`:$Tag"

  Write-Host "Building GHCR Store content image: $tagged" -ForegroundColor Cyan
  & docker build --file (Join-Path $PSScriptRoot "mpay-store-content.Dockerfile") --tag $tagged $tempRoot
  if ($LASTEXITCODE -ne 0) { throw "Docker build failed." }

  Write-Host "Pushing GHCR Store content image: $tagged" -ForegroundColor Cyan
  & docker push $tagged
  if ($LASTEXITCODE -ne 0) { throw "Docker push failed." }

  Write-Host ""
  Write-Host "Store content image published successfully: $tagged" -ForegroundColor Green
}
finally {
  if (Test-Path $tempRoot) { Remove-Item -Recurse -Force $tempRoot }
}
