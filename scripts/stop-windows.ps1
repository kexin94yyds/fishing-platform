[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $projectRoot ".runtime"

function Get-PortOwner {
    param([int]$Port)
    try {
        $connection = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop |
            Select-Object -First 1
        if ($null -ne $connection) {
            return [int]$connection.OwningProcess
        }
    }
    catch {
        return $null
    }
    return $null
}

function Stop-TrackedService {
    param(
        [string]$Label,
        [string]$PidFile,
        [int]$Port,
        [string]$ExpectedName
    )
    if (-not (Test-Path $PidFile)) {
        Write-Host "$Label is not tracked as running."
        return
    }

    $rawPid = (Get-Content $PidFile -Raw).Trim()
    $trackedPid = 0
    if (-not [int]::TryParse($rawPid, [ref]$trackedPid)) {
        Remove-Item $PidFile -Force
        Write-Warning "$Label PID file was invalid and has been removed."
        return
    }

    try {
        $process = Get-Process -Id $trackedPid -ErrorAction Stop
        $portOwner = Get-PortOwner -Port $Port
        if ($process.ProcessName -ne $ExpectedName -or $portOwner -ne $trackedPid) {
            Remove-Item $PidFile -Force
            Write-Warning "$Label tracking was stale. No unrelated process was stopped."
            return
        }
        Stop-Process -Id $trackedPid -Force
        Write-Host "$Label stopped (PID $trackedPid)." -ForegroundColor Green
    }
    catch {
        Write-Host "$Label was already stopped."
    }
    finally {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
}

try {
    Stop-TrackedService `
        -Label "Frontend" `
        -PidFile (Join-Path $runtimeDir "frontend.pid") `
        -Port 5173 `
        -ExpectedName "node"
    Stop-TrackedService `
        -Label "Backend" `
        -PidFile (Join-Path $runtimeDir "backend.pid") `
        -Port 18080 `
        -ExpectedName "java"
    Write-Host "Fishing Platform shutdown completed."
    exit 0
}
catch {
    Write-Host "Shutdown failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
