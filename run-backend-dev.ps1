# Run auth, master-data, and ticket services with in-memory H2 (no PostgreSQL).
# Use this for end-to-end testing: login, add technician, create booking, accept booking.
# Requires Maven and Java 25+ on PATH. Run from repo root: .\run-backend-dev.ps1
# Or run each service from your IDE with VM option: -Dspring.profiles.active=dev

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$authDir = "$root\services\auth-service"
$masterDir = "$root\services\master-data-service"
$ticketDir = "$root\services\ticket-service"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Maven (mvn) not found on PATH. Either:" -ForegroundColor Yellow
    Write-Host "  1. Add Maven to PATH and run this script again"
    Write-Host "  2. Run from IDE: AuthServiceApplication, MasterDataServiceApplication, TicketServiceApplication with -Dspring.profiles.active=dev"
    Write-Host ""
    Write-Host "Services: auth (8081), master-data (8091), ticket (8082)"
    exit 1
}

Write-Host "Starting Auth Service (port 8081)..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$authDir'; mvn -q -DskipTests spring-boot:run '-Dspring-boot.run.profiles=dev'"

Start-Sleep -Seconds 3
Write-Host "Starting Master Data Service (port 8091)..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$masterDir'; mvn -q -DskipTests spring-boot:run '-Dspring-boot.run.profiles=dev'"

Start-Sleep -Seconds 3
Write-Host "Starting Ticket Service (port 8082)..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$ticketDir'; mvn -q -DskipTests spring-boot:run '-Dspring-boot.run.profiles=dev'"

Write-Host ""
Write-Host "All three services are starting in new windows. Wait until you see 'Started ...Application' in each."
Write-Host "  Auth:        http://localhost:8081  (login: barani / barani; GET /auth/shops; POST /auth/shops/{id}/technicians)"
Write-Host "  Master Data: http://localhost:8091  (brands, models, repair services, RAM, storage)"
Write-Host "  Ticket:      http://localhost:8082  (create/update tickets, PATCH status for accept booking)"
Write-Host ""
Write-Host "Admin panel .env.local: NEXT_PUBLIC_AUTH_BASE=http://localhost:8081, NEXT_PUBLIC_API_BASE=http://localhost:8091"
Write-Host "E2E flow: see services\README-E2E-FLOW.md"
