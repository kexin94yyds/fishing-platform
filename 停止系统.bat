@echo off
setlocal
chcp 65001 >nul
set "PROJECT_ROOT=%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%scripts\stop-windows.ps1"
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" pause

endlocal & exit /b %RESULT%
