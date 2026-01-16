@echo off
chcp 65001 >nul
del /q "%~dp0.node_counter" 2>nul
echo Node sayaci sifirlandi!
echo Bir sonraki yeni_uye.bat Follower #1 olarak baslayacak.
timeout /t 3
