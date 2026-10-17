@echo off
title Vampire Survivors Game
echo Dang bien dich va khoi chay game...

"C:\Program Files\Java\jdk-27\bin\javac.exe" *.java
if %errorlevel% equ 0 (
    "C:\Program Files\Java\jdk-27\bin\java.exe" Main
) else (
    echo [LÔI] Bien dich thất bại! Kiem tra lai code Java.
    pause
)