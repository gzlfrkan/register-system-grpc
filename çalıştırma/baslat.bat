@echo off
chcp 65001 >nul
title Distributed System - Node Baslat
color 0A

echo ==========================================
echo   DISTRIBUTED SYSTEM - COOL VERSION v4.0
echo   Network Auto-Discovery
echo ==========================================
echo.

cd /d "c:\Users\cooll\Desktop\sistem programlama_cool\distributed-disk-register"

echo [1/2] Proje derleniyor...
if exist target rmdir /s /q target
call .\mvnw.cmd clean compile -q
if %errorlevel% neq 0 (
    echo.
    echo [HATA] Derleme basarisiz!
    pause
    exit /b 1
)

echo.
echo [2/2] Node baslatiliyor...
echo.
echo *** Ilk acilan bilgisayar LEADER olacak ***
echo *** Diger bilgisayarlar otomatik baglanacak ***
echo.

call .\mvnw.cmd exec:java "-Dexec.mainClass=com.example.family.NodeMain" "-Dexec.args=--mode=CLASSIC --tolerance=2"

pause
