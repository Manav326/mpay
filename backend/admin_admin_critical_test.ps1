param(
    [Parameter(Mandatory=$true)][string]$AdminMobile,
    [Parameter(Mandatory=$true)][string]$AdminPassword,
    [Parameter(Mandatory=$true)][string]$ManagerMobile,
    [Parameter(Mandatory=$true)][string]$ManagerPassword,
    [Parameter(Mandatory=$true)][string]$ClientMobile,
    [Parameter(Mandatory=$true)][string]$ClientPassword,
    [string]$BaseUrl = "http://localhost:8080"
)
$ErrorActionPreference='Stop'
function CallJson($Uri,$Method='GET',$Headers=@{},$Body=$null){
    if($null -eq $Body){ return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers }
    return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 30)
}
function Login($m,$p){ CallJson "$BaseUrl/api/v1/auth/login" 'POST' @{} @{mobile=$m;password=$p} }
function Pass($m){ Write-Host "PASS: $m" -ForegroundColor Green }
function Fail($m){ throw $m }

Write-Host 'mPay Admin Backend Critical Test' -ForegroundColor Yellow
$admin=Login $AdminMobile $AdminPassword
if($admin.role -ne 'ADMIN'){ Fail "Admin login returned $($admin.role)" }
Pass 'Admin portal login succeeded.'
$adminH=@{Authorization="Bearer $($admin.accessToken)"}
$dash=CallJson "$BaseUrl/api/v1/admin/dashboard" 'GET' $adminH
if($null -eq $dash.todayCommission -or $null -eq $dash.monthlyCommission){ Fail 'Dashboard commission fields missing.' }
Pass 'Admin dashboard returned company metrics.'
$users=CallJson "$BaseUrl/api/v1/admin/users?role=ALL&sort=today-high" 'GET' $adminH
if($users.Count -lt 1){ Fail 'Admin user list is empty.' }
Pass 'Admin can see hierarchy users.'
$manager=Login $ManagerMobile $ManagerPassword
if($manager.role -ne 'MANAGER'){ Fail "Manager login returned $($manager.role)" }
Pass 'Manager portal login succeeded.'
$mgrH=@{Authorization="Bearer $($manager.accessToken)"}
$mgrUsers=CallJson "$BaseUrl/api/v1/admin/users?role=ALL&sort=today-high" 'GET' $mgrH
if(@($mgrUsers | ? {$_.role -ne 'CLIENT'}).Count -ne 0){ Fail 'Manager received non-client users.' }
Pass 'Manager sees only clients.'
try { $null=CallJson "$BaseUrl/api/v1/admin/vendors" 'GET' $mgrH; Fail 'Manager unexpectedly accessed vendors.' } catch { if($_.Exception.Message -notmatch '403|Forbidden'){ throw } }
Pass 'Manager cannot access admin-only vendor management.'
try { $client=Login $ClientMobile $ClientPassword; Fail 'Client unexpectedly logged into admin portal.' } catch { if($_.Exception.Message -notmatch '403|Forbidden|Access denied'){ throw } }
Pass 'Client is blocked from admin portal login.'
if($users.Count -gt 0){
    $id=$users[0].id
    $detail=CallJson "$BaseUrl/api/v1/admin/users/$([uri]::EscapeDataString($id))" 'GET' $adminH
    foreach($f in @('summary','rechargeCount','addMoneyTotal','withdrawalTotal','commissionRate','recentWalletEntries')){ if($null -eq $detail.$f){ Fail "User detail missing $f" } }
    Pass 'Admin user detail returned read-only account data.'
}
$vendors=CallJson "$BaseUrl/api/v1/admin/vendors" 'GET' $adminH
$vendorBody=@{name="Admin Test Vendor";category="CAR_RENT";city="Patna";phone="9876543210";commissionRate=5;active=$true}
$created=CallJson "$BaseUrl/api/v1/admin/vendors" 'POST' $adminH $vendorBody
if([string]::IsNullOrWhiteSpace([string]$created.id)){ Fail 'Vendor create returned no id.' }
Pass 'Admin can create a vendor.'
Write-Host 'ALL ADMIN CRITICAL TESTS PASSED' -ForegroundColor Green
