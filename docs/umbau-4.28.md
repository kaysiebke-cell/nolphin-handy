# Umbau 4.28: Oberfläche und Dateiverwaltung

Stand: Version 4.28 (versionCode 46). Dieser Bericht nennt, was geändert wurde und was geprüft ist.

## Geänderte und neue Dateien

* Neu: `FilesProvider.java`, `FileUris.java`, `ViewModes.java`, `SquareImage.java`, `res/drawable/ic_menu.xml`, `test/…/ViewTest.java`.
* Geändert: `ToPcScreen.java` (Kopf, Menü, Ansichtsdialog, Kacheln, Zeilen, Suche, Öffnen mit), `MainActivity.java` (schmale Leiste, zentrales Menü), `Screen.java`, `ToPcTabs.java` (Menüeinträge), `SortOrder.java`, `Conn.java`, `Thumbs.java` (Musikdaten), `AndroidManifest.xml` (Anbieter, Version), App-Symbole (`res/mipmap-*`, `icon-512.png`, `docs/bilder/app-symbol.png`), `test.sh`, `README.md`.

## Umgesetzt

* **Logo:** nur das Handy mit dem grünen Logo, ohne den umgebenden Rahmen (transparenter Hintergrund).
* **Menü:** eigenes Menü im Stil der App (Karte mit Rand, große Zeilen) statt des Standard-Menüfensters des Systems.
* **Obere Leiste:** 48 dp hoch, App-Symbol, Name, Status und ein Menü (☰). Im Menü: Suchen, Neuer Ordner, Ansicht und Sortierung, Zwei Fenster, Einstellungen. Die Zeile „Sortiert nach …“ unter der Pfadleiste entfällt (mehr Platz für Dateien).
* **Ansicht:** Liste / Details, Raster, Kompakt; Standard- oder große Vorschau (unabhängig); Dateinamen ausblenden (nur Raster); ausgeblendete Dateien anzeigen.
* **Sortierung:** Keine, Name, Größe, Datum, Typ, je auf- und absteigend; Standard Datum absteigend, Ordner zuerst.
* **Symbolgröße im Raster:** Der Rahmen der Kacheln ist weg, Innenabstände sind klein (2 dp außen, 6 dp um Symbole, 0 um Fotos). Die Symbolfläche ist quadratisch und so breit wie die Kachel (vorher fest 64 dp hoch in einer 148 dp hohen, gerahmten Kachel). Spalten richten sich nach der Breite (mindestens 84 dp, groß 128 dp).
* **Fotos und Cover:** Vorschaubilder füllen die Kachel, Größe richtet sich nach der Vorschaueinstellung, Zwischenspeicher wie bisher. Ohne Vorschau erscheint das Dateisymbol. Musik zeigt Titel, Interpret und Album, wenn vorhanden.
* **Suche:** eingeklappt, Lupe öffnet und schließt, Kreuz leert, „Nichts gefunden für …“ bei null Treffern.
* **Neuer Ordner:** im Menü; leere Namen, Namen mit „/“, „.“, „..“ und vorhandene Namen werden mit Meldung abgelehnt (bestand schon, `FileOps.newFolder`).
* **Speicheranalyse:** Tipp auf Hauptspeicher oder SD-Karte öffnet sofort den Speicher. Die Analyse ist optional (Menü „Speicheranalyse“) und gilt für den Bereich: Ordner = dieser Ordner, Gruppe (Bilder, Audio, Videos, Dokumente) = gewählter Speicher mit der Gruppe im Mittelpunkt, Übersicht = Speicher zur Wahl.
* **Keine doppelten Zeilen:** Die zweite Zeile (Zurück, Titel, Lupe, Zwei-Fenster-Symbol) ist entfernt. Im einzelnen Fenster gibt es nur die Pfadleiste; Suchen und Zwei Fenster stehen im ☰-Menü, Zurück über Pfadleiste und Zurück-Taste. In der geteilten Ansicht hat jedes Fenster nur seine Pfadzeile (Marke, ⌂, Pfad, Menü); die Titelzeile „Zum PC“ mit Umschalter entfällt, „Ein Fenster“ steht im ☰-Menü.
* **Zwei Fenster:** schnelles Symbol in der Leiste von „Zum PC“ (wenn der Modus angeboten wird) und Menüeintrag „Zwei Fenster“, der den Modus bei Bedarf einschaltet. Kleine Fenster: eigenes ☰-Menü; Ansicht, Vorschau, Namen, versteckte Dateien und Sortierung sind je Fenster gespeichert (Einstellungsschlüssel mit @1 und @2); ohne eigenen Wert gilt der des einzelnen Fensters.
* **Externe Apps:** „Öffnen mit …“ und „Bearbeiten mit …“ über einen eigenen `FilesProvider` (`content://`, nur mit Berechtigung für die eine Datei, nur unter `/storage`, nie `Android/data` oder `Android/obb`). Danach lädt die Liste neu; die App meldet, ob die Datei geändert wurde.

## Speicherung

Alle Wahlen liegen in den App-Einstellungen (`Conn`): `viewmode`, `largepreview`, `hidenames`, `showhidden`, `sortkey`/`sortasc`/`foldersfirst`. Eine frühere Einstellung Kacheln/Liste wird übernommen, wenn noch keine neue gespeichert ist.

## Geprüft (auf dem PC)

* `./build.sh`: Bau erfolgreich.
* `./test.sh`: alle Prüfungen bestanden (Link 55, Dateioperationen 60, Sortierung 25, Ansicht und Öffnen mit 184, ZIP 52, Speicheranalyse 39, Papierkorb 39, verschlüsselte Verbindung 16, Nolphin-Server 7, QR-Leser 181).

## Am Handy angesehen (Xiaomi, dunkles Design, 13.10.2026)

* Schmale Leiste mit Logo ohne Rahmen und Menü ☰ (Suchen, Neuer Ordner, Ansicht und Sortierung, Einstellungen).
* Dialog „Ansicht und Sortierung“ mit allen Einträgen; Liste, Raster und Kompakt umgeschaltet, die Wahl gilt sofort.
* Raster mit 4 Spalten: Albumbilder füllen die Kachel, Titel darunter (zwei Zeilen fest, damit nichts überlappt). Ordner: Symbol mit kleinem Rand. Kompakt: Zeilen mit zwei Textzeilen, Titel, Interpret und Album.
* Suche: eingeklappt, Lupe öffnet, „Nichts gefunden für …“, Kreuz leert und schließt.
* Speicheranalyse: Tipp auf die Karte „SD-Karte“ öffnet sofort den Speicher. Menü → Speicheranalyse im Ordner „ZZ- Top“ zählt nur diesen Ordner (kein Speicher-Gesamtwert mehr); im Bereich „Audio“ fragt sie nach dem Speicher und zeigt „Audio hier: 3,96 GB (75 %)“.
* Zwei Fenster: im ☰-Menü einschaltbar, zwei Fenster mit eigenem ☰ und 4 Spalten; das Symbol in der Leiste führt hinein und zurück.
* Neuer Ordner: „Ordner „NolphinTest“ angelegt“; der Testordner wurde danach über sein Menü in den Papierkorb gelegt und dort endgültig gelöscht (Dialog nannte „NolphinTest“; die vier älteren Dateien im Papierkorb blieben unberührt).
* Einstellungen bleiben nach hartem Beenden der App erhalten (Raster, große Vorschau, Namen aus).
* Große Vorschau und Namen ausblenden im Raster: Galerie mit 2 Spalten, Cover füllen die Kachel.
* Öffnen mit …: Auswahl der Apps erscheint; VLC hat die Datei über die Freigabe abgespielt (kein Zugriffsfehler).

## Nicht geprüft / Grenzen

* **Die Bilder und GIFs der README sind aus erfundenen Beispieldaten gezeichnet** (`docs/bilder/quelle.html`), nicht von echten Bildschirmfotos. Sie zeigen die neue Leiste, das Ansichtsblatt und die geteilte Ansicht, wurden aber nicht mit Aufnahmen vom Handy verglichen; Einzelheiten (Abstände, Schrift) können vom echten Aussehen abweichen.
* **Eigene Ansicht je Fenster** (Zwei-Fenster-Modus) ist programmiert, aber am Handy nicht ausprobiert.
* **Nicht am Handy gesehen:** helles Design, große Systemschrift, andere Bildschirmgrößen, große Vorschau, „Dateinamen ausblenden“, „Ausgeblendete Dateien anzeigen“, Fotos im Raster (angesehen wurden Albumbilder), Neuer Ordner, „Bearbeiten mit …“, Neustart-Test der Einstellungen.
* Das Speichern der Einstellungen (SharedPreferences) und der Datei-Anbieter sind nicht in den PC-Tests (benötigen Android); getestet sind die Regeln dahinter.
* „Öffnen mit …“ und „Bearbeiten mit …“ wurden mit keiner echten App ausprobiert. Ob eine App die Originaldatei ändert oder eine Kopie speichert, entscheidet die App.
* Das Launcher-Symbol kann je nach Hersteller-Oberfläche trotzdem in eine eigene Form oder auf einen Hintergrund gesetzt werden.
* Albumbilder und Musikdaten stammen aus den Dateien; ohne Einträge bleibt der Dateiname.
* „Dateinamen ausblenden“ wirkt nur im Raster (in Liste und Kompakt wären Zeilen ohne Namen nicht benutzbar).
