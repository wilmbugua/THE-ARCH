@echo off
setlocal
if /I not "%~dp0"=="D:\KALCPOS\" (
  echo KALCPOS must be installed and run from D:\KALCPOS
  echo Move this folder to D:\KALCPOS, then run this installer again.
  pause
  exit /b 1
)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0INSTALL_KALCPOS_AUTOSTART.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0START_KALCPOS_AUTO.ps1"
echo.
echo KALCPOS is starting.
echo Open http://127.0.0.1:8000/ on this computer.
echo Client computers should open http://SERVER_IP_ADDRESS:8000/
echo.
pause
