$jar = Join-Path $PSScriptRoot 'webpos-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { Write-Error "JAR not found: $jar"; exit 2 }

$env:KALC_DB_URL = 'jdbc:mariadb://localhost:3306/kalc_pos_web?allowMultiQueries=true&serverTimezone=UTC&useSSL=false&allowServerSideCustomAuth=false'
$env:KALC_DB_USER = 'root'
$env:KALC_DB_PASSWORD = 'Kaisy@3030'
$env:KALC_ENV_PROFILE = 'server'
$env:KALC_SERVER_PORT = '8081'

Write-Output "Starting backend in foreground (press Ctrl+C to stop): java -jar $jar"
& java -jar $jar
