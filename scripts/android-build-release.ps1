param(
  [Parameter(Mandatory=$true)]
  [string]$ApiBaseUrl
)
$ErrorActionPreference = 'Stop'
Push-Location android
try {
  .\gradlew.bat :app:assembleRelease "-PmpayApiBaseUrl=$ApiBaseUrl"
} finally {
  Pop-Location
}
