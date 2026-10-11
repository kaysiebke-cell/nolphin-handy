#!/bin/bash
# Baut die Handy-App (APK) nur mit den Werkzeugen des Android-SDK, ohne Gradle und ohne Bibliotheken.
# Voraussetzungen: JDK (javac, keytool) und das Android-SDK (android.jar, aapt2, d8, zipalign, apksigner).
# Aufruf: android/build.sh   Ergebnis: android/build/nolphin-handy.apk
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[ -d "$SDK" ] || { echo "Android-SDK nicht gefunden (ANDROID_HOME setzen): $SDK" >&2; exit 2; }

BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
# Die jüngste Plattform mit ganzzahliger Version (keine Vorabversionen wie 37.0)
PLATFORM="$(ls -d "$SDK"/platforms/android-[0-9][0-9] 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] && [ -n "$PLATFORM" ] || { echo "build-tools oder platforms fehlen im SDK" >&2; exit 2; }
for t in aapt2 d8 zipalign apksigner; do [ -x "$BT/$t" ] || { echo "$t fehlt in $BT" >&2; exit 2; }; done
command -v javac >/dev/null && command -v keytool >/dev/null || { echo "JDK (javac, keytool) fehlt" >&2; exit 2; }

OUT="$HERE/build"
rm -rf "$OUT" && mkdir -p "$OUT/classes"

# Eigener Signaturschlüssel, einmal erzeugt und außerhalb des Repositories abgelegt. Das Passwort steht im
# Systemschlüsselbund (secret-tool, Paket libsecret-tools), nie in einer Datei und nie auf der Kommandozeile.
command -v secret-tool >/dev/null || { echo "secret-tool fehlt: sudo apt install libsecret-tools" >&2; exit 2; }
KEYDIR="${XDG_CONFIG_HOME:-$HOME/.config}/nolphin"
KS="$KEYDIR/handy.keystore"
LEGACY="$KEYDIR/handy.keystore.pass"   # frühere Klartextdatei; wird in den Schlüsselbund übernommen und gelöscht
lookup() { secret-tool lookup service nolphin-handy account keystore 2>/dev/null; }
KS_PASS="$(lookup || true)"
if [ ! -f "$KS" ]; then
    mkdir -p "$KEYDIR" && chmod 700 "$KEYDIR"
    KS_PASS="$(head -c 24 /dev/urandom | base64 | tr -d '=+/')"
    printf '%s' "$KS_PASS" | secret-tool store --label="Nolphin Handy: Signaturschlüssel" service nolphin-handy account keystore
    [ "$(lookup)" = "$KS_PASS" ] || { echo "Passwort konnte nicht im Schlüsselbund abgelegt werden" >&2; exit 3; }
    export NOLPHIN_KS_PASS="$KS_PASS"
    keytool -genkeypair -keystore "$KS" -storepass:env NOLPHIN_KS_PASS -keypass:env NOLPHIN_KS_PASS \
        -alias nolphin-handy -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=Nolphin Handy" >/dev/null
elif [ -z "$KS_PASS" ]; then
    if [ -f "$LEGACY" ]; then
        KS_PASS="$(cat "$LEGACY")"
        printf '%s' "$KS_PASS" | secret-tool store --label="Nolphin Handy: Signaturschlüssel" service nolphin-handy account keystore
        if [ "$(lookup)" = "$KS_PASS" ]; then
            shred -u "$LEGACY" && echo "Passwort in den Schlüsselbund übernommen, Klartextdatei gelöscht."
        else
            echo "Passwort konnte nicht im Schlüsselbund abgelegt werden; Klartextdatei bleibt" >&2; exit 3
        fi
    else
        echo "Das Passwort des Signaturschlüssels fehlt im Schlüsselbund (service nolphin-handy)" >&2; exit 3
    fi
fi
export NOLPHIN_KS_PASS="$KS_PASS"

# Ressourcen (Symbole, Farben, Themen) übersetzen; aapt2 erzeugt dabei auch R.java
mkdir -p "$OUT/gen"
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link --manifest "$HERE/AndroidManifest.xml" -I "$PLATFORM/android.jar" \
    --min-sdk-version 29 --target-sdk-version 34 --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res.zip"

javac --release 11 -Xlint:all -cp "$PLATFORM/android.jar" -d "$OUT/classes" \
    $(find "$HERE/src" -name '*.java') $(find "$OUT/gen" -name '*.java')
"$BT/d8" --lib "$PLATFORM/android.jar" --min-api 29 --output "$OUT" $(find "$OUT/classes" -name '*.class')
(cd "$OUT" && zip -q -j base.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass env:NOLPHIN_KS_PASS --key-pass env:NOLPHIN_KS_PASS \
    --out "$OUT/nolphin-handy.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/nolphin-handy.apk"
rm -f "$OUT/base.apk" "$OUT/aligned.apk" "$OUT/res.zip" "$OUT/nolphin-handy.apk.idsig"
echo "Fertig: $OUT/nolphin-handy.apk"
