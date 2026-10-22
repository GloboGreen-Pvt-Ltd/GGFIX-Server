# Run the visual-search service (Python/CLIP, port 8199) for local development.
# Companion to run-all-backend-*.ps1, which start only the Java services.
#
# The first run creates services\visual-search-service\.venv and installs its
# requirements (torch is large - allow a few minutes). Requires Python 3.10+.
#
# Run from the backend root:
#     .\run-visual-search-dev.ps1
#     .\run-visual-search-dev.ps1 -Reindex    # refresh the catalog index first
#
# Phones on the same Wi-Fi reach it at http://<this-PC's-LAN-IP>:8199 once
# Windows Firewall allows inbound TCP 8199 (see the service README).

param([switch]$Reindex)

$ErrorActionPreference = "Stop"
$dir = Join-Path $PSScriptRoot "services\visual-search-service"
$venvPython = Join-Path $dir ".venv\Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
    $py = Get-Command python -ErrorAction SilentlyContinue
    if (-not $py) {
        Write-Host "Python 3.10+ not found on PATH." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Creating $dir\.venv and installing requirements (first run only)..."
    & $py.Source -m venv (Join-Path $dir ".venv")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $venvPython -m pip install -r (Join-Path $dir "requirements.txt")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Push-Location $dir
try {
    if ($Reindex -or -not (Test-Path "data\embeddings.npy")) {
        Write-Host "Building/refreshing the catalog index (only new or changed images are embedded)..."
        & $venvPython scripts\index_all.py
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    Write-Host "Visual search: http://localhost:8199  (health: http://localhost:8199/health)" -ForegroundColor Green
    & $venvPython -m uvicorn app.main:app --host 0.0.0.0 --port 8199
}
finally {
    Pop-Location
}
