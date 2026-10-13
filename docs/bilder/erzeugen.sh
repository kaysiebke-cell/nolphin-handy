#!/bin/bash
# Erzeugt die beiden GIFs und das Titelbild der README aus quelle.html und titelbild-quelle.html. Braucht Chrome oder Chromium und Python mit Pillow.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
SEITEN="start uebersicht raster liste sortieren dokumente auswahl einfuegen zweifenster entpacken umbenennen loeschen papierkorb analyse vompc vompcauswahl"
CHROME="$(command -v google-chrome || command -v chromium || command -v chromium-browser)"
for t in hell dunkel; do
  for s in $SEITEN; do
    H=740
    "$CHROME" --headless=new --hide-scrollbars --virtual-time-budget=6000 --force-device-scale-factor=2 \
      --window-size=360,$H --screenshot="$TMP/$s-$t.png" "file://$HERE/quelle.html#$s-$t" >/dev/null 2>&1
  done
  python3 "$HERE/gif.py" "$TMP" "$t" "$HERE/funktionen-$t.gif"
done

# Titelbild: braucht zwei Startseiten der App (hell und dunkel) neben der Quelle; sie werden nur dafür gezeichnet
for t in hell dunkel; do
  "$CHROME" --headless=new --hide-scrollbars --virtual-time-budget=6000 --force-device-scale-factor=2 \
    --window-size=360,740 --screenshot="$HERE/titel-start-$t.png" "file://$HERE/quelle.html#start-$t" >/dev/null 2>&1
done
"$CHROME" --headless=new --hide-scrollbars --default-background-color=00000000 --virtual-time-budget=6000 \
  --window-size=1600,1230 --screenshot="$HERE/titelbild.png" "file://$HERE/titelbild-quelle.html" >/dev/null 2>&1
rm -f "$HERE/titel-start-hell.png" "$HERE/titel-start-dunkel.png"
