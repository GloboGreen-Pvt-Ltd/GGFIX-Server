# Stop ALL backend services started for dev environment
# Run from repo root: .\stop-all-backend-dev.ps1

$ErrorActionPreference = "SilentlyContinue"

$ports = @(
    8081, # Auth
    8091, # Master Data
    8082, # Ticket
    8083, # User
    8084, # Shop
    8085, # Technician
    8086, # Inventory
    8087, # Marketplace
    8088, # Pickup
    8089, # Notification
    8090, # Subscription
    8092  # Order
)

Write-Host "Stopping backend services..." -ForegroundColor Yellow
Write-Host ""

foreach ($port in $ports) {
    $process = Get-NetTCPConnection -LocalPort $port -State Listen |
               Select-Object -ExpandProperty OwningProcess

    if ($process) {
        Write-Host "Stopping service running on port $port (PID: $process)"
        Stop-Process -Id $process -Force
    }
    else {
        Write-Host "No service running on port $port"
    }
}

Write-Host ""
Write-Host "All backend services stopped." -ForegroundColor Green