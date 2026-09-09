$ErrorActionPreference = "SilentlyContinue"

$BaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RequiredBaseDir = "D:\KALCPOS"

if ($BaseDir.TrimEnd('\') -ine $RequiredBaseDir) {
    Write-Output "KALCPOS must be installed and run from $RequiredBaseDir"
    Write-Output "Move this folder to $RequiredBaseDir, then run START_KALCPOS_AUTO.ps1 again."
    exit 1
}

$BackendUrl = "http://127.0.0.1:8081/api/v1/health"
$FrontendUrl = "http://127.0.0.1:8000/"
$BackendScript = Join-Path $BaseDir "START_KALCPOS.ps1"
$FrontendScript = Join-Path $BaseDir "frontend\serve_ps_proxy_httpclient.ps1"
$LogDir = Join-Path $BaseDir "logs"
$ConfigScript = Join-Path $BaseDir "KALCPOS_AUTO_CONFIG.ps1"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$StartArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $BackendScript)
if (Test-Path $ConfigScript) {
    . $ConfigScript
    if ($KalcDbHost) { $StartArgs += @("-DbHost", $KalcDbHost) }
    if ($KalcDbPort) { $StartArgs += @("-DbPort", $KalcDbPort) }
    if ($KalcDbName) { $StartArgs += @("-DbName", $KalcDbName) }
    if ($KalcDbUser) { $StartArgs += @("-DbUser", $KalcDbUser) }
    if ($KalcDbPassword) { $StartArgs += @("-DbPassword", $KalcDbPassword) }
    if ($KalcServerPort) { $StartArgs += @("-ServerPort", $KalcServerPort) }
}

function Test-UrlOk {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 4
        return [int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 400
    } catch {
        return $false
    }
}

if (-not (Test-UrlOk $BackendUrl)) {
    Start-Process -FilePath powershell -ArgumentList $StartArgs -WindowStyle Hidden
}

$deadline = (Get-Date).AddSeconds(45)
while ((Get-Date) -lt $deadline -and -not (Test-UrlOk $BackendUrl)) {
    Start-Sleep -Seconds 2
}

if (-not (Test-UrlOk $FrontendUrl)) {
    Start-Process -FilePath powershell -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $FrontendScript -WindowStyle Hidden
}
