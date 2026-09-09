$jar = Join-Path $PSScriptRoot 'webpos-backend-0.0.1-SNAPSHOT.jar'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Write-Error 'Java not found on PATH.'; exit 3 }

$env:KALC_DB_URL = 'jdbc:mariadb://localhost:3306/kalc_pos_web?allowMultiQueries=true&serverTimezone=UTC&useSSL=false&allowServerSideCustomAuth=false'
$env:KALC_DB_USER = 'root'
$env:KALC_DB_PASSWORD = 'Kaisy@3030'
$env:KALC_ENV_PROFILE = 'server'
$env:KALC_SERVER_PORT = '8081'

$proc = Start-Process -FilePath java -ArgumentList '-jar',$jar -WorkingDirectory $PSScriptRoot -PassThru -WindowStyle Hidden
Write-Output "Started PID: $($proc.Id)"
