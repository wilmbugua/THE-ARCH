param(
  [string]$JarPath = (Join-Path $PSScriptRoot 'webpos-backend-0.0.1-SNAPSHOT.jar'),
  [string]$HealthUrl = 'http://127.0.0.1:8081/health',
  [int]$Attempts = 30,
  [int]$DelaySeconds = 2
)

if (-not (Test-Path $JarPath)) { Write-Error "JAR not found: $JarPath"; exit 2 }

$env:KALC_DB_URL = 'jdbc:mariadb://localhost:3306/kalc_pos_web?allowMultiQueries=true&serverTimezone=UTC&useSSL=false&allowServerSideCustomAuth=false'
$env:KALC_DB_USER = 'root'
$env:KALC_DB_PASSWORD = 'Kaisy@3030'
$env:KALC_ENV_PROFILE = 'server'
$env:KALC_SERVER_PORT = '8081'

# Ensure logs directory exists
$logsDir = Join-Path (Split-Path $JarPath) 'logs'
if (-not (Test-Path $logsDir)) { New-Item -Path $logsDir -ItemType Directory -Force | Out-Null }

# Start process detached (stop existing matching processes first)
$existing = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and ($_.CommandLine -match [regex]::Escape($JarPath)) }
if ($existing) {
    foreach ($p in $existing) {
        try {
            Write-Output "Stopping existing PID $($p.ProcessId)"
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            Write-Output "Stopped $($p.ProcessId)"
        } catch {
            Write-Output "Failed to stop PID $($p.ProcessId): $($_.Exception.Message)"
        }
    }
    Start-Sleep -Seconds 1
}

Write-Output "Starting backend (detached): java -jar $JarPath"
$proc = Start-Process -FilePath java -ArgumentList '-jar', $JarPath -WorkingDirectory (Split-Path $JarPath) -PassThru -WindowStyle Hidden
Write-Output "Started PID: $($proc.Id)"

# Poll health endpoint
$attempt = 0
$healthy = $false
while ($attempt -lt $Attempts) {
    $attempt++
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $HealthUrl -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -eq 200) {
            Write-Output ("Health check succeeded on attempt {0}: {1}" -f $attempt, $HealthUrl)
            $healthy = $true
            break
        } else {
            Write-Output ("Health check attempt {0} returned status {1}" -f $attempt, $r.StatusCode)
        }
    } catch {
        Write-Output ("Health check attempt {0} failed: {1}" -f $attempt, $_.Exception.Message)
    }
    Start-Sleep -Seconds $DelaySeconds
}

if (-not $healthy) {
    Write-Output "Backend did not become healthy after $Attempts attempts. Gathering diagnostics..."
    # Show last lines of log file if present
    $logFile = Join-Path $logsDir 'backend.log'
    if (Test-Path $logFile) {
        Write-Output "--- Last 200 lines of $logFile ---"
        try { Get-Content -Path $logFile -Tail 200 } catch { Write-Output "Failed to read log file: $($_.Exception.Message)" }
    } else {
        Write-Output "Log file not found at $logFile"
    }

    # Show process details
    try {
        Get-Process -Id $proc.Id | Select-Object Id,ProcessName,StartTime | Format-List
    } catch { Write-Output "Process $($proc.Id) not found or already exited." }

    exit 3
}

Write-Output "Backend is healthy. PID: $($proc.Id)"
Start-Process 'http://127.0.0.1:8081'
Write-Output "Opened backend root in browser: http://127.0.0.1:8081"
exit 0
