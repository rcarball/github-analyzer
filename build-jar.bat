@echo off
REM Build a runnable JAR (Windows).
REM
REM Produces github-analyzer.jar with the application classes and the tree icons
REM (bundled at /images/). Third-party libraries are NOT copied in: the manifest's
REM Class-Path points to the lib/ folder, so keep lib/ next to the jar.
REM
REM Run it with:  java -jar github-analyzer.jar
REM from a folder that also contains lib\ and a resources\ folder with your
REM config.properties and repositories.txt (these stay external and editable).
setlocal
cd /d "%~dp0"

set OUT=build\jar-classes
set JAR=github-analyzer.jar
set MAIN=es.deusto.prog3.githubanalyzer.Main
set LIBS=lib/commons-io-2.20.0.jar lib/commons-lang3-3.19.0.jar lib/github-api-1.330.jar lib/jackson-annotations-2.20.jar lib/jackson-core-2.20.1.jar lib/jackson-databind-2.20.1.jar

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\images"

REM Compile application sources (tests excluded) against the libraries.
dir /s /b src\*.java > "%TEMP%\ga-src.txt"
javac -cp "lib/*" -d "%OUT%" @"%TEMP%\ga-src.txt"
del "%TEMP%\ga-src.txt"

REM Bundle the icons on the classpath (loaded as /images/<name> at runtime).
copy resources\images\*.png "%OUT%\images\" >nul

REM Write the manifest.
if not exist build mkdir build
> build\MANIFEST.MF echo Main-Class: %MAIN%
>> build\MANIFEST.MF echo Class-Path: %LIBS%

jar cfm "%JAR%" build\MANIFEST.MF -C "%OUT%" .
echo Built %JAR%
echo Run with: java -jar %JAR%   (keep lib\ and resources\ alongside it)

endlocal
