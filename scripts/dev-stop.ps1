$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Stop-ProcessByPort {
    param([int]$Port)

    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        if ($connection.OwningProcess -and $connection.OwningProcess -ne 0) {
            Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped process $($connection.OwningProcess) on port $Port"
        }
    }
}

function Stop-MatchingProcess {
    param([string[]]$Patterns)

    Get-CimInstance Win32_Process | ForEach-Object {
        $commandLine = $_.CommandLine
        if (-not $commandLine) {
            return
        }

        foreach ($pattern in $Patterns) {
            if ($commandLine -like $pattern) {
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
                Write-Host "Stopped process $($_.ProcessId)"
                break
            }
        }
    }
}

Push-Location $root
try {
    Stop-ProcessByPort -Port 3000
    Stop-ProcessByPort -Port 8080
    Stop-MatchingProcess -Patterns @(
        "*green-house*frontend*next dev*",
        "*green-house*backend*bootRun*",
        "*com.greenhouse.backend.BackendApplication*"
    )

    docker compose stop db

    Write-Host "Development servers stopped."
} finally {
    Pop-Location
}
