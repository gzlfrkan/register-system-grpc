@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: Sayac dosyasinin yolu
set "COUNTER_FILE=%~dp0.node_counter"

:: Sayaci oku veya 1 ile baslat
if exist "%COUNTER_FILE%" (
    set /p NODE_NUM=<"%COUNTER_FILE%"
) else (
    set NODE_NUM=0
)

:: Sayaci artir
set /a NODE_NUM+=1

:: Yeni sayaci kaydet
echo %NODE_NUM%>"%COUNTER_FILE%"

:: I/O modunu belirle (dongusel)
set /a MOD_RESULT=NODE_NUM %% 3
if %MOD_RESULT%==1 set "IO_MODE=CLASSIC"
if %MOD_RESULT%==2 set "IO_MODE=MEMORY_MAPPED"
if %MOD_RESULT%==0 set "IO_MODE=UNBUFFERED"

:: Pencere basligi ayarla
title FOLLOWER NODE #%NODE_NUM% - %IO_MODE%
color 0B

echo ==========================================
echo   DISTRIBUTED SYSTEM - COOL VERSION
echo   Follower Node #%NODE_NUM%
echo ==========================================
echo.
echo   I/O Mode: %IO_MODE%
echo   Bu node otomatik olarak Leader'a baglanacak
echo.
echo ==========================================
echo.

cd /d "c:\Users\cooll\Desktop\sistem programlama_cool\distributed-disk-register"

call .\mvnw.cmd exec:java "-Dexec.mainClass=com.example.family.NodeMain" "-Dexec.args=--mode=%IO_MODE%"

pause
