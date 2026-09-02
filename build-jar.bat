@echo off
REM Build a SELF-CONTAINED runnable JAR (Windows).
REM
REM Produces github-analyzer.jar with the application classes, all GUI icons AND
REM all third-party libraries bundled inside — no lib\ folder needed at runtime.
REM
REM Run it (from any directory) with:
REM     java -jar github-analyzer.jar
REM Keep a resources\ folder (with your config.properties and repositories.txt)
REM next to the jar — those files stay EXTERNAL and editable (never bundled).
setlocal
cd /d "%~dp0"

set OUT=build\jar-classes
set STAGE=build\fat
set JAR=github-analyzer.jar
set MAIN=es.deusto.prog3.githubanalyzer.Main
set LIB=%CD%\lib

if exist "%OUT%" rmdir /s /q "%OUT%"
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%OUT%"
mkdir "%STAGE%"

REM 1) Compile application sources (tests excluded).
dir /s /b src\*.java > "%TEMP%\ga-src.txt"
javac --release 17 -cp "lib/*" -d "%OUT%" @"%TEMP%\ga-src.txt"
del "%TEMP%\ga-src.txt"

REM 2) Unpack the runtime dependencies into the staging dir (JUnit excluded).
pushd "%STAGE%"
for %%J in ("%LIB%\commons-io-*.jar" "%LIB%\commons-lang3-*.jar" "%LIB%\github-api-*.jar" "%LIB%\jackson-*.jar") do jar xf "%%J"
popd

REM 3) Drop artifacts that don't belong in a merged, non-modular classpath jar.
del /q "%STAGE%\META-INF\MANIFEST.MF" "%STAGE%\module-info.class" 2>nul
del /q "%STAGE%\META-INF\*.SF" "%STAGE%\META-INF\*.DSA" "%STAGE%\META-INF\*.RSA" "%STAGE%\META-INF\*.EC" 2>nul

REM 4) Add our compiled classes and all GUI icons.
xcopy /e /i /y /q "%OUT%\*" "%STAGE%\" >nul
mkdir "%STAGE%\images" 2>nul
copy /y resources\images\*.png "%STAGE%\images\" >nul

REM 5) Package a single self-contained runnable jar (Main-Class via -e).
jar cfe "%JAR%" "%MAIN%" -C "%STAGE%" .
echo Built self-contained %JAR%
echo Run with: java -jar %JAR%   (only a resources\ folder needs to sit next to it)

endlocal
