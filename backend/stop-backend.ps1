$procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and ($_.CommandLine -match 'webpos-backend-0.0.1-SNAPSHOT.jar') }
if (-not $procs) { Write-Output 'No backend process found.'; exit 0 }
foreach ($p in $procs) { Write-Output "Stopping PID $($p.ProcessId)"; try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; Write-Output "Stopped $($p.ProcessId)" } catch { Write-Output "Failed to stop $($p.ProcessId): $($_.Exception.Message)" } }
