# Run ALL backend services against the LOCAL POSTGRES database.
#
# Use this script when you want services to read/write the same Postgres
# database (`repairshop`) that psql sees. The other script (run-all-backend-dev.ps1)
# uses the `dev` Spring profile, which for most services points at an in-memory
# or file-based H2 — useful for quick demos, dangerous if you assume Postgres.
#
# Requires Maven + Java 25+ on PATH and a running PostgreSQL with database
# `repairshop` and the schema applied (see database/schema/*.sql).
#
# Run from the backend root:
#     .\run-all-backend-pg.ps1

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$svc = "$root\services"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Maven (mvn) not found on PATH." -ForegroundColor Yellow
    exit 1
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

foreach ($s in $services) {
    $dir = Join-Path $svc $s.Dir
    if (-not (Test-Path $dir)) { Write-Host "Skip $($s.Name) (not found: $dir)" -ForegroundColor Yellow; continue }
    Write-Host "Starting $($s.Name) (port $($s.Port)) on Postgres..."
    # NOTE: no -Dspring-boot.run.profiles=dev so each service uses application.yml (Postgres).
    # DB_PASSWORD=root matches the local Postgres install; application.yml defaults to 'postgres' which would fail.
    $cmd = "`$Host.UI.RawUI.WindowTitle = 'ggfix-$($s.Name)-$($s.Port)-PG'; `$env:DB_PASSWORD='root'; cd '$dir'; mvn -q -DskipTests spring-boot:run"
    Start-Process powershell -ArgumentList @("-NoExit","-Command",$cmd) | Out-Null
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "All services starting in separate windows. Wait until each prints 'Started ...Application'." -ForegroundColor Green
Write-Host ""
Write-Host "Ports:" -ForegroundColor Cyan
Write-Host "  Auth:         http://localhost:8081  |  Master Data: http://localhost:8091  |  Ticket: http://localhost:8082"
Write-Host "  User:         http://localhost:8083  |  Shop:         http://localhost:8084  |  Technician: http://localhost:8085"
Write-Host "  Inventory:    http://localhost:8086  |  Marketplace:  http://localhost:8087  |  Pickup: http://localhost:8088"
Write-Host "  Notification: http://localhost:8089  |  Subscription: http://localhost:8090  |  Order: http://localhost:8092"
Write-Host ""
Write-Host "Smoke test: Invoke-RestMethod http://localhost:8081/auth/login -Method POST -ContentType application/json -Body '{\"email\":\"barani\",\"password\":\"barani\"}'"
