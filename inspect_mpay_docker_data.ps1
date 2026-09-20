$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Yellow
Write-Host " mPay - Existing PostgreSQL / Redis Docker Inspector" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host ""

function Section($title) {
    Write-Host ""
    Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host $title -ForegroundColor Cyan
    Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
}

function Run-Docker($args) {
    try {
        return docker @args 2>&1
    }
    catch {
        return $_.Exception.Message
    }
}

# ------------------------------------------------------------
# 1. Docker availability
# ------------------------------------------------------------
Section "1. Docker version"

docker version

# ------------------------------------------------------------
# 2. Running containers
# ------------------------------------------------------------
Section "2. Running containers"

docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"

# ------------------------------------------------------------
# 3. All containers
# ------------------------------------------------------------
Section "3. All containers"

docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"

# ------------------------------------------------------------
# 4. Docker volumes
# ------------------------------------------------------------
Section "4. Docker volumes"

docker volume ls

# ------------------------------------------------------------
# 5. Detect probable PostgreSQL / Redis containers
# ------------------------------------------------------------
Section "5. Detecting PostgreSQL / Redis containers"

$containers = @(docker ps -a --format "{{.Names}}")

$postgresContainers = @(
    $containers | Where-Object {
        $_ -match "(?i)postgres|postgre"
    }
)

$redisContainers = @(
    $containers | Where-Object {
        $_ -match "(?i)redis"
    }
)

Write-Host ""
Write-Host "PostgreSQL-like containers:" -ForegroundColor Green
if ($postgresContainers.Count -eq 0) {
    Write-Host "  None detected"
} else {
    $postgresContainers | ForEach-Object { Write-Host "  $_" }
}

Write-Host ""
Write-Host "Redis-like containers:" -ForegroundColor Green
if ($redisContainers.Count -eq 0) {
    Write-Host "  None detected"
} else {
    $redisContainers | ForEach-Object { Write-Host "  $_" }
}

# ------------------------------------------------------------
# 6. Inspect PostgreSQL containers
# ------------------------------------------------------------
Section "6. PostgreSQL container details"

foreach ($name in $postgresContainers) {
    Write-Host ""
    Write-Host "POSTGRES CONTAINER: $name" -ForegroundColor Yellow
    Write-Host ""

    Write-Host "[Image]"
    docker inspect $name --format "{{.Config.Image}}"

    Write-Host ""
    Write-Host "[Status]"
    docker inspect $name --format "{{.State.Status}}"

    Write-Host ""
    Write-Host "[Ports]"
    docker inspect $name --format "{{json .NetworkSettings.Ports}}"

    Write-Host ""
    Write-Host "[Environment]"
    docker inspect $name --format '{{range .Config.Env}}{{println .}}{{end}}' |
        Where-Object {
            $_ -match "POSTGRES_|TZ|PG"
        }

    Write-Host ""
    Write-Host "[Mounts / Volumes]"
    docker inspect $name --format '{{range .Mounts}}{{println "Type=" .Type " Name=" .Name " Source=" .Source " Destination=" .Destination " RW=" .RW}}{{end}}'

    Write-Host ""
    Write-Host "[Networks]"
    docker inspect $name --format '{{json .NetworkSettings.Networks}}'
}

# ------------------------------------------------------------
# 7. Inspect Redis containers
# ------------------------------------------------------------
Section "7. Redis container details"

foreach ($name in $redisContainers) {
    Write-Host ""
    Write-Host "REDIS CONTAINER: $name" -ForegroundColor Yellow
    Write-Host ""

    Write-Host "[Image]"
    docker inspect $name --format "{{.Config.Image}}"

    Write-Host ""
    Write-Host "[Status]"
    docker inspect $name --format "{{.State.Status}}"

    Write-Host ""
    Write-Host "[Ports]"
    docker inspect $name --format "{{json .NetworkSettings.Ports}}"

    Write-Host ""
    Write-Host "[Environment]"
    docker inspect $name --format '{{range .Config.Env}}{{println .}}{{end}}' |
        Where-Object {
            $_ -match "REDIS|TZ"
        }

    Write-Host ""
    Write-Host "[Mounts / Volumes]"
    docker inspect $name --format '{{range .Mounts}}{{println "Type=" .Type " Name=" .Name " Source=" .Source " Destination=" .Destination " RW=" .RW}}{{end}}'

    Write-Host ""
    Write-Host "[Networks]"
    docker inspect $name --format '{{json .NetworkSettings.Networks}}'
}

# ------------------------------------------------------------
# 8. PostgreSQL database information
# ------------------------------------------------------------
Section "8. PostgreSQL database information"

foreach ($name in $postgresContainers) {

    Write-Host ""
    Write-Host "Testing PostgreSQL container: $name" -ForegroundColor Yellow

    $envLines = docker inspect $name --format '{{range .Config.Env}}{{println .}}{{end}}'

    $dbUser = ($envLines | Where-Object { $_ -match "^POSTGRES_USER=" }) -replace "^POSTGRES_USER=", ""
    $dbName = ($envLines | Where-Object { $_ -match "^POSTGRES_DB=" }) -replace "^POSTGRES_DB=", ""

    if ([string]::IsNullOrWhiteSpace($dbUser)) {
        $dbUser = "postgres"
    }

    if ([string]::IsNullOrWhiteSpace($dbName)) {
        $dbName = "postgres"
    }

    Write-Host "Detected DB user: $dbUser"
    Write-Host "Detected DB name : $dbName"

    Write-Host ""
    Write-Host "Database list:"
    docker exec $name psql -U $dbUser -d $dbName -c "\l" 2>&1

    Write-Host ""
    Write-Host "Table list:"
    docker exec $name psql -U $dbUser -d $dbName -c "\dt" 2>&1
}

# ------------------------------------------------------------
# 9. Redis basic information
# ------------------------------------------------------------
Section "9. Redis information"

foreach ($name in $redisContainers) {
    Write-Host ""
    Write-Host "Redis container: $name" -ForegroundColor Yellow

    docker exec $name redis-cli ping 2>&1
    docker exec $name redis-cli INFO server 2>&1
    docker exec $name redis-cli DBSIZE 2>&1
}

# ------------------------------------------------------------
# 10. Compose labels
# ------------------------------------------------------------
Section "10. Docker Compose labels"

foreach ($name in $containers) {
    Write-Host ""
    Write-Host "Container: $name" -ForegroundColor Yellow

    docker inspect $name --format '{{range $k,$v := .Config.Labels}}{{println $k "=" $v}}{{end}}' |
        Where-Object {
            $_ -match "(?i)compose|project|service"
        }
}

# ------------------------------------------------------------
# 11. Save full inspection to JSON files
# ------------------------------------------------------------
Section "11. Saving complete inspect output"

$outputDir = Join-Path (Get-Location) "mpay_docker_inspection"

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

docker ps -a --format json |
    Out-File (Join-Path $outputDir "containers.json") -Encoding utf8

docker volume ls --format json |
    Out-File (Join-Path $outputDir "volumes.json") -Encoding utf8

foreach ($name in $containers) {
    $safeName = $name -replace '[^a-zA-Z0-9._-]', '_'
    docker inspect $name |
        Out-File (Join-Path $outputDir "$safeName.inspect.json") -Encoding utf8
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Inspection completed" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Saved details to:" -ForegroundColor Yellow
Write-Host (Resolve-Path $outputDir)
Write-Host ""
Write-Host "No containers, volumes, databases, or Redis data were modified." -ForegroundColor Green