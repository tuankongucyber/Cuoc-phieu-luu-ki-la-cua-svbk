@echo off
setlocal
cd /d "%~dp0"
title SVBK - Danmaku Survivors
where javac >nul 2>nul
if errorlevel 1 (
    echo Can cai JDK 17+ va them Java vao PATH.
    pause
    exit /b 1
)
set "BUILD_DIR=%TEMP%\svbk-game-%RANDOM%-%RANDOM%"
mkdir "%BUILD_DIR%"
javac --release 17 -encoding UTF-8 -d "%BUILD_DIR%" *.java
if errorlevel 1 (
    echo Bien dich that bai.
    pause
    exit /b 1
)
copy /y "resources\th06-boss-data.zip" "%BUILD_DIR%\th06-boss-data.zip" >nul
if errorlevel 1 (
    echo Thieu goi du lieu Boss di kem. Hay tai lai day du du an.
    pause
    exit /b 1
)
java -cp "%BUILD_DIR%" Main
if errorlevel 1 pause
