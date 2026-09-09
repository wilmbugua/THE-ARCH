param(
    [string]$DbHost = "localhost",
    [string]$DbPort = "3306",
    [string]$DbName = "kalc_pos_web",
    [string]$DbUser = "root",
    [string]$DbPassword = "Kaisy@3030",
    [string]$ServerPort = "8081"
)

$ErrorActionPreference = "Stop"
$BaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RequiredBaseDir = "D:\KALCPOS"

if ($BaseDir.TrimEnd('\') -ine $RequiredBaseDir) {
    Write-Host "KALCPOS must be installed and run from $RequiredBaseDir" -ForegroundColor Red
    Write-Host "Move this folder to $RequiredBaseDir, then run the script again." -ForegroundColor Yellow
    exit 1
}

$JarPath = Join-Path $BaseDir "backend\webpos-backend-0.0.1-SNAPSHOT.jar"
$LogPath = Join-Path $BaseDir "logs\kalcpos-backend.log"

if (-not (Test-Path $JarPath)) {
    Write-Host "Backend JAR not found: $JarPath" -ForegroundColor Red
    exit 1
}

try {
    java -version | Out-Null
} catch {
    Write-Host "Java was not found. Install Java 17 or newer, then run this again." -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path (Join-Path $BaseDir "logs") | Out-Null

$env:KALC_DB_URL = "jdbc:mariadb://${DbHost}:${DbPort}/${DbName}?allowMultiQueries=true&serverTimezone=UTC&useSSL=false&allowServerSideCustomAuth=false"
$env:KALC_DB_USER = $DbUser
$env:KALC_DB_PASSWORD = $DbPassword
$env:KALC_ENV_PROFILE = "server"
$env:KALC_SERVER_PORT = $ServerPort

Write-Host "Starting KALCPOS..." -ForegroundColor Cyan
Write-Host "App URL: http://localhost:$ServerPort" -ForegroundColor Green
Write-Host "LAN URL: http://<server-ip>:$ServerPort" -ForegroundColor Green
Write-Host "Log: $LogPath" -ForegroundColor Gray
Write-Host ""
Write-Host "Keep this window open while KALCPOS is running. Press Ctrl+C to stop." -ForegroundColor Yellow
Write-Host ""

java -jar $JarPath *> $LogPath
