#!/usr/bin/env bash
# Build Bàn Phím Hán Nôm without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar
OUT=build
KS=${KEYSTORE:?set KEYSTORE to the release keystore (never commit it)}
KS_PASS=${KS_PASS:?set KS_PASS}

rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex}

# shared data lives in ../data; copy it into assets/ (ignored by git)
cp ../data/dict.txt ../data/nghia.txt assets/
cp ../data/fonts/NomNaTong-Regular.ttf assets/nom.ttf

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
    -A assets --java "$OUT/gen" "$OUT/res.zip" --min-sdk-version 21 --target-sdk-version 36 \
    --version-code 5 --version-name 1.4

javac -nowarn -encoding UTF-8 --release 8 -classpath "$JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v -E "^Picked up|warning: \[options\]|^[0-9]+ warning" || true
[ -f "$OUT/classes/vn/hannom/keyboard/KeyboardService.class" ] || { echo "javac failed"; exit 1; }

"$BT/d8" --release --min-api 21 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias hannom \
    --out "$OUT/BanPhimHanNom-1.4.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/BanPhimHanNom-1.4.apk" | head -3
ls -la "$OUT/BanPhimHanNom-1.4.apk"

# ---- Android App Bundle for Google Play (needs bundletool; set BUNDLETOOL="java -jar bundletool.jar" or a -cp command)
if [ -n "${BUNDLETOOL:-}" ]; then
    "$BT/aapt2" link --proto-format -o "$OUT/proto.apk" -I "$JAR" --manifest AndroidManifest.xml \
        -A assets "$OUT/res.zip" --min-sdk-version 21 --target-sdk-version 36 \
        --version-code 5 --version-name 1.4
    M="$OUT/module" && rm -rf "$M" && mkdir -p "$M/manifest" "$M/dex"
    (cd "$M" && unzip -q ../proto.apk)
    mv "$M/AndroidManifest.xml" "$M/manifest/AndroidManifest.xml"
    cp "$OUT/dex/classes.dex" "$M/dex/"
    (cd "$M" && zip -q -r ../base.zip manifest dex res assets resources.pb)
    rm -f "$OUT/BanPhimHanNom-1.4.aab"
    $BUNDLETOOL build-bundle --modules="$OUT/base.zip" --output="$OUT/BanPhimHanNom-1.4.aab"
    jarsigner -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -sigalg SHA256withRSA -digestalg SHA-256 "$OUT/BanPhimHanNom-1.4.aab" hannom >/dev/null
    jarsigner -verify "$OUT/BanPhimHanNom-1.4.aab" | tail -1
    ls -la "$OUT/BanPhimHanNom-1.4.aab"
fi
