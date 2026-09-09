# Simple dev smoke checks for frontend and backend
$backend = 'http://127.0.0.1:8081/actuator/health'
$frontend = 'http://127.0.0.1:8000/'

Write-Output "Checking backend: $backend"
try { $b = Invoke-WebRequest -UseBasicParsing -Uri $backend -TimeoutSec 5; Write-Output "Backend HTTP $($b.StatusCode)" } catch { Write-Output "Backend check failed: $($_.Exception.Message)" }

Write-Output "Checking frontend: $frontend"
try { $f = Invoke-WebRequest -UseBasicParsing -Uri $frontend -TimeoutSec 5; Write-Output "Frontend HTTP $($f.StatusCode)" } catch { Write-Output "Frontend check failed: $($_.Exception.Message)" }
