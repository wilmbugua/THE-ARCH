param([string]$Uri = 'http://127.0.0.1:8081/actuator/health')
try {
  $r = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 5
  Write-Output "Status: $($r.StatusCode)"
  Write-Output $r.Content
  exit 0
} catch {
  Write-Error "Request failed: $($_.Exception.Message)"
  exit 2
}
