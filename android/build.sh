#!/usr/bin/env bash
# Build Bàn Phím Hán Nôm without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-34/android.jar
OUT=build
KS=${KEYSTORE:?set KEYSTORE to the release keystore (never commit it)}
KS_PASS=${KS_PASS:?set KS_PASS}

rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex}

# shared data lives in ../data; copy it into assets/ (ignored by git)
cp ../data/dict.txt ../data/nghia.txt assets/
cp ../data/fonts/NomNaTong-Regular.ttf assets/nom.ttf

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
    -A assets --java "$OUT/gen" "$OUT/res.zip" --min-sdk-version 21 --target-sdk-version 34 \
    --version-code 4 --version-name 1.3

javac -nowarn -encoding UTF-8 --release 8 -classpath "$JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v -E "^Picked up|warning: \[options\]|^[0-9]+ warning" || true
[ -f "$OUT/classes/vn/hannom/keyboard/KeyboardService.class" ] || { echo "javac failed"; exit 1; }

"$BT/d8" --release --min-api 21 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias hannom \
    --out "$OUT/BanPhimHanNom-1.3.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/BanPhimHanNom-1.3.apk" | head -3
ls -la "$OUT/BanPhimHanNom-1.3.apk"
