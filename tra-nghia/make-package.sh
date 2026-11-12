#!/usr/bin/env bash
# Biên dịch (mingw-w64) và đóng gói Tra Nghĩa Hán Nôm: ra build/TraNghiaHanNom-<ver>.zip
set -euo pipefail
cd "$(dirname "$0")"
VER=${1:-1.4.4}
./build.sh
P=build/pkg/TraNghiaHanNom
rm -rf build/pkg && mkdir -p "$P/data"
cp -r goi-cai-dat/. "$P/"
cp build/TraNghiaHanNom.exe "$P/"
cp ../data/dict.txt ../data/nghia.txt "$P/data/"
cp ../data/fonts/NomNaTong-Regular.ttf "$P/"
cp ../data/fonts/LICENSE-NomNaTong.txt "$P/giay-phep/"
(cd build/pkg && zip -qr9 "../TraNghiaHanNom-$VER.zip" TraNghiaHanNom)
ls -la "build/TraNghiaHanNom-$VER.zip"
