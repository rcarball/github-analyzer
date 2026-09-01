@echo off
REM Compile and run the unit tests from the command line (Windows).
REM Eclipse users can just use "Run as > JUnit Test"; this is the CLI equivalent.
setlocal
cd /d "%~dp0"

set OUT=build\test-classes
set JUNIT=lib\junit-platform-console-standalone-1.14.4.jar

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

REM Collect application + test sources and compile them against the bundled libs.
dir /s /b src\*.java test\*.java > "%TEMP%\ga-sources.txt"
javac --release 17 -cp "lib/*" -d "%OUT%" @"%TEMP%\ga-sources.txt"
del "%TEMP%\ga-sources.txt"

REM Run every test on the classpath (headless: no GUI window pops up).
java -Djava.awt.headless=true -jar "%JUNIT%" execute ^
     --class-path "%OUT%" --scan-classpath --details=tree

endlocal
