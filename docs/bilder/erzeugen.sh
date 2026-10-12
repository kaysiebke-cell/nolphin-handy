#!/bin/bash
# Erzeugt die beiden GIFs der README (hell und dunkel) aus quelle.html. Braucht Chrome oder Chromium und Python mit Pillow.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
SEITEN="start uebersicht liste sortieren dokumente auswahl einfuegen zweifenster entpacken umbenennen loeschen papierkorb analyse vompc vompcauswahl"
CHROME="$(command -v google-chrome || command -v chromium || command -v chromium-browser)"
for t in hell dunkel; do
  for s in $SEITEN; do
    H=740
    "$CHROME" --headless=new --hide-scrollbars --virtual-time-budget=6000 --force-device-scale-factor=2 \
      --window-size=360,$H --screenshot="$TMP/$s-$t.png" "file://$HERE/quelle.html#$s-$t" >/dev/null 2>&1
  done
  python3 "$HERE/gif.py" "$TMP" "$t" "$HERE/funktionen-$t.gif"
done
