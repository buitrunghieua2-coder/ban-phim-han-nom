@echo off
chcp 65001 >nul
setlocal EnableExtensions
title Cài đặt Bàn Phím Hán Nôm cho Weasel
echo.
echo  ==========================================
echo    BÀN PHÍM HÁN NÔM 漢喃  -  cài cho Windows
echo  ==========================================
echo.

set "SRC=%~dp0"

rem --- 1. Tìm Weasel ---------------------------------------------------------
set "WEASEL="
for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\Rime\Weasel" /v WeaselRoot 2^>nul ^| find /i "WeaselRoot"') do set "WEASEL=%%B"
if not defined WEASEL for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\WOW6432Node\Rime\Weasel" /v WeaselRoot 2^>nul ^| find /i "WeaselRoot"') do set "WEASEL=%%B"
if not defined WEASEL for /d %%D in ("%ProgramFiles%\Rime\weasel-*" "%ProgramFiles(x86)%\Rime\weasel-*") do set "WEASEL=%%~fD"
if not defined WEASEL (
  echo  [!] Chưa thấy Weasel ^(Tiểu Lang Hào^) trên máy.
  echo      Hãy cài Weasel trước: https://rime.im  hoặc  https://github.com/rime/weasel/releases
  echo      rồi chạy lại tệp này.
  echo.
  pause
  exit /b 1
)
echo  [1/4] Đã tìm thấy Weasel: %WEASEL%

rem --- 2. Thư mục dữ liệu Rime (có thể đã được đổi trong cài đặt Weasel) ------
set "RIME="
for /f "tokens=2,*" %%A in ('reg query "HKCU\Software\Rime\Weasel" /v RimeUserDir 2^>nul ^| find /i "RimeUserDir"') do set "RIME=%%B"
if not defined RIME set "RIME=%APPDATA%\Rime"
if not exist "%RIME%" mkdir "%RIME%"
if not exist "%RIME%\lua" mkdir "%RIME%\lua"

for %%F in (default.custom.yaml weasel.custom.yaml) do (
  if exist "%RIME%\%%F" if not exist "%RIME%\%%F.truoc-hannom" copy /y "%RIME%\%%F" "%RIME%\%%F.truoc-hannom" >nul
)
copy /y "%SRC%rime\*.yaml" "%RIME%\" >nul || goto :loi
copy /y "%SRC%rime\lua\*.*" "%RIME%\lua\" >nul || goto :loi
echo  [2/4] Đã chép kiểu gõ Hán Nôm vào: %RIME%
echo        ^(cấu hình cũ, nếu có, được giữ lại với đuôi .truoc-hannom^)

rem --- 3. Phông Nôm Na Tống (cài cho tài khoản này, không cần quyền quản trị) --
set "FONTDIR=%LOCALAPPDATA%\Microsoft\Windows\Fonts"
if not exist "%FONTDIR%" mkdir "%FONTDIR%"
copy /y "%SRC%phong-chu\NomNaTong-Regular.ttf" "%FONTDIR%\" >nul
reg add "HKCU\Software\Microsoft\Windows NT\CurrentVersion\Fonts" /v "Nom Na Tong (TrueType)" /t REG_SZ /d "%FONTDIR%\NomNaTong-Regular.ttf" /f >nul
echo  [3/4] Đã cài phông Nôm Na Tống.

rem --- 4. Triển khai lại -------------------------------------------------------
echo  [4/4] Đang triển khai lại Weasel (mất khoảng 10-30 giây)...
"%WEASEL%\WeaselDeployer.exe" /deploy
echo.
echo  XONG! Cách dùng:
echo    - Bấm Windows + Space, chọn bộ gõ Weasel ^(小狼毫^).
echo    - Gõ Telex: nguoi, vieejtnam, ddi ...  Phím cách để chọn chữ đầu.
echo    - Nếu chưa thấy "Hán Nôm 漢喃", bấm F4 hoặc Ctrl+` để chọn.
echo    - Một số ứng dụng cần mở lại mới thấy phông Nôm Na Tống.
echo.
pause
exit /b 0

:loi
echo.
echo  [!] Không chép được tệp vào %RIME%.
echo      Hãy giải nén cả thư mục trước khi chạy, rồi thử lại.
pause
exit /b 1
