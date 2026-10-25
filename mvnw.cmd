@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Batch Script for Windows
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "" @ECHO OFF
@SETLOCAL

SET ERROR_CODE=0

set "DIR=%~dp0"
if "%DIR:~-1%"=="\" set "DIR=%DIR:~0,-1%"

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto runMvn

echo Apache Maven not found. Downloading Maven 3.9.9...
if not exist "%USERPROFILE%\.m2\wrapper\dists" mkdir "%USERPROFILE%\.m2\wrapper\dists"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; " ^
    "$zip = Join-Path $env:TEMP 'apache-maven-3.9.9-bin.zip'; " ^
    "Write-Host 'Downloading Maven distribution...'; " ^
    "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile $zip; " ^
    "Write-Host 'Extracting Maven...'; " ^
    "Expand-Archive -Path $zip -DestinationPath (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Force; " ^
    "Remove-Item -Force $zip; " ^
    "Write-Host 'Maven downloaded and installed successfully.'"

if not exist "%MVN_CMD%" (
    echo Error: Failed to setup Maven at %MVN_CMD%
    set ERROR_CODE=1
    goto end
)

:runMvn
"%MVN_CMD%" %*
if ERRORLEVEL 1 (
    set ERROR_CODE=%ERRORLEVEL%
    goto error
)
goto end

:error
set ERROR_CODE=1

:end
@endlocal & exit /B %ERROR_CODE%

