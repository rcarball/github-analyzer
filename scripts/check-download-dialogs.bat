@echo off
REM Optional real-window regression check; JDK 17+, no Python/PowerShell dependency.
setlocal EnableExtensions DisableDelayedExpansion
set "DIALOG_RESULT=1"
set "DIALOG_PROJECT_PUSHED="
set "DIALOG_JAVAC="
set "DIALOG_JAVA="
pushd "%~dp0.."
if errorlevel 1 (
    echo Could not open the project folder.
    goto finish
)
set "DIALOG_PROJECT_PUSHED=1"
set "DIALOG_PROJECT=%CD%"
REM An explicit JAVA_HOME must identify a complete JDK; never silently mix it with PATH.
if defined JAVA_HOME goto java_home
for /f "delims=" %%J in ('where javac.exe 2^>nul') do if not defined DIALOG_JAVAC set "DIALOG_JAVAC=%%~fJ"
if not defined DIALOG_JAVAC (
    echo A JDK 17 or newer is required. Set JAVA_HOME or add its bin folder to PATH.
    goto finish
)
for %%J in ("%DIALOG_JAVAC%") do set "DIALOG_JAVA=%%~dpJjava.exe"
goto check_jdk

:java_home
set "DIALOG_JAVAC=%JAVA_HOME%\bin\javac.exe"
set "DIALOG_JAVA=%JAVA_HOME%\bin\java.exe"

:check_jdk
if not exist "%DIALOG_JAVAC%" (
    echo JAVA_HOME does not contain bin\javac.exe. Select a JDK, not a JRE.
    goto finish
)
if not exist "%DIALOG_JAVA%" (
    echo The selected compiler has no matching java.exe in the same bin folder.
    goto finish
)

REM The shared Java controller owns child processes, exit codes and all temporary files.
set "GITHUB_TOKEN="
"%DIALOG_JAVA%" --source 17 "%DIALOG_PROJECT%\scripts\DownloadDialogLauncher.java" "%DIALOG_PROJECT%"
set "DIALOG_RESULT=%ERRORLEVEL%"

:finish
if defined DIALOG_PROJECT_PUSHED popd
if not "%DIALOG_RESULT%"=="0" echo Download dialog checks failed. A complete JDK 17 or newer is required; review the error above.
REM Keep results visible when started by double-click. Use --no-pause for a terminal or CI.
if /I not "%~1"=="--no-pause" pause
endlocal & exit /b %DIALOG_RESULT%
