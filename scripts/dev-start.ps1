param(
    [switch]$NoDb
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Path $logs -Force | Out-Null

function Test-PortListening {
    param([int]$Port)
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [string]$Name,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "$Name ready: $Url"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "$Name did not become ready in $TimeoutSeconds seconds."
}

Push-Location $root
try {
    if (-not $NoDb) {
        Write-Host "Starting PostgreSQL..."
        docker compose up -d db
    }

    if (Test-PortListening 8080) {
        Write-Host "Backend already listening on http://localhost:8080"
    } else {
        Write-Host "Starting backend..."
        $backendOut = Join-Path $logs "backend.out.log"
        $backendErr = Join-Path $logs "backend.err.log"
        $backendProcess = Start-Process `
            -FilePath (Join-Path $root "backend\gradlew.bat") `
            -ArgumentList "bootRun" `
            -WorkingDirectory (Join-Path $root "backend") `
            -RedirectStandardOutput $backendOut `
            -RedirectStandardError $backendErr `
            -WindowStyle Hidden `
            -PassThru
        Write-Host "Backend PID: $($backendProcess.Id)"
    }

    Wait-HttpReady -Url "http://localhost:8080/actuator/health" -Name "Backend"

    if (Test-PortListening 3000) {
        Write-Host "Frontend already listening on http://localhost:3000"
    } else {
        Write-Host "Starting frontend..."
        $frontendOut = Join-Path $logs "frontend.out.log"
        $frontendErr = Join-Path $logs "frontend.err.log"
        $frontendProcess = Start-Process `
            -FilePath "npm.cmd" `
            -ArgumentList "run", "dev" `
            -WorkingDirectory (Join-Path $root "frontend") `
            -RedirectStandardOutput $frontendOut `
            -RedirectStandardError $frontendErr `
            -WindowStyle Hidden `
            -PassThru
        Write-Host "Frontend PID: $($frontendProcess.Id)"
    }

    Wait-HttpReady -Url "http://localhost:3000" -Name "Frontend"

    Write-Host ""
    Write-Host "Development servers are running."
    Write-Host "Frontend: http://localhost:3000"
    Write-Host "Backend:  http://localhost:8080"
    Write-Host "Logs:     $logs"
} finally {
    Pop-Location
}
