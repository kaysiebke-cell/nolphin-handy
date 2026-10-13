# Umbau 4.28: Oberfläche und Dateiverwaltung

Stand: Version 4.28 (versionCode 46). Dieser Bericht nennt, was geändert wurde und was geprüft ist.

## Geänderte und neue Dateien

* Neu: `FilesProvider.java`, `FileUris.java`, `ViewModes.java`, `SquareImage.java`, `res/drawable/ic_menu.xml`, `test/…/ViewTest.java`.
* Geändert: `ToPcScreen.java` (Kopf, Menü, Ansichtsdialog, Kacheln, Zeilen, Suche, Öffnen mit), `MainActivity.java` (schmale Leiste, zentrales Menü), `Screen.java`, `ToPcTabs.java` (Menüeinträge), `SortOrder.java`, `Conn.java`, `Thumbs.java` (Musikdaten), `AndroidManifest.xml` (Anbieter, Version), App-Symbole (`res/mipmap-*`, `icon-512.png`, `docs/bilder/app-symbol.png`), `test.sh`, `README.md`.

## Umgesetzt

* **Logo:** nur das Handy mit dem grünen Logo, ohne den umgebenden Rahmen (transparenter Hintergrund).
* **Obere Leiste:** 48 dp hoch, App-Symbol, Name, Status und ein Menü (☰). Im Menü: Suchen, Neuer Ordner, Ansicht und Sortierung, Zwei Fenster, Einstellungen. Die Zeile „Sortiert nach …“ unter der Pfadleiste entfällt (mehr Platz für Dateien).
* **Ansicht:** Liste / Details, Raster, Kompakt; Standard- oder große Vorschau (unabhängig); Dateinamen ausblenden (nur Raster); ausgeblendete Dateien anzeigen.
* **Sortierung:** Keine, Name, Größe, Datum, Typ, je auf- und absteigend; Standard Datum absteigend, Ordner zuerst.
* **Symbolgröße im Raster:** Der Rahmen der Kacheln ist weg, Innenabstände sind klein (2 dp außen, 6 dp um Symbole, 0 um Fotos). Die Symbolfläche ist quadratisch und so breit wie die Kachel (vorher fest 64 dp hoch in einer 148 dp hohen, gerahmten Kachel). Spalten richten sich nach der Breite (mindestens 84 dp, groß 128 dp).
* **Fotos und Cover:** Vorschaubilder füllen die Kachel, Größe richtet sich nach der Vorschaueinstellung, Zwischenspeicher wie bisher. Ohne Vorschau erscheint das Dateisymbol. Musik zeigt Titel, Interpret und Album, wenn vorhanden.
* **Suche:** eingeklappt, Lupe öffnet und schließt, Kreuz leert, „Nichts gefunden für …“ bei null Treffern.
* **Neuer Ordner:** im Menü; leere Namen, Namen mit „/“, „.“, „..“ und vorhandene Namen werden mit Meldung abgelehnt (bestand schon, `FileOps.newFolder`).
* **Externe Apps:** „Öffnen mit …“ und „Bearbeiten mit …“ über einen eigenen `FilesProvider` (`content://`, nur mit Berechtigung für die eine Datei, nur unter `/storage`, nie `Android/data` oder `Android/obb`). Danach lädt die Liste neu; die App meldet, ob die Datei geändert wurde.

## Speicherung

Alle Wahlen liegen in den App-Einstellungen (`Conn`): `viewmode`, `largepreview`, `hidenames`, `showhidden`, `sortkey`/`sortasc`/`foldersfirst`. Eine frühere Einstellung Kacheln/Liste wird übernommen, wenn noch keine neue gespeichert ist.

## Geprüft (auf dem PC)

* `./build.sh`: Bau erfolgreich.
* `./test.sh`: alle Prüfungen bestanden (Link 55, Dateioperationen 60, Sortierung 25, Ansicht und Öffnen mit 169, ZIP 52, Speicheranalyse 39, Papierkorb 39, verschlüsselte Verbindung 16, Nolphin-Server 7, QR-Leser 181).

## Nicht geprüft / Grenzen

* **Die neue Oberfläche wurde noch nicht auf einem Handy angesehen.** Aussehen, Spaltenzahl, große Systemschrift, Touch-Ziele, hell und dunkel sind nur aus dem Code abgeleitet, nicht gemessen.
* Das Speichern der Einstellungen (SharedPreferences) und der Datei-Anbieter sind nicht in den PC-Tests (benötigen Android); getestet sind die Regeln dahinter.
* „Öffnen mit …“ und „Bearbeiten mit …“ wurden mit keiner echten App ausprobiert. Ob eine App die Originaldatei ändert oder eine Kopie speichert, entscheidet die App.
* Das Launcher-Symbol kann je nach Hersteller-Oberfläche trotzdem in eine eigene Form oder auf einen Hintergrund gesetzt werden.
* Albumbilder und Musikdaten stammen aus den Dateien; ohne Einträge bleibt der Dateiname.
* „Dateinamen ausblenden“ wirkt nur im Raster (in Liste und Kompakt wären Zeilen ohne Namen nicht benutzbar).
