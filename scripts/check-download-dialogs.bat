@echo off
REM Optional real-window regression check; JDK 17+.
"%~dp0run-java-checks.bat" --desktop "%~1"
