@echo off
setlocal
cd /d "%~dp0"
echo Stopping all wedding microservices...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":3000 :3001 :3002 :3003 :3004 :3005 "') do (
  taskkill /f /pid %%p 2>nul
)
echo Done.
endlocal
