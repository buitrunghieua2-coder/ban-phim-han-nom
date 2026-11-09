@echo off
chcp 65001 >nul
setlocal EnableExtensions
title Cài đặt Tra Nghĩa Hán Nôm
echo.
echo  ==========================================
echo    TRA NGHĨA HÁN NÔM  -  từ điển rê chuột
echo  ==========================================
echo.
set "SRC=%~dp0"
set "DST=%LOCALAPPDATA%\BanPhimHanNom\TraNghia"
if not exist "%SRC%data\nghia.txt" (
  echo  [!] Thiếu thư mục data. Hãy GIẢI NÉN toàn bộ tệp zip ra một thư mục rồi chạy lại.
  pause
  exit /b 1
)
taskkill /im TraNghiaHanNom.exe /f >nul 2>&1
ping -n 2 127.0.0.1 >nul
echo  [1/3] Chép chương trình vào %DST%
if not exist "%DST%\data" mkdir "%DST%\data"
copy /y "%SRC%TraNghiaHanNom.exe" "%DST%\" >nul
copy /y "%SRC%NomNaTong-Regular.ttf" "%DST%\" >nul
copy /y "%SRC%data\dict.txt" "%DST%\data\" >nul
copy /y "%SRC%data\nghia.txt" "%DST%\data\" >nul
if not exist "%DST%\data\nghia.txt" goto :loi
if not exist "%DST%\TraNghiaHanNom.exe" goto :loi

echo  [2/3] Tạo lối tắt trong menu Start và bật khởi động cùng Windows
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Programs')+'\Tra Nghia Han Nom.lnk'); $s.TargetPath='%DST%\TraNghiaHanNom.exe'; $s.WorkingDirectory='%DST%'; $s.Description='Tra Nghia Han Nom'; $s.Save()" >nul 2>&1
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v TraNghiaHanNom /t REG_SZ /d "\"%DST%\TraNghiaHanNom.exe\"" /f >nul

echo  [3/3] Khởi động
start "" "%DST%\TraNghiaHanNom.exe"
echo.
echo  XONG! Biểu tượng chữ 𡨸 màu đỏ son đã nằm ở khay hệ thống (góc phải thanh tác vụ,
echo  có thể nằm trong mũi tên ^^ ). Rê chuột lên một chữ Hán/Nôm và giữ yên nửa giây.
echo.
echo  - Ctrl+Alt+N : bật / tắt nhanh
echo  - Nhấp chuột vào biểu tượng để đổi cài đặt.
echo.
pause
exit /b 0
:loi
echo  [!] Không chép được tệp. Hãy đóng Tra Nghĩa Hán Nôm (nếu đang chạy) rồi thử lại.
pause
exit /b 1
