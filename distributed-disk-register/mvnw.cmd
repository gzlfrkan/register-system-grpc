@REM Maven Wrapper Script for Windows
@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_HOME=%~dp0.mvn\wrapper\apache-maven-%MAVEN_VERSION%
set PATH=%MAVEN_HOME%\bin;%PATH%

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven not found. Downloading...
    
    if not exist "%~dp0.mvn\wrapper" mkdir "%~dp0.mvn\wrapper"
    
    powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%~dp0.mvn\wrapper\maven.zip'"
    
    powershell -Command "Expand-Archive -Path '%~dp0.mvn\wrapper\maven.zip' -DestinationPath '%~dp0.mvn\wrapper' -Force"
    
    del "%~dp0.mvn\wrapper\maven.zip"
    
    echo Maven downloaded successfully.
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
