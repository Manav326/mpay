param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminMobile = "9999999999",
    [string]$AdminPassword = "Admin@123",
    [string]$ManagerMobile = "9999999998",
    [string]$ManagerPassword = "Manager@123",
    [string]$ClientMobile = "7070107458",
    [string]$ClientPassword = "Manav@123"
)
$ErrorActionPreference = 'Stop'
function CallJson($Uri,$Method='GET',$Headers=@{},$Body=$null){
    if($null -eq $Body){ return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers }
    return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 30)
}
function Pass($m){ Write-Host "PASS: $m" -ForegroundColor Green }
function Fail($m){ throw $m }
function ExpectFailure([scriptblock]$Action,[string[]]$Patterns=@('401','403','Forbidden','Unauthorized','Invalid','denied')){
    try { & $Action; return $false } catch { $msg=$_.Exception.Message; foreach($p in $Patterns){ if($msg -match $p){ return $true } }; throw }
}

Write-Host 'mPay Portal Roles + Permissions Critical Test' -ForegroundColor Yellow

$roles=CallJson "$BaseUrl/api/v1/auth/portal-roles"
if(-not ($roles.roles -contains 'ADMIN')){ Fail 'ADMIN is not an enabled portal role.' }
if(-not ($roles.roles -contains 'MANAGER')){ Fail 'MANAGER is not an enabled portal role.' }
Pass 'Portal role discovery returns ADMIN and MANAGER.'

$admin=CallJson "$BaseUrl/api/v1/auth/admin-login" 'POST' @{} @{mobile=$AdminMobile;password=$AdminPassword}
if($admin.role -ne 'ADMIN'){ Fail "Admin login returned role $($admin.role)." }
if(@($admin.permissions) -notcontains 'PORTAL_LOGIN'){ Fail 'Admin token response is missing PORTAL_LOGIN.' }
if(@($admin.permissions) -notcontains 'MANAGE_VENDORS'){ Fail 'Admin token response is missing MANAGE_VENDORS.' }
Pass 'ADMIN portal login succeeded with database role and permissions.'
$adminH=@{Authorization="Bearer $($admin.accessToken)"}

$adminRoles=CallJson "$BaseUrl/api/v1/admin/visible-roles" 'GET' $adminH
foreach($r in @('ADMIN','MANAGER','CLIENT')){ if(@($adminRoles.roles) -notcontains $r){ Fail "Admin cannot see $r." } }
Pass 'ADMIN visibility hierarchy includes all roles.'

$manager=CallJson "$BaseUrl/api/v1/auth/manager-login" 'POST' @{} @{mobile=$ManagerMobile;password=$ManagerPassword}
if($manager.role -ne 'MANAGER'){ Fail "Manager login returned role $($manager.role)." }
if(@($manager.permissions) -notcontains 'VIEW_USERS'){ Fail 'Manager token response is missing VIEW_USERS.' }
if(@($manager.permissions) -contains 'MANAGE_VENDORS'){ Fail 'Manager unexpectedly has MANAGE_VENDORS.' }
Pass 'MANAGER portal login succeeded with scoped permissions.'
$managerH=@{Authorization="Bearer $($manager.accessToken)"}

$managerRoles=CallJson "$BaseUrl/api/v1/admin/visible-roles" 'GET' $managerH
if(@($managerRoles.roles).Count -ne 1 -or @($managerRoles.roles)[0] -ne 'CLIENT'){ Fail 'Manager visibility is not limited to CLIENT.' }
Pass 'MANAGER hierarchy is limited to CLIENT.'

$mgrUsers=CallJson "$BaseUrl/api/v1/admin/users?role=ALL&sort=today-high" 'GET' $managerH
if(@($mgrUsers | Where-Object { $_.role -ne 'CLIENT' }).Count -ne 0){ Fail 'Manager received a non-client user.' }
Pass 'Manager user list contains only CLIENT accounts.'

if(-not (ExpectFailure { CallJson "$BaseUrl/api/v1/admin/vendors" 'GET' $managerH })){ Fail 'Manager unexpectedly accessed vendor management.' }
Pass 'Manager cannot access vendor management.'

if(-not (ExpectFailure { CallJson "$BaseUrl/api/v1/auth/admin-login" 'POST' @{} @{mobile=$ManagerMobile;password=$ManagerPassword} })){ Fail 'Manager credentials were accepted by admin-login endpoint.' }
Pass 'Manager credentials rejected by ADMIN endpoint.'

$client=CallJson "$BaseUrl/api/v1/auth/login" 'POST' @{} @{mobile=$ClientMobile;password=$ClientPassword}
if($client.role -ne 'CLIENT'){ Fail "Client login returned role $($client.role)." }
Pass 'Client normal login still works.'

if(-not (ExpectFailure { CallJson "$BaseUrl/api/v1/auth/admin-login" 'POST' @{} @{mobile=$ClientMobile;password=$ClientPassword} })){ Fail 'Client credentials were accepted by admin-login endpoint.' }
Pass 'Client credentials rejected by ADMIN portal endpoint.'

if(-not (ExpectFailure { CallJson "$BaseUrl/api/v1/auth/manager-login" 'POST' @{} @{mobile=$ClientMobile;password=$ClientPassword} })){ Fail 'Client credentials were accepted by manager-login endpoint.' }
Pass 'Client credentials rejected by MANAGER portal endpoint.'

if(-not (ExpectFailure { CallJson "$BaseUrl/api/v1/auth/login" 'POST' @{} @{mobile=$AdminMobile;password=$AdminPassword} })){ Fail 'Admin unexpectedly logged in through normal client login.' }
Pass 'Admin is restricted to portal login for now.'

$detail=CallJson "$BaseUrl/api/v1/admin/users/$($client.userId)" 'GET' $adminH
if($null -eq $detail.summary){ Fail 'Admin could not inspect client details.' }
Pass 'Admin can inspect client details read-only.'

Write-Host ''
Write-Host 'ALL PORTAL ROLE/PERMISSION TESTS PASSED' -ForegroundColor Green
