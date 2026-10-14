# Einrichten und Aktualisieren

Das Handy mit Nolphin am PC verbinden und die App aktuell halten.

[← Zurück zur Übersicht](../README.md)

## Einrichten

1. **Am PC:** Klicken Sie in Nolphin in der Seitenleiste auf **Handy**. Es erscheint ein QR-Code. (Nolphin braucht dafür `openssl` und `glib-networking`; fehlt eines, startet der Austausch nicht und sagt es.)
2. **App installieren (einmalig, von Hand):** Der Austausch ist verschlüsselt und hat deshalb keine Webseite mehr, über die sich die App laden ließe. Kopieren Sie die Datei `nolphin-handy.apk` aufs Handy (zum Beispiel per USB-Kabel; nach der Installation von Nolphin liegt sie unter `/usr/local/share/nolphin/phone/`, sonst im Ordner `build/` dieses Projekts) und öffnen Sie sie dort. Erlauben Sie die Installation. Warnungen von Chrome, Play Protect oder Mi Protect sind bei Apps außerhalb des Play Store normal.
3. **Verbinden:** Öffnen Sie die App und tippen Sie auf **QR-Code scannen und verbinden** (oder drücken Sie lange auf das App-Symbol und wählen Sie **QR-Code scannen**). Halten Sie die Kamera der App **ruhig und gerade** auf den Code am PC, etwa 15 bis 30 cm entfernt; zum Scharfstellen tippen Sie aufs Bild. Die App liest den Code mit einem **eigenen QR-Leser** ([`QrDecoder.java`](../src/org/nolphin/handy/QrDecoder.java)), ohne Bibliothek und ohne Google-Dienste, auch bei schräg gehaltenem Handy, Spiegelungen und teilweise verdecktem Code. Sie verbindet sich sofort. Ein Scanner des Herstellers (zum Beispiel der von Xiaomi) gibt das Ergebnis nicht an die App zurück und wird nicht benutzt.
4. **Wenn das Scannen nicht klappt:** Kopieren Sie am PC den Link (Knopf neben dem Link im Panel) und tippen Sie in der App auf **Link aus der Zwischenablage einfügen**. Ein frisch kopierter Link wird beim Öffnen der App auch von selbst angeboten.
5. **Wichtig:** Handy und PC müssen im **selben WLAN** sein. In einem anderen Netz, zum Beispiel einem Hotspot, erreicht die App den PC nicht.

**Woran die App den richtigen PC erkennt:** Der QR-Code enthält den Schlüssel und den **Fingerabdruck** des Zertifikats, das Nolphin für diesen Austausch erzeugt. Die App verbindet sich nur mit genau diesem Zertifikat. Passt es nicht (neuer Code am PC, oder jemand gibt sich als Ihr PC aus), meldet sie „Das Zertifikat des PCs passt nicht zum QR-Code“ und sendet nichts. Ein Link, der **von außen** kommt (Kamera-App, Browser, andere App), verbindet erst nach einer Rückfrage mit der Anzeige der PC-Adresse; bestätigen Sie nur, wenn Sie den Code gerade selbst an Ihrem PC angezeigt haben. Links der alten, unverschlüsselten Version lehnt die App ab. Zeigen Sie den QR-Code nur Ihrem eigenen Handy: Er enthält den Schlüssel.

Der Schlüssel im Link gilt, solange der Austausch am PC läuft, höchstens eine begrenzte Zeit. Nach einem Neustart von Nolphin oder bei einem neuen Code (der auch ein neues Zertifikat bringt) scannen Sie einfach erneut. Beim ersten Verbinden lädt die App vorhandene Dateien nicht. Neue Dateien landen in `Download/Nolphin`.

## Unterwegs ohne WLAN: per USB-Kabel

Ohne gemeinsames WLAN (zum Beispiel in der Bahn) geht der Austausch auch über das **USB-Kabel**. Das Kabel bildet dann ein eigenes kleines Netz zwischen Handy und PC (USB-Tethering). Verschlüsselung und QR-Code bleiben wie sonst.

1. Kabel anstecken (ein Kabel, das Daten überträgt).
2. Am Handy **USB-Tethering einschalten** (Einstellungen → Verbindung und Freigabe → USB-Tethering; bei Xiaomi oder per Tipp auf die USB-Meldung). Eine App darf das nicht selbst einschalten; „USB-Tethering öffnen“ auf der Startseite führt direkt zur Einstellung.
3. Am PC in Nolphin links auf **Handy** klicken. Gibt es ein Kabelnetz, nimmt Nolphin **dessen Adresse zuerst**; die Meldung lautet dann „Läuft über das USB-Kabel“.
4. Am Handy **QR-Code scannen und verbinden**. Die Startseite zeigt „Kabel erkannt“ und nach dem Verbinden „über Kabel“.

Die App erkennt das Kabelnetz nur und zeigt es an; sie benutzt das Kabel nicht anders als das WLAN. Während USB-Tethering an ist, ist die USB-Dateiübertragung (MTP) für den Dateimanager am PC aus; beides zugleich geht nicht.

**Geprüft** (14.10.2026, Xiaomi Redmi Note 13, Linux Mint): USB-Tethering einschalten, der PC bekommt das Netz `enx…` mit einer privaten Adresse, die App verbindet sich über diese Adresse mit dem Nolphin-Server und lädt eine Datei, bei **ausgeschaltetem WLAN am Handy**. Nicht angesehen: die neue Kabel-Anzeige in der App am Handy mit steckendem Kabel, und der Handy-Austausch in Nolphin selbst auf der Kabeladresse (der Test lief mit dem Prüfprogramm des Projekts, das denselben Server benutzt).

## Aktualisieren

Hat Nolphin am PC eine neuere App (höhere `versionCode`), zeigt die Startseite **Neue Version**. Die neue App kommt über die verschlüsselte Verbindung. Ein Tipp auf **Aktualisieren** installiert sie über die alte, ohne Deinstallieren. Die Verbindung bleibt. Beim ersten Mal erlauben Sie der App einmal „Apps installieren“. Je nach Hersteller kommen dabei zusätzliche Warnungen: Bei Xiaomi zum Beispiel erscheint eine rote „Gefahr“-Seite, auf der Sie ein Kästchen abhaken und einige Sekunden warten müssen, bevor **OK** geht. Danach fragt Android noch einmal „Möchtest du diese App aktualisieren?“.

**Geprüft** (13.10.2026, Xiaomi): Die Startseite zeigte „Neue Version 4.31“, die Berechtigung und die Rückfrage erschienen wie beschrieben, und die App wurde über die alte installiert (Datum der Erstinstallation blieb, der Synchronisationsdienst lief danach weiter). Nicht angesehen wurde, ob die Anzeige „Verbunden“ nach dem Update sofort wieder erschien.

Eine neue Version bereitstellen:

1. `versionCode` in [`AndroidManifest.xml`](../AndroidManifest.xml) erhöhen.
2. `build.sh` ausführen.
3. Die APK nach `~/.local/share/nolphin/phone/nolphin-handy.apk` kopieren, oder Nolphin installieren (`sudo ninja -C build install` im Nolphin-Ordner).

**Wichtig:** Nolphin nimmt **zuerst** die Datei aus `~/.local/share/nolphin/phone/` und erst danach die installierte aus `/usr/local/share/nolphin/phone/`. Liegt im Nutzerordner eine ältere APK, liefert der PC diese aus, und die App zeigt nie „Neue Version“, selbst wenn Sie Nolphin neu installiert haben. Räumen Sie die alte Datei dort weg oder ersetzen Sie sie durch die neue.
