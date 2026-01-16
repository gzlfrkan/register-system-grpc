@echo off
chcp 65001 >nul
title Komut Gonderici - SET/GET
color 0E

echo ==========================================
echo   DISTRIBUTED SYSTEM - KOMUT GONDERICI
echo ==========================================
echo.

:menu
echo   [1] SET - Veri kaydet
echo   [2] GET - Veri oku
echo   [3] Interaktif mod (birden fazla komut)
echo   [4] Cikis
echo.
set /p SECIM="Seciminiz (1-4): "

if "%SECIM%"=="1" goto set_komutu
if "%SECIM%"=="2" goto get_komutu
if "%SECIM%"=="3" goto interaktif
if "%SECIM%"=="4" exit
goto menu

:set_komutu
echo.
set /p KEY="Key (numara): "
set /p VALUE="Value (deger): "
echo.
echo Gonderiliyor: SET %KEY% %VALUE%
powershell -Command "$c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 6666); $s = $c.GetStream(); $w = New-Object System.IO.StreamWriter($s); $r = New-Object System.IO.StreamReader($s); $w.AutoFlush = $true; $w.WriteLine('SET %KEY% %VALUE%'); Write-Host ('Sonuc: ' + $r.ReadLine()) -ForegroundColor Cyan; $c.Close()"
echo.
goto menu

:get_komutu
echo.
set /p KEY="Key (numara): "
echo.
echo Gonderiliyor: GET %KEY%
powershell -Command "$c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 6666); $s = $c.GetStream(); $w = New-Object System.IO.StreamWriter($s); $r = New-Object System.IO.StreamReader($s); $w.AutoFlush = $true; $w.WriteLine('GET %KEY%'); Write-Host ('Sonuc: ' + $r.ReadLine()) -ForegroundColor Cyan; $c.Close()"
echo.
goto menu

:interaktif
echo.
echo Interaktif mod baslatiliyor...
echo (Komutlari yazin, cikmak icin 'exit')
echo.
powershell -Command ^
  "$client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 6666); ^
   $stream = $client.GetStream(); ^
   $writer = New-Object System.IO.StreamWriter($stream); ^
   $reader = New-Object System.IO.StreamReader($stream); ^
   $writer.AutoFlush = $true; ^
   Write-Host 'Baglanti kuruldu!' -ForegroundColor Green; ^
   while ($true) { ^
     $cmd = Read-Host '>'; ^
     if ($cmd -eq 'exit') { $client.Close(); break } ^
     $writer.WriteLine($cmd); ^
     Write-Host $reader.ReadLine() -ForegroundColor Cyan ^
   }"
echo.
goto menu
