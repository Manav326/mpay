param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$Mobile,
    [Parameter(Mandatory = $true)]
    [string]$CurrentPassword,
    [string]$NewPassword = "TwilioReset!456"
)

$ErrorActionPreference = "Stop"

function Fail($Message) { Write-Host "FAILED: $Message" -ForegroundColor Red; exit 1 }
function Pass($Message) { Write-Host "PASS: $Message" -ForegroundColor Green }
function Step($Number, $Message) { Write-Host "`n[$Number] $Message" -ForegroundColor Cyan }

function Request-Json {
    param([Parameter(Mandatory=$true)][string]$Uri,[string]$Method="GET",[hashtable]$Headers=@{},$Body=$null)
    try {
        if ($null -eq $Body) { return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers }
        return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 20)
    } catch {
        $detail = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($detail)) { $detail = $_.Exception.Message }
        throw "HTTP $Method $Uri failed: $detail"
    }
}

try {
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host " mPay - Twilio Verify Password Reset Critical Test" -ForegroundColor Yellow
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host "Backend: $BaseUrl" -ForegroundColor Gray
    Write-Host "Mobile : $Mobile" -ForegroundColor Gray

    Step 1 "Verifying the current password works"
    $login = Request-Json -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body @{ mobile=$Mobile; password=$CurrentPassword }
    if ([string]::IsNullOrWhiteSpace([string]$login.accessToken)) { Fail "Current password login failed." }
    Pass "Current password works."

    Step 2 "Requesting Twilio SMS OTP"
    $forgot = Request-Json -Uri "$BaseUrl/api/v1/auth/forgot-password" -Method POST -Body @{ mobile=$Mobile }
    if ("$($forgot.status)" -ne "OTP_SENT") { Fail "Expected OTP_SENT but got '$($forgot.status)'." }
    if ($null -ne $forgot.demoOtp) { Fail "Backend must not return demoOtp when Twilio is enabled." }
    Pass "Forgot-password request accepted; SMS OTP should be delivered to the mobile."

    Write-Host "`nEnter the 6-digit OTP that Twilio sent to +91$Mobile." -ForegroundColor Yellow
    $otp = Read-Host "Twilio OTP"
    if ($otp -notmatch '^[0-9]{6}$') { Fail "OTP must be exactly 6 digits." }

    Step 3 "Rejecting an intentionally wrong OTP"
    try {
        $null = Request-Json -Uri "$BaseUrl/api/v1/auth/reset-password" -Method POST -Body @{ mobile=$Mobile; otp="000000"; newPassword=$NewPassword }
        Fail "Invalid OTP unexpectedly succeeded."
    } catch {
        if ($_.Exception.Message -notmatch "HTTP|Invalid OTP|OTP") { throw }
        Pass "Invalid OTP rejected."
    }

    Step 4 "Resetting the password with the real Twilio OTP"
    $reset = Request-Json -Uri "$BaseUrl/api/v1/auth/reset-password" -Method POST -Body @{ mobile=$Mobile; otp=$otp; newPassword=$NewPassword }
    if ("$($reset.status)" -ne "PASSWORD_RESET_SUCCESS") { Fail "Password reset returned '$($reset.status)'." }
    Pass "Password reset succeeded."

    Step 5 "Checking old password no longer works"
    try {
        $null = Request-Json -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body @{ mobile=$Mobile; password=$CurrentPassword }
        Fail "Old password unexpectedly still works."
    } catch {
        if ($_.Exception.Message -notmatch "HTTP|401|Unauthorized|Invalid mobile number or password") { throw }
        Pass "Old password rejected."
    }

    Step 6 "Checking new password works"
    $login2 = Request-Json -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body @{ mobile=$Mobile; password=$NewPassword }
    if ([string]::IsNullOrWhiteSpace([string]$login2.accessToken)) { Fail "New password login failed." }
    Pass "New password login succeeded."

    Step 7 "Checking the same Twilio OTP cannot be reused"
    try {
        $null = Request-Json -Uri "$BaseUrl/api/v1/auth/reset-password" -Method POST -Body @{ mobile=$Mobile; otp=$otp; newPassword="AnotherReset!789" }
        Fail "Used OTP unexpectedly worked again."
    } catch {
        if ($_.Exception.Message -notmatch "HTTP|expired|used|Invalid OTP|OTP") { throw }
        Pass "Used OTP rejected."
    }

    Write-Host "`n============================================================" -ForegroundColor Green
    Write-Host " TWILIO PASSWORD RESET TESTS PASSED" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
}
catch { Fail $_.Exception.Message }
