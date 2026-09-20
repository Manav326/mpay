$ErrorActionPreference = "Stop"
$required = @(
  "database_postgres_data",
  "c2c0f03741da80b4292c280a6b51e56c72785d278990f9b6518ccdaa7246c003"
)
foreach ($v in $required) {
  $found = docker volume inspect $v 2>$null
  if (-not $found) { throw "Required existing Docker volume not found: $v" }
  Write-Host "FOUND: $v" -ForegroundColor Green
}
Write-Host "All required existing data volumes are present." -ForegroundColor Green
