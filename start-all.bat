@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo  Wedding Microservices - Spring Boot
echo  Start all 6 services
echo ==========================================
set JVM=-Xms48m -Xmx180m -XX:MaxMetaspaceSize=90m -XX:+UseSerialGC

start "wedding-gateway" java %JVM% -jar "wedding-gateway\target\wedding-gateway-1.0.0.jar"
start "wedding-auth"    java %JVM% -jar "wedding-auth\target\wedding-auth-1.0.0.jar"
start "wedding-package" java %JVM% -jar "wedding-package\target\wedding-package-1.0.0.jar"
start "wedding-booking" java %JVM% -jar "wedding-booking\target\wedding-booking-1.0.0.jar"
start "wedding-video"   java %JVM% -jar "wedding-video\target\wedding-video-1.0.0.jar"
start "wedding-message" java %JVM% -jar "wedding-message\target\wedding-message-1.0.0.jar"

echo.
echo  All services started. Gateway: http://localhost:3000
echo  Stop all: run stop-all.bat
endlocal
