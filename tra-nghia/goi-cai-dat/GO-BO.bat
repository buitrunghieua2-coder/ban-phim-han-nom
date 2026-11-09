@echo off
chcp 65001 >nul
title Gỡ bỏ Tra Nghĩa Hán Nôm
set "DST=%LOCALAPPDATA%\BanPhimHanNom\TraNghia"
taskkill /im TraNghiaHanNom.exe /f >nul 2>&1
ping -n 2 127.0.0.1 >nul
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v TraNghiaHanNom /f >nul 2>&1
reg delete "HKCU\Software\BanPhimHanNom\TraNghia" /f >nul 2>&1
del /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Tra Nghia Han Nom.lnk" >nul 2>&1
rmdir /s /q "%DST%" >nul 2>&1
echo.
echo  Đã gỡ Tra Nghĩa Hán Nôm. (Bàn phím Hán Nôm trên Weasel không bị ảnh hưởng.)
echo.
pause
