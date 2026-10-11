#!/bin/bash
# Prüft den QR-Leser der Handy-App auf dem PC (ohne Handy). Voraussetzungen: JDK, gcc, GLib.
# Die Testcodes erzeugt der C-Encoder von Nolphin (src/nolphin-qr.c).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
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
javac --release 11 -d "$TMP/classes" "$HERE/src/org/nolphin/handy/QrDecoder.java" "$HERE/src/org/nolphin/handy/Link.java" \
    "$HERE/test/org/nolphin/handy/QrDecoderTest.java" "$HERE/test/org/nolphin/handy/LinkTest.java"
java -cp "$TMP/classes" org.nolphin.handy.LinkTest
java -cp "$TMP/classes" org.nolphin.handy.QrDecoderTest "$TMP/drv" "$ROOT/docs/bilder/panel-handy.png" "$HERE/test/resources/echtes-foto.png"
