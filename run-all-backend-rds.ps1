# Run ALL backend services against the AWS RDS database (`ggfix_server`).
#
# Sibling of run-all-backend-pg.ps1 (local Postgres) and run-all-backend-dev.ps1
# (H2 `dev` profile). This one uses the default profile - so application.yml,
# Postgres, ddl-auto: validate - but points DB_* at RDS instead of localhost.
#
# Credentials live in .env.rds (gitignored). Copy .env.rds and edit if the
# instance or password changes; nothing is hardcoded here.
#
# Requires Maven + Java 21+ on PATH and network access to the RDS endpoint
# (the instance security group must allow 5432 from this machine's IP).
#
# Run from the backend root:
#     .\run-all-backend-rds.ps1
#     .\run-all-backend-rds.ps1 -Only auth-service,order-service
#     .\run-all-backend-rds.ps1 -SkipPreflight

param(
    [string[]] $Only,
    [switch] $SkipPreflight
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$svc = "$root\services"
$envFile = Join-Path $root ".env.rds"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Maven (mvn) not found on PATH." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $envFile)) {
    Write-Host "Missing $envFile - create it with DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD." -ForegroundColor Yellow
    exit 1
}

# --- load .env.rds (KEY=VALUE, # comments and blank lines ignored) ---------
$envVars = [ordered]@{}
foreach ($line in Get-Content $envFile) {
    $trimmed = $line.Trim()
    if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
    $idx = $trimmed.IndexOf("=")
    if ($idx -lt 1) { continue }
    $key = $trimmed.Substring(0, $idx).Trim()
    $value = $trimmed.Substring($idx + 1).Trim().Trim('"')
    $envVars[$key] = $value
}

foreach ($required in @("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD")) {
    if (-not $envVars.Contains($required)) {
        Write-Host "$envFile is missing $required." -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "RDS target: $($envVars.DB_USER)@$($envVars.DB_HOST):$($envVars.DB_PORT)/$($envVars.DB_NAME)" -ForegroundColor Cyan

# --- preflight: fail fast on a bad endpoint instead of 12 crashed windows --
if (-not $SkipPreflight) {
    $reachable = Test-NetConnection -ComputerName $envVars.DB_HOST -Port ([int]$envVars.DB_PORT) -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $reachable) {
        Write-Host "Cannot reach $($envVars.DB_HOST):$($envVars.DB_PORT)." -ForegroundColor Yellow
        Write-Host "Check the RDS security group allows 5432 from your current public IP." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Endpoint reachable." -ForegroundColor Green
}

$services = @(
    @{ Name = "Auth";         Dir = "auth-service";         Port = 8081 },
    @{ Name = "Master Data";  Dir = "master-data-service";  Port = 8091 },
    @{ Name = "Ticket";       Dir = "ticket-service";       Port = 8082 },
    @{ Name = "User";         Dir = "user-service";         Port = 8083 },
    @{ Name = "Shop";         Dir = "shop-service";         Port = 8084 },
    @{ Name = "Technician";   Dir = "technician-service";   Port = 8085 },
    @{ Name = "Inventory";    Dir = "inventory-service";    Port = 8086 },
    @{ Name = "Marketplace";  Dir = "marketplace-service";  Port = 8087 },
    @{ Name = "Pickup";       Dir = "pickup-service";       Port = 8088 },
    @{ Name = "Notification"; Dir = "notification-service"; Port = 8089 },
    @{ Name = "Subscription"; Dir = "subscription-service"; Port = 8090 },
    @{ Name = "Order";        Dir = "order-service";        Port = 8092 }
)

if ($Only) { $services = $services | Where-Object { $Only -contains $_.Dir } }
if (-not $services) { Write-Host "No services matched -Only." -ForegroundColor Yellow; exit 1 }

# Build the `$env:KEY='value'` prefix once; single quotes are doubled so a
# password containing ' cannot break out of the string.
$envPrefix = ""
foreach ($k in $envVars.Keys) {
    $escaped = $envVars[$k].Replace("'", "''")
    $envPrefix += "`$env:$k='$escaped'; "
}

foreach ($s in $services) {
    $dir = Join-Path $svc $s.Dir
    if (-not (Test-Path $dir)) { Write-Host "Skip $($s.Name) (not found: $dir)" -ForegroundColor Yellow; continue }
    Write-Host "Starting $($s.Name) (port $($s.Port)) on RDS..."
    # NOTE: no -Dspring-boot.run.profiles=dev - the dev profile would swap in H2
    # and silently ignore RDS entirely (list endpoints then look empty).
    $cmd = "`$Host.UI.RawUI.WindowTitle = 'ggfix-$($s.Name)-$($s.Port)-RDS'; $envPrefix cd '$dir'; mvn -q -DskipTests spring-boot:run"
    Start-Process powershell -ArgumentList @("-NoExit", "-Command", $cmd) | Out-Null
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "All services starting in separate windows. Wait until each prints 'Started ...Application'." -ForegroundColor Green
Write-Host "A window that dies on 'Schema-validation: missing column' means RDS is behind the entities - add the next migration." -ForegroundColor DarkGray
Write-Host ""
Write-Host "Ports:" -ForegroundColor Cyan
Write-Host "  Auth:         http://localhost:8081  |  Master Data: http://localhost:8091  |  Ticket: http://localhost:8082"
Write-Host "  User:         http://localhost:8083  |  Shop:         http://localhost:8084  |  Technician: http://localhost:8085"
Write-Host "  Inventory:    http://localhost:8086  |  Marketplace:  http://localhost:8087  |  Pickup: http://localhost:8088"
Write-Host "  Notification: http://localhost:8089  |  Subscription: http://localhost:8090  |  Order: http://localhost:8092"
Write-Host ""
Write-Host "Smoke test: Invoke-RestMethod http://localhost:8081/auth/login -Method POST -ContentType application/json -Body '{\"email\":\"barani\",\"password\":\"barani\"}'"
