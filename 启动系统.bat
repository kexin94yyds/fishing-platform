@echo off
setlocal
chcp 65001 >nul
set "PROJECT_ROOT=%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%scripts\start-windows.ps1"
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" (
  echo.
  echo Startup failed. Review the message above and the .runtime logs.
  pause
)

endlocal & exit /b %RESULT%
