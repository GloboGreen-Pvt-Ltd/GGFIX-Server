Param(
  [string]$EnvFile = "$PSScriptRoot\..\.env.example"
)

function Load-DotEnv([string]$path) {
  if (!(Test-Path $path)) { throw "Env file not found: $path" }
  Get-Content $path | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
      $k,$v = $line.Split('=',2)
      [System.Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim())
    }
  }
}

Load-DotEnv $EnvFile

$root = (Resolve-Path "$PSScriptRoot\..").Path

$services = @(
  @{ name = 'auth-service'; port = 8081 },
  @{ name = 'ticket-service'; port = 8082 },
  @{ name = 'user-service'; port = 8083 },
  @{ name = 'shop-service'; port = 8084 },
  @{ name = 'technician-service'; port = 8085 },
  @{ name = 'inventory-service'; port = 8086 },
  @{ name = 'marketplace-service'; port = 8087 },
  @{ name = 'pickup-service'; port = 8088 },
  @{ name = 'notification-service'; port = 8089 },
  @{ name = 'subscription-service'; port = 8090 },
  @{ name = 'master-data-service'; port = 8091 },
  @{ name = 'order-service'; port = 8092 }
)

Write-Host "Root: $root" -ForegroundColor Cyan
Write-Host "JWT_SECRET loaded from: $EnvFile (replace later)" -ForegroundColor Cyan
Write-Host "Starting services in separate PowerShell windows..." -ForegroundColor Cyan

foreach ($s in $services) {
  $name = $s.name
  $port = $s.port
  $svcDir = Join-Path $root $name
  if (!(Test-Path $svcDir)) {
    Write-Host "Skipping missing service folder: $svcDir" -ForegroundColor Yellow
    continue
  }
  $cmd = "cd `"$svcDir`"; `$env:PORT=$port; mvn spring-boot:run"
  Start-Process powershell -ArgumentList "-NoProfile","-Command",$cmd | Out-Null
  Start-Sleep -Milliseconds 400
}

Write-Host "Done." -ForegroundColor Green
Write-Host "Auth Swagger:   http://localhost:8081/swagger-ui.html" -ForegroundColor Green
Write-Host "Ticket Swagger: http://localhost:8082/swagger-ui.html" -ForegroundColor Green
