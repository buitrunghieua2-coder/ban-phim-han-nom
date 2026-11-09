#!/bin/sh
set -e
cd "$(dirname "$0")"
mkdir -p build
x86_64-w64-mingw32-windres -O coff -i res/app.rc -o build/app.res
x86_64-w64-mingw32-g++ -std=c++17 -O2 -municode -mwindows -DUNICODE -D_UNICODE -Wall \
  src/main.cpp src/core.cpp build/app.res -o build/TraNghiaHanNom.exe \
  -static -static-libgcc -static-libstdc++ \
  -ld2d1 -ldwrite -lole32 -loleaut32 -luuid -lshell32 -lshcore -ldwmapi -luser32 -lgdi32 -ladvapi32
x86_64-w64-mingw32-strip build/TraNghiaHanNom.exe
ls -la build/TraNghiaHanNom.exe
