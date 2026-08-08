[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$frontendDir = Join-Path $projectRoot "frontend"
$runtimeDir = Join-Path $projectRoot ".runtime"
$backendPidFile = Join-Path $runtimeDir "backend.pid"
$frontendPidFile = Join-Path $runtimeDir "frontend.pid"
$backendOutLog = Join-Path $runtimeDir "backend.out.log"
$backendErrLog = Join-Path $runtimeDir "backend.err.log"
$frontendOutLog = Join-Path $runtimeDir "frontend.out.log"
$frontendErrLog = Join-Path $runtimeDir "frontend.err.log"
$backendUrl = "http://127.0.0.1:18080/api/auth/registration"
$frontendUrl = "http://127.0.0.1:5173"
$backendPort = 18080
$frontendPort = 5173
$startedBackend = $null
$startedFrontend = $null
$originalBackendPortOverride = [Environment]::GetEnvironmentVariable("FISHING_SERVER_PORT", "Process")
$originalApiProxyOverride = [Environment]::GetEnvironmentVariable("VITE_API_PROXY_TARGET", "Process")
$startupMutex = New-Object System.Threading.Mutex($false, "Local\FishingPlatformStarter")
$mutexAcquired = $false

function Write-Step {
    param([string]$Message)
    Write-Host "[Fishing Platform] $Message" -ForegroundColor Cyan
}

function Require-Command {
    param(
        [string]$Name,
        [string]$InstallHint
    )
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "$Name was not found. $InstallHint"
    }
    return $command
}

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

function Get-TrackedProcess {
    param(
        [string]$PidFile,
        [int]$Port,
        [string]$ExpectedName
    )
    if (-not (Test-Path $PidFile)) {
        return $null
    }

    $rawPid = (Get-Content $PidFile -Raw).Trim()
    $trackedPid = 0
    if (-not [int]::TryParse($rawPid, [ref]$trackedPid)) {
        Remove-Item $PidFile -Force
        return $null
    }

    try {
        $process = Get-Process -Id $trackedPid -ErrorAction Stop
        $portOwner = Get-PortOwner -Port $Port
        if ($process.ProcessName -ne $ExpectedName -or $portOwner -ne $trackedPid) {
            Remove-Item $PidFile -Force
            return $null
        }
        return $process
    }
    catch {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        return $null
    }
}

function Assert-PortAvailable {
    param([int]$Port)
    $owner = Get-PortOwner -Port $Port
    if ($null -ne $owner) {
        throw "Port $Port is already in use by process $owner. Close that program or change the port before retrying."
    }
}

function Wait-ForUrl {
    param(
        [string]$Url,
        [int]$TimeoutSeconds,
        [System.Diagnostics.Process]$Process,
        [string]$ErrorLog
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $Process -and $Process.HasExited) {
            $tail = ""
            if (Test-Path $ErrorLog) {
                $tail = (Get-Content $ErrorLog -Tail 20) -join [Environment]::NewLine
            }
            throw "Process exited before $Url became ready.$([Environment]::NewLine)$tail"
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    $timeoutTail = ""
    if (Test-Path $ErrorLog) {
        $timeoutTail = (Get-Content $ErrorLog -Tail 20) -join [Environment]::NewLine
    }
    throw "Timed out waiting for $Url.$([Environment]::NewLine)$timeoutTail"
}

function Stop-StartedProcess {
    param(
        [AllowNull()][System.Diagnostics.Process]$Process,
        [string]$PidFile
    )
    if ($null -eq $Process) {
        return
    }
    Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

try {
    [Environment]::SetEnvironmentVariable("FISHING_SERVER_PORT", "$backendPort", "Process")
    [Environment]::SetEnvironmentVariable(
        "VITE_API_PROXY_TARGET", "http://127.0.0.1:$backendPort", "Process")

    try {
        $mutexAcquired = $startupMutex.WaitOne(0)
    }
    catch [System.Threading.AbandonedMutexException] {
        $mutexAcquired = $true
    }
    if (-not $mutexAcquired) {
        throw "Another startup task is already running. Wait for it to finish before retrying."
    }

    Write-Step "Checking Windows prerequisites..."
    $javaCommand = Require-Command -Name "java.exe" -InstallHint "Install JDK 17 and reopen PowerShell."
    $mavenCommand = Require-Command -Name "mvn.cmd" -InstallHint "Install Maven 3.9+ and add its bin directory to PATH."
    $nodeCommand = Require-Command -Name "node.exe" -InstallHint "Install Node.js 20+ LTS and reopen PowerShell."
    $npmCommand = Require-Command -Name "npm.cmd" -InstallHint "Reinstall Node.js with npm enabled."

    $javaVersionText = (& $javaCommand.Source -version 2>&1 | Out-String)
    if ($javaVersionText -notmatch 'version "17([.]|"|$)') {
        throw "JDK 17 is required. Current java output: $($javaVersionText.Trim())"
    }

    $mavenVersionText = (& $mavenCommand.Source -version 2>&1 | Out-String)
    if ($mavenVersionText -notmatch 'Apache Maven ([0-9]+)[.]([0-9]+)') {
        throw "Unable to read the Maven version: $($mavenVersionText.Trim())"
    }
    $mavenMajor = [int]$Matches[1]
    $mavenMinor = [int]$Matches[2]
    if ($mavenMajor -lt 3 -or ($mavenMajor -eq 3 -and $mavenMinor -lt 9)) {
        throw "Maven 3.9 or newer is required. Current output: $($mavenVersionText.Trim())"
    }
    if ($mavenVersionText -notmatch 'Java version: 17([.,]|$)') {
        throw "Maven is not using JDK 17. Fix JAVA_HOME, reopen PowerShell, and retry."
    }

    $nodeVersionText = (& $nodeCommand.Source --version | Out-String).Trim()
    if ($nodeVersionText -notmatch '^v([0-9]+)') {
        throw "Unable to read the Node.js version."
    }
    $nodeMajor = [int]$Matches[1]
    if ($nodeMajor -lt 20) {
        throw "Node.js 20 or newer is required. Current version: $nodeVersionText"
    }

    if (-not (Test-Path (Join-Path $backendDir "pom.xml")) -or
        -not (Test-Path (Join-Path $frontendDir "package.json"))) {
        throw "Project files are incomplete. Keep the script beside the backend and frontend folders."
    }

    New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

    $backendProcess = Get-TrackedProcess -PidFile $backendPidFile -Port $backendPort -ExpectedName "java"
    if ($null -eq $backendProcess) {
        Assert-PortAvailable -Port $backendPort
        Write-Step "Building the backend application..."
        Push-Location $backendDir
        try {
            & $mavenCommand.Source "-DskipTests" "package"
            if ($LASTEXITCODE -ne 0) {
                throw "Backend build failed with exit code $LASTEXITCODE."
            }
        }
        finally {
            Pop-Location
        }

        $backendJar = Join-Path $backendDir "target\fishing-platform-0.0.1-SNAPSHOT.jar"
        if (-not (Test-Path $backendJar)) {
            throw "Backend JAR was not created: $backendJar"
        }
        Remove-Item $backendOutLog, $backendErrLog -Force -ErrorAction SilentlyContinue
        Write-Step "Starting the backend on port $backendPort..."
        $startedBackend = Start-Process -FilePath $javaCommand.Source `
            -ArgumentList @("-jar", "`"$backendJar`"") `
            -WorkingDirectory $backendDir `
            -RedirectStandardOutput $backendOutLog `
            -RedirectStandardError $backendErrLog `
            -WindowStyle Hidden `
            -PassThru
        Set-Content -Path $backendPidFile -Value $startedBackend.Id -Encoding Ascii
        $backendProcess = $startedBackend
    }
    else {
        Write-Step "Backend is already running (PID $($backendProcess.Id))."
    }

    Write-Step "Waiting for the backend health endpoint..."
    Wait-ForUrl -Url $backendUrl -TimeoutSeconds 600 -Process $backendProcess -ErrorLog $backendErrLog

    $frontendProcess = Get-TrackedProcess -PidFile $frontendPidFile -Port $frontendPort -ExpectedName "node"
    if ($null -eq $frontendProcess) {
        Assert-PortAvailable -Port $frontendPort
        $packageLock = Join-Path $frontendDir "package-lock.json"
        $installedLock = Join-Path $frontendDir "node_modules\.package-lock.json"
        $installRequired = -not (Test-Path $installedLock)
        if (-not $installRequired -and (Test-Path $packageLock)) {
            $installRequired = (Get-Item $packageLock).LastWriteTimeUtc -gt (Get-Item $installedLock).LastWriteTimeUtc
        }
        if ($installRequired) {
            Write-Step "Installing frontend dependencies (first launch may take several minutes)..."
            Push-Location $frontendDir
            try {
                & $npmCommand.Source "ci"
                if ($LASTEXITCODE -ne 0) {
                    throw "Frontend dependency installation failed with exit code $LASTEXITCODE."
                }
            }
            finally {
                Pop-Location
            }
        }
        else {
            Write-Step "Frontend dependencies are already installed."
        }

        $viteScript = Join-Path $frontendDir "node_modules\vite\bin\vite.js"
        if (-not (Test-Path $viteScript)) {
            throw "Vite was not installed correctly: $viteScript"
        }
        Remove-Item $frontendOutLog, $frontendErrLog -Force -ErrorAction SilentlyContinue
        Write-Step "Starting the frontend on port $frontendPort..."
        $startedFrontend = Start-Process -FilePath $nodeCommand.Source `
            -ArgumentList @("`"$viteScript`"", "--host", "127.0.0.1", "--port", "$frontendPort", "--strictPort") `
            -WorkingDirectory $frontendDir `
            -RedirectStandardOutput $frontendOutLog `
            -RedirectStandardError $frontendErrLog `
            -WindowStyle Hidden `
            -PassThru
        Set-Content -Path $frontendPidFile -Value $startedFrontend.Id -Encoding Ascii
        $frontendProcess = $startedFrontend
    }
    else {
        Write-Step "Frontend is already running (PID $($frontendProcess.Id))."
    }

    Write-Step "Waiting for the frontend..."
    Wait-ForUrl -Url $frontendUrl -TimeoutSeconds 180 -Process $frontendProcess -ErrorLog $frontendErrLog

    Write-Host ""
    Write-Host "Fishing Platform is ready." -ForegroundColor Green
    Write-Host "URL:      $frontendUrl"
    Write-Host "Username: admin"
    Write-Host "Password: admin123"
    Write-Host "Logs:     $runtimeDir"
    Write-Host "Double-click the stop batch file in the project root to close both services."

    try {
        Start-Process $frontendUrl
    }
    catch {
        Write-Warning "The browser could not be opened automatically. Open $frontendUrl manually."
    }
    Start-Sleep -Seconds 2
    exit 0
}
catch {
    Write-Host ""
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Stop-StartedProcess -Process $startedFrontend -PidFile $frontendPidFile
    Stop-StartedProcess -Process $startedBackend -PidFile $backendPidFile
    Write-Host "See logs in $runtimeDir when available."
    exit 1
}
finally {
    [Environment]::SetEnvironmentVariable(
        "FISHING_SERVER_PORT", $originalBackendPortOverride, "Process")
    [Environment]::SetEnvironmentVariable(
        "VITE_API_PROXY_TARGET", $originalApiProxyOverride, "Process")
    if ($mutexAcquired) {
        $startupMutex.ReleaseMutex()
    }
    $startupMutex.Dispose()
}
