#!/bin/bash
# Prüft den QR-Leser der Handy-App auf dem PC (ohne Handy). Voraussetzungen: JDK, gcc, GLib.
# Die Testcodes erzeugt der C-Encoder von Nolphin (src/nolphin-qr.c).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# Nolphin (PC-Teil) liegt im Nachbarordner; mit NOLPHIN_ROOT lässt sich ein anderer Ort angeben
ROOT="${NOLPHIN_ROOT:-$HERE/../nolphin-file-manager-master}"
[ -f "$ROOT/src/nolphin-qr.c" ] || { echo "Nolphin-Quelltext nicht gefunden: $ROOT (NOLPHIN_ROOT setzen)" >&2; exit 2; }
ROOT="$(cd "$ROOT" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/drv.c" <<'C'
#include "nolphin-qr.h"
#include <stdio.h>
#include <stdlib.h>
int main (int argc, char **argv)
{
	NolphinQr q;
	if (argc < 3 || !nolphin_qr_encode (argv[1], atoi (argv[2]), &q)) return 1;
	for (int y = 0; y < q.size; y++) {
		for (int x = 0; x < q.size; x++) putchar (nolphin_qr_get (&q, x, y) ? '1' : '0');
		putchar ('\n');
	}
	return 0;
}
C
CONFIG_DIR="$ROOT/build"
[ -f "$CONFIG_DIR/config.h" ] || CONFIG_DIR="$ROOT"
gcc -I"$ROOT/src" -I"$CONFIG_DIR" $(pkg-config --cflags glib-2.0) "$TMP/drv.c" "$ROOT/src/nolphin-qr.c" \
    -o "$TMP/drv" $(pkg-config --libs glib-2.0)
# RemoteFile verweist auf android.net.Uri (nur in einer Methode, die die Tests nicht aufrufen): android.jar nur zum Übersetzen
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
AJAR="$(ls -d "$SDK"/platforms/android-[0-9][0-9]/android.jar 2>/dev/null | sort -V | tail -1 || true)"
javac --release 11 ${AJAR:+-cp "$AJAR"} -d "$TMP/classes" "$HERE/src/org/nolphin/handy/QrDecoder.java" "$HERE/src/org/nolphin/handy/Link.java" \
    "$HERE/src/org/nolphin/handy/FileOps.java" "$HERE/test/org/nolphin/handy/QrDecoderTest.java" \
    "$HERE/test/org/nolphin/handy/LinkTest.java" "$HERE/test/org/nolphin/handy/FileOpsTest.java" \
    "$HERE/src/org/nolphin/handy/SortOrder.java" "$HERE/src/org/nolphin/handy/PlaceLists.java" "$HERE/test/org/nolphin/handy/SortTest.java" \
    "$HERE/src/org/nolphin/handy/WidgetModel.java" "$HERE/src/org/nolphin/handy/FetchGuard.java" "$HERE/src/org/nolphin/handy/RemoteFile.java" "$HERE/test/org/nolphin/handy/WidgetTest.java" "$HERE/src/org/nolphin/handy/UiScale.java" "$HERE/src/org/nolphin/handy/ViewModes.java" "$HERE/src/org/nolphin/handy/FileUris.java" "$HERE/test/org/nolphin/handy/ViewTest.java" \
    "$HERE/src/org/nolphin/handy/ZipOps.java" "$HERE/src/org/nolphin/handy/BatchRename.java" "$HERE/test/org/nolphin/handy/ZipBatchTest.java" \
    "$HERE/src/org/nolphin/handy/StorageScan.java" "$HERE/test/org/nolphin/handy/StorageScanTest.java" \
    "$HERE/src/org/nolphin/handy/TrashOps.java" "$HERE/test/org/nolphin/handy/TrashTest.java" \
    "$HERE/src/org/nolphin/handy/Secure.java" "$HERE/test/org/nolphin/handy/SecureTest.java" \
    "$HERE/src/org/nolphin/handy/PcCheck.java" "$HERE/test/org/nolphin/handy/LiveTest.java"
java -cp "$TMP/classes" org.nolphin.handy.LinkTest
java -cp "$TMP/classes" org.nolphin.handy.FileOpsTest
java -cp "$TMP/classes" org.nolphin.handy.SortTest
java -cp "$TMP/classes" org.nolphin.handy.ViewTest
java -cp "$TMP/classes" org.nolphin.handy.WidgetTest
java -cp "$TMP/classes" org.nolphin.handy.ZipBatchTest
java -cp "$TMP/classes" org.nolphin.handy.StorageScanTest
java -cp "$TMP/classes" org.nolphin.handy.TrashTest
# Verschlüsselte Verbindung: zwei Zertifikate mit keytool, Prüfung der Fingerabdrücke
mkdir -p "$TMP/zert"
for n in eins zwei; do
  keytool -genkeypair -alias "$n" -keyalg EC -groupname secp256r1 -dname "CN=nolphin-$n" -ext "san=ip:127.0.0.1" \
      -validity 2 -storetype PKCS12 -keystore "$TMP/zert/$n.p12" -storepass geheim -keypass geheim >/dev/null 2>&1
done
java -cp "$TMP/classes" org.nolphin.handy.SecureTest "$TMP/zert"
# Gegen den echten Nolphin-Server (C, GnuTLS): Fingerabdruck und Verbindung von beiden Seiten
gcc -I"$ROOT/src" -I"$CONFIG_DIR" $(pkg-config --cflags glib-2.0 gio-2.0) "$HERE/test/phone-drv.c" \
    "$ROOT/src/nolphin-phone-server.c" "$ROOT/src/nolphin-phone-tls.c" -o "$TMP/phone-drv" $(pkg-config --libs glib-2.0 gio-2.0)
java -cp "$TMP/classes" org.nolphin.handy.LiveTest "$TMP/phone-drv"
java -cp "$TMP/classes" org.nolphin.handy.QrDecoderTest "$TMP/drv" "$ROOT/docs/bilder/panel-handy.png" "$HERE/test/resources/testfoto-verwischt.png"
