#!/usr/bin/env bash
# Đóng gói Bàn phím Hán Nôm cho Windows (Weasel): ra build/BanPhimHanNom-Windows-<ver>.zip
set -euo pipefail
cd "$(dirname "$0")"
VER=${1:-1.2}
rm -rf build && mkdir -p build/HanNom-Windows
cp -r goi-cai-dat/. build/HanNom-Windows/
mkdir -p build/HanNom-Windows/phong-chu
cp ../data/fonts/NomNaTong-Regular.ttf ../data/fonts/LICENSE-NomNaTong.txt build/HanNom-Windows/phong-chu/
(cd build && zip -qr9 "BanPhimHanNom-Windows-$VER.zip" HanNom-Windows)
ls -la "build/BanPhimHanNom-Windows-$VER.zip"
