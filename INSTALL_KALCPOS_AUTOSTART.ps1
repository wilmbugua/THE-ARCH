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
    throw "KALCPOS must be installed and run from $RequiredBaseDir. Move this folder to $RequiredBaseDir, then run this installer again."
}

$AutoScript = Join-Path $BaseDir "START_KALCPOS_AUTO.ps1"
$ConfigScript = Join-Path $BaseDir "KALCPOS_AUTO_CONFIG.ps1"
$TaskName = "KALCPOS Auto Start"

if (-not (Test-Path $AutoScript)) {
    throw "Auto-start script not found: $AutoScript"
}

@"
`$KalcDbHost = "$DbHost"
`$KalcDbPort = "$DbPort"
`$KalcDbName = "$DbName"
`$KalcDbUser = "$DbUser"
`$KalcDbPassword = "$DbPassword"
`$KalcServerPort = "$ServerPort"
"@ | Set-Content -Path $ConfigScript -Encoding ASCII

$Action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$AutoScript`""
$Trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -StartWhenAvailable

Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Settings $Settings -Description "Starts KALC POS backend and frontend proxy when this Windows user logs in." -Force | Out-Null

Write-Output "Registered scheduled task: $TaskName"
Write-Output "Saved auto-start config: $ConfigScript"
