@echo off
cd /d "%~dp0"
echo Compiling RailReserve...
javac RailwayReservationSystem.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Check the error messages above.
  pause
  exit /b 1
)
echo.
echo Starting RailReserve at http://localhost:8080
java RailwayReservationSystem
pause
