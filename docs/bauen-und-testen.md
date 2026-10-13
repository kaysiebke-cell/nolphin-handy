# Bauen und Testen (Entwickler)

Die App bauen, die Tests ausführen und die Bilder der README neu erzeugen.

[← Zurück zur Übersicht](../README.md)

## Bauen

Voraussetzungen: JDK (`javac`, `keytool`) und das Android-SDK mit `android.jar`, `aapt2`, `d8`, `zipalign`, `apksigner`. Das SDK wird standardmäßig in `~/Android/Sdk` gesucht, sonst über `ANDROID_HOME`.

```bash
./build.sh
```

Ergebnis: `build/nolphin-handy.apk`.

Beim ersten Lauf entsteht ein Signaturschlüssel in `~/.config/nolphin/`. Er liegt **nicht** im Repository. Sein Passwort liegt im Systemschlüsselbund (`secret-tool`, Paket `libsecret-tools`), nie in einer Datei. Eine neue APK lässt sich nur über die alte installieren, wenn sie mit demselben Schlüssel signiert ist.

### Tests

```bash
./test.sh
```

Der Test läuft ohne Handy und prüft:

* den **QR-Leser**: 181 Prüfungen (Codes des C-Encoders von Nolphin, gedreht, verzerrt, unscharf, verrauscht, **stark schräg gehalten mit Mauszeiger auf dem Code, Schatten und Rauschen**, ein Bildschirmfoto und ein künstlich verwischtes, dunkles Testbild mit erfundenem Link),
* die **Link-Prüfung**: 55 Prüfungen (Version 2 mit Fingerabdruck, alte Links, öffentliche Adressen, Hostnamen, Ports, Schlüsselform, Ablauf, fremde Schemata),
* die **verschlüsselte Verbindung** (`Secure`): 16 Prüfungen (richtiger Fingerabdruck wird angenommen; falscher, fremdes Zertifikat, Dritter mit eigenem Zertifikat, `http://`, Klartext-Server hinter einer `https`-Adresse und Umleitungen werden abgelehnt),
* die **App-Seite gegen den echten Nolphin-Server** (C, GnuTLS): 7 Prüfungen (beide Seiten berechnen denselben Fingerabdruck, die App verbindet, falscher Fingerabdruck und falscher Schlüssel werden abgelehnt, keine Browser-Seite, Klartext ohne Antwort),
* die **Ansicht** (`ViewModes`, `FileUris`, `UiScale`): 475 Prüfungen (gespeicherte Darstellung und Übernahme der alten Einstellung, Spalten bei vielen Bildschirmbreiten, Vorschaugröße unabhängig von der Darstellung, Mindestgrößen der Touch-Ziele, neun Einträge im Sortiermenü, Dateien für andere Apps nur im Speicher und nie aus fremden App-Ordnern, Meldung nach der Rückkehr),
* die **Sortierung und die Listen für Favoriten und zuletzt geöffnete Orte** (`SortOrder`, `PlaceLists`): 25 Prüfungen (dazu „Keine Sortierung“ und der neue Standard) (alle vier Schlüssel, beide Richtungen, „Ordner zuerst“, Gleichstand nach Namen, Endungen, Begrenzung und Reihenfolge der Listen, Pfade mit Leerzeichen und Umlauten),
* der **Papierkorb** (`TrashOps`): 39 Prüfungen (nur Umbenennen, jeder Speicher sein eigener Papierkorb, Zurückholen an den alten Ort, Namensgleichheit „(2)“ ohne Überschreiben, fehlender alter Ordner, Verknüpfungen, Original bleibt bei Fehler und Abbruch, 30 Tage, Absturz ohne Infodatei, kaputte Infodatei, nie aus dem Papierkorb erneut hinein),
* die **Speicheranalyse** (`StorageScan`): 39 Prüfungen (Gruppen, Summen, größte Ordner und Dateien, Verknüpfungen ohne Endlosschleife, leerer und fehlender Ordner, nicht lesbarer Ordner, Abbruch auch mitten in einem großen Ordner, Fortschritt),
* **ZIP und Stapelumbenennen** (`ZipOps`, `BatchRename`): 52 Prüfungen (Inhalt lesen, Entpacken mit Umlauten und leeren Ordnern, gemeinsamer Oberordner, unsichere Pfade, Verknüpfungen, Größe über der Angabe, Konflikte, Packen, Vorschau, Tauschfälle, Groß-/Kleinschreibung, Zurücknehmen bei Fehler),
* die **Dateioperationen** (`FileOps`): 60 Prüfungen für Kopieren, Verschieben (auch über Kopieren und Prüfen), Umbenennen, neuer Ordner, ZIP und Löschen. Dazu die Schutzfälle: Ordner nicht in sich selbst, Namenskonflikte (überspringen, umbenennen, ersetzen, Ordner zusammenführen), Verknüpfungen (nie verfolgt, keine Endlosschleife), nicht lesbare Dateien und Ordner (Rest wird erledigt, Fehler genannt), keine halben Dateien nach Fehler oder Abbruch, Original bleibt bei Fehler oder Abbruch, Fortschritt mit Dateizähler, Abbruch auch beim Löschen, Konfliktliste vorab, Wiederholen nur des Fehlgeschlagenen.

Der Test braucht den Nolphin-Quelltext im Nachbarordner `../nolphin-file-manager-master` (oder `NOLPHIN_ROOT`), außerdem `gcc` und GLib.

**Von Hand auf einem echten Handy geprüft** (mit Testdateien in einem eigenen Testordner, danach gelöscht): Neuer Ordner, Kopieren (auch mit Namenskonflikt, es entsteht `a (2).txt`), Verschieben, Umbenennen, Löschen mit Abbrechen und Bestätigen, Löschen eines ganzen Ordners, Mehrfachauswahl mit der Leiste oben, Eigenschaften, Ordner als ZIP an den PC (kam mit Unterordnern an, so läuft es noch bei älterem Nolphin) und der Schutz „Ordner nicht in sich selbst kopieren“. Der Ordner-Versand Datei für Datei ist am PC-Server automatisch getestet (Unterordner, Schutz vor `..`, Nummerierung) und auf dem Handy von Hand geprüft: Ein Testordner mit zwei Ebenen, einer Datei mit Umlauten und einem leeren Unterordner kam mit allen Dateien und Unterordnern an. Beim zweiten Senden entstanden `Name (1).txt`, nichts wurde überschrieben. Die Oberfläche selbst ist nicht automatisch getestet.

### Bilder der README neu erzeugen

```bash
docs/bilder/erzeugen.sh
```

Das Skript zeichnet das Titelbild (`bilder/titelbild-quelle.html`, mit dem Nolphin-Fenster aus dem Nolphin-Projekt und dem App-Symbol) und die Seiten aus `bilder/quelle.html` mit Chrome oder Chromium (Beispieldaten, keine echten Dateien) und setzt sie mit `bilder/gif.py` (Python mit Pillow) zu den beiden GIFs `funktionen-hell.gif` und `funktionen-dunkel.gif` zusammen. Die Reihenfolge und die Beschriftungen stehen am Anfang von `gif.py`, die Seiten in `quelle.html` (`#<seite>-<hell|dunkel>`). Die Quelle braucht beim Rendern Internet für die Symbolschrift.
