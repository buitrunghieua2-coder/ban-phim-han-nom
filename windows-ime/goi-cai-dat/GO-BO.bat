@echo off
chcp 65001 >nul
setlocal EnableExtensions
title Gỡ Bàn Phím Hán Nôm khỏi Weasel
echo.
echo  Gỡ kiểu gõ Hán Nôm khỏi Weasel (Weasel và các kiểu gõ khác vẫn giữ nguyên).
echo.
set "RIME="
for /f "tokens=2,*" %%A in ('reg query "HKCU\Software\Rime\Weasel" /v RimeUserDir 2^>nul ^| find /i "RimeUserDir"') do set "RIME=%%B"
if not defined RIME set "RIME=%APPDATA%\Rime"

del /q "%RIME%\hannom.schema.yaml" "%RIME%\hannom.dict.yaml" 2>nul
del /q "%RIME%\lua\hannom_telex_*.lua" "%RIME%\lua\hannom_exact_first.lua" "%RIME%\lua\hannom-telex-syllables.tsv" 2>nul
for %%F in (default.custom.yaml weasel.custom.yaml) do (
  if exist "%RIME%\%%F.truoc-hannom" (
    move /y "%RIME%\%%F.truoc-hannom" "%RIME%\%%F" >nul
  ) else (
    del /q "%RIME%\%%F" 2>nul
  )
)
echo  Đã gỡ kiểu gõ và khôi phục cấu hình cũ.
echo  (Dữ liệu học thói quen hannom.userdb vẫn để lại; xoá tay nếu muốn.)

set "WEASEL="
for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\Rime\Weasel" /v WeaselRoot 2^>nul ^| find /i "WeaselRoot"') do set "WEASEL=%%B"
if not defined WEASEL for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\WOW6432Node\Rime\Weasel" /v WeaselRoot 2^>nul ^| find /i "WeaselRoot"') do set "WEASEL=%%B"
if defined WEASEL "%WEASEL%\WeaselDeployer.exe" /deploy
echo.
pause
