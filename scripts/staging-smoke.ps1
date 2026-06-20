# Staging smoke test (local Docker)
# Usage: .\scripts\staging-smoke.ps1
# Optional: .\scripts\staging-smoke.ps1 -BuildLocal

param(
    [switch]$BuildLocal,
    [string]$Image = "equity-pnl-service:local"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if ($BuildLocal) {
    Write-Host "Building Docker image $Image ..."
    docker build -t $Image .
}

$env:APP_IMAGE = $Image
$env:JWT_SECRET = "staging-smoke-test-secret-min-256-bits-long-enough-for-jwt"
$env:DATABASE_PASSWORD = "changeme"
$env:FINHUB_KEY = "smoke-test-placeholder"

Write-Host "Starting staging stack ..."
docker compose -f docker-compose.staging.yml down -v 2>$null
docker compose -f docker-compose.staging.yml up -d

Write-Host "Waiting for app health (up to 3 min) ..."
$deadline = (Get-Date).AddMinutes(3)
$healthy = $false
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) {
            $healthy = $true
            break
        }
    } catch { }
    Start-Sleep -Seconds 5
}

if (-not $healthy) {
    Write-Host "FAIL: health check did not pass. Logs:"
    docker compose -f docker-compose.staging.yml logs equity-app --tail 80
    exit 1
}

Write-Host "OK: /actuator/health"

$openapi = Invoke-WebRequest -Uri "http://localhost:8080/v3/api-docs" -UseBasicParsing
if ($openapi.StatusCode -ne 200) { throw "OpenAPI failed" }
Write-Host "OK: /v3/api-docs"

$prom = Invoke-WebRequest -Uri "http://localhost:8080/actuator/prometheus" -UseBasicParsing
if ($prom.StatusCode -ne 200) { throw "Prometheus failed" }
Write-Host "OK: /actuator/prometheus"

$loginBody = '{"uid":"carjam","password":"password"}'
$login = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/login" `
    -Method POST -ContentType "application/json" -Body $loginBody -UseBasicParsing
$token = ($login.Content | ConvertFrom-Json).token
if (-not $token) { throw "Login failed — check Flyway seed user carjam / password" }
Write-Host "OK: POST /api/v1/auth/login"

$pnl = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/pnl?from=2020-01-01&to=2021-12-31" `
    -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
if ($pnl.StatusCode -ne 200) { throw "P&L failed" }
Write-Host "OK: GET /api/v1/pnl"

Write-Host ""
Write-Host "Staging smoke passed. Swagger: http://localhost:8080/swagger-ui.html"
Write-Host "Teardown: docker compose -f docker-compose.staging.yml down -v"
