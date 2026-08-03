# Run ALL backend services with in-memory H2 for end-to-end testing.
# Requires Maven and Java 25+ on PATH. Run from repo root: .\run-all-backend-dev.ps1
# Starts 12 services in separate PowerShell windows. Wait until each shows "Started ...Application".

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$svc = "$root\services"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Maven (mvn) not found on PATH. Add Maven and Java 25+ to PATH, or run each service from IDE with -Dspring.profiles.active=dev" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Ports: auth 8081, ticket 8082, user 8083, shop 8084, technician 8085, inventory 8086, marketplace 8087, pickup 8088, notification 8089, subscription 8090, master-data 8091, order 8092"
    exit 1
}

$services = @(
    @{ Name = "Auth";           Dir = "auth-service";           Port = 8081 },
    @{ Name = "Master Data";     Dir = "master-data-service";    Port = 8091 },
    @{ Name = "Ticket";          Dir = "ticket-service";         Port = 8082 },
    @{ Name = "User";            Dir = "user-service";           Port = 8083 },
    @{ Name = "Shop";            Dir = "shop-service";           Port = 8084 },
    @{ Name = "Technician";      Dir = "technician-service";     Port = 8085 },
    @{ Name = "Inventory";       Dir = "inventory-service";      Port = 8086 },
    @{ Name = "Marketplace";     Dir = "marketplace-service";   Port = 8087 },
    @{ Name = "Pickup";          Dir = "pickup-service";        Port = 8088 },
    @{ Name = "Notification";    Dir = "notification-service";  Port = 8089 },
    @{ Name = "Subscription";   Dir = "subscription-service";   Port = 8090 },
    @{ Name = "Order";           Dir = "order-service";         Port = 8092 }
)

foreach ($s in $services) {
    $dir = Join-Path $svc $s.Dir
    if (-not (Test-Path $dir)) { Write-Host "Skip $($s.Name) (not found: $dir)" -ForegroundColor Yellow; continue }
    Write-Host "Starting $($s.Name) (port $($s.Port))..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$dir'; mvn -q -DskipTests spring-boot:run '-Dspring-boot.run.profiles=dev'"
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "All services are starting in new windows. Wait until each shows 'Started ...Application'." -ForegroundColor Green
Write-Host ""
Write-Host "Ports (use in admin .env.local and mobile app.json):" -ForegroundColor Cyan
Write-Host "  Auth:         http://localhost:8081   |  Master Data: http://localhost:8091  |  Ticket: http://localhost:8082"
Write-Host "  User:         http://localhost:8083  |  Shop:         http://localhost:8084  |  Technician: http://localhost:8085"
Write-Host "  Inventory:    http://localhost:8086  |  Marketplace:  http://localhost:8087  |  Pickup: http://localhost:8088"
Write-Host "  Notification: http://localhost:8089  |  Subscription: http://localhost:8090 |  Order: http://localhost:8092"
Write-Host ""
Write-Host "Test users (auth): barani/barani, owner/test, technician/test, customer/test"
Write-Host "E2E test data: see services\README-E2E-TEST-DATA.md"
