@echo off
setlocal

set MAVEN_HOME=%~dp0.mvn\apache-maven-3.9.9
set MVN=%MAVEN_HOME%\bin\mvn.cmd

if not exist "%MVN%" (
    echo Maven not found. Run: powershell -ExecutionPolicy Bypass -File install-maven.ps1
    exit /b 1
)

"%MVN%" %*
