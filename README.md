# Nolphin Handy

Android-App für den Dateiaustausch mit **Nolphin** am PC, in beide Richtungen, und zugleich ein Dateimanager für das Handy.

* **Vom PC:** Dateien aus dem Ordner „Handy“ am PC kommen aufs Handy.
* **Zum PC:** Fotos, Dateien und Ordner vom Handy gehen an den PC.
* **Dateiverwaltung:** Kopieren, Verschieben, Löschen, Umbenennen, neuer Ordner, Eigenschaften, mit internem Speicher und SD-Karte.

Nur das Android-Framework. **Keine Bibliotheken, kein Gradle.** Mindestens Android 10.

Das Aussehen folgt Linux Mint (Mint-Grün), sachlich und ruhig: flache Listen, einfarbige Symbole, große klare Schrift mit etwas mehr Abstand. Es gibt einen hellen und einen dunklen Modus. Beide folgen der Einstellung des Handys.

> Die Bilder unten sind **nachgezeichnet mit Beispieldaten** (Quelle: [`docs/bilder/quelle.html`](docs/bilder/quelle.html)). Sie zeigen das Aussehen der App, aber keine echten Dateien oder Netzwerkdaten.

| Start | Zum PC: Übersicht | Liste |
|:--:|:--:|:--:|
| ![Start, hell](docs/bilder/start-hell.png) | ![Übersicht mit Google Fotos und Cloud, hell](docs/bilder/uebersicht-hell.png) | ![Liste, hell](docs/bilder/liste-hell.png) |

| Auswahl | Dokumente mit Reitern und Speicher-Schalter | Kopieren und Verschieben |
|:--:|:--:|:--:|
| ![Auswahl, hell](docs/bilder/auswahl-hell.png) | ![Dokumente, hell](docs/bilder/dokumente-hell.png) | ![Einfügen-Leiste, hell](docs/bilder/einfuegen-hell.png) |

| Dunkel: Liste | Dunkel: Auswahl | Dunkel: Dokumente |
|:--:|:--:|:--:|
| ![Liste, dunkel](docs/bilder/liste-dunkel.png) | ![Auswahl, dunkel](docs/bilder/auswahl-dunkel.png) | ![Dokumente, dunkel](docs/bilder/dokumente-dunkel.png) |

| Vom PC | Vom PC: Auswahl, dunkel | Start, dunkel |
|:--:|:--:|:--:|
| ![Vom PC, hell](docs/bilder/vompc-hell.png) | ![Vom PC mit Auswahl, dunkel](docs/bilder/vompcauswahl-dunkel.png) | ![Start, dunkel](docs/bilder/start-dunkel.png) |

## Wo die Dateien am PC landen

Am PC gibt es **einen** Handy-Ordner: `~/Handy`. In Nolphin stehen links in der Seitenleiste zwei Einträge dafür:

| Seitenleiste in Nolphin | Ordner am PC | Wofür |
|---|---|---|
| **Handy** | `~/Handy` | Dateien, die das Handy laden soll. Sie erscheinen in der App unter **Vom PC**. |
| **Vom Handy** | `~/Handy/Vom Handy` | Alles, was Sie vom Handy senden: Dateien, Fotos aus Google Fotos, Dateien aus Google Drive und Ordner. |

* Einzelne Dateien liegen direkt in `Vom Handy`.
* Ordner liegen dort als Ordner mit allen Unterordnern.
* Gibt es einen Namen schon, wird nummeriert (`Name (1).ext`). Nichts wird überschrieben.

## Die vier Reiter

Unten stehen vier Reiter. Die Einstellungen öffnen Sie oben rechts.

| Reiter | Was er tut |
|---|---|
| **Start** | Zeigt „Verbunden“ oder „Nicht verbunden“, den Fortschritt einer Übertragung (mit Abbruch) und die letzten Übertragungen. |
| **Vom PC** | Dateien aus dem Ordner „Handy“ am PC, mit Vorschaubild. Antippen lädt eine Datei. Geladene Dateien lassen sich öffnen, teilen oder speichern. |
| **Zum PC** | Der Dateimanager des Handys. Hier senden Sie Dateien und Ordner an den PC. |
| **Verlauf** | Alle Übertragungen (bis 200) mit Ergebnis. |

Die **Einstellungen** enthalten: Verbindung trennen, neue Dateien automatisch laden (aus: erst nach Antippen), Zielordner für Dateien vom PC, Verlauf löschen.

## „Zum PC“ im Einzelnen

**Übersicht.** Hauptspeicher und SD-Karte mit Füllstand, Schnellzugriff (Downloads, Apps, Google Fotos, Cloud und andere Apps) und die Kategorien Bilder, Audio, Videos und Dokumente.

**Google und andere Apps.** Dateien, die nur in der Cloud oder im Speicher einer App liegen, sind keine Dateien auf dem Handy. Dafür gibt es zwei Einträge im Schnellzugriff. Beide öffnen die Auswahl des Systems. Was Sie dort wählen, geht an den PC.

* **Google Fotos:** die Fotoauswahl des Systems. Sie zeigt auch Bilder und Videos aus der Google-Cloud, die nicht auf dem Handy liegen (ab Android 13; früher die allgemeine Dateiauswahl).
* **Cloud und andere Apps:** die Dateiauswahl des Systems mit Google Drive und den Dateien anderer Apps, die einen Dokumentenanbieter haben. Mehrfachauswahl ist möglich.

Der Eintrag **Apps** zeigt dagegen nur Apps, die ihre Dateien in einem eigenen Medienordner ablegen (zum Beispiel WhatsApp). Die privaten Datenordner anderer Apps (`Android/data`) sind seit Android 11 für jede App gesperrt, auch mit „Zugriff auf alle Dateien“. Was andere Apps in Bilder, Dokumente, Audio oder Download speichern, finden Sie in den Kategorien.

**Ordner.** Oben stehen Pfeil zurück, Name des Ordners, „Neuer Ordner“ und der Wechsel zwischen Liste und Kacheln. Darunter folgen Suche und Pfadleiste. Tippen Sie auf das Haus, um zur Übersicht zu gelangen.

**Dokumente.** Reiter: **Alle, DOC, XLS, PDF, PPT, Sonstige.** „Sonstige“ enthält zum Beispiel ZIP, EPUB und Konstruktionsdateien (STEP, STL, DXF …). Die Liste zeigt Dokumente aus dem internen Speicher **und** von der SD-Karte zusammen (einstellbar mit dem Schalter unten). Dateien auf der SD-Karte tragen in der Zeile den Zusatz „SD-Karte“. Programm- und Systemdateien anderer Apps erscheinen nicht.

**Sortieren.** Der Knopf mit den zwei Pfeilen in der Kopfleiste öffnet die Sortierung: nach **Name, Größe, Änderungsdatum oder Dateityp**, **aufsteigend oder absteigend**, mit oder ohne **Ordner zuerst**. Unter der Pfadleiste steht, wonach gerade sortiert ist („Sortiert nach Größe ↓“). Die Wahl bleibt gespeichert, auch nach dem Schließen der App. Der Knopf **Standard** im Menü setzt sie zurück. Gleiche Werte werden immer nach Namen geordnet. Solange Sie nichts gewählt haben, stehen in den Kategorien (Bilder, Audio, Dokumente …) die neuesten Dateien zuerst und in Ordnern die Namen von A bis Z.

**Favoriten und zuletzt geöffnete Orte.** Im Menü **⋮** eines Ordners steht **Zu Favoriten hinzufügen**. In der Übersicht erscheinen die Favoriten oben mit einem Stern, darunter die **zuletzt geöffneten Ordner** (die letzten fünf). Ein Favorit hat ein eigenes Menü mit „Öffnen“ und „Aus Favoriten entfernen“. Ist ein Ordner nicht mehr da (zum Beispiel SD-Karte entfernt), steht dort „Nicht verfügbar“. Die Liste der zuletzt geöffneten Ordner löschen Sie in den Einstellungen.

**Zwei Fenster.** In den Einstellungen unter „Dateimanager“ schalten Sie **Zwei-Fenster-Modus anbieten** ein (Standard: aus). Dann erscheint in „Zum PC“ oben ein Knopf für zwei Fenster. Im Hochformat stehen die Fenster untereinander, im Querformat nebeneinander. Jedes Fenster hat seinen eigenen Ordner, Speicher (intern oder SD-Karte), Sortierung und Auswahl. Ein Fenster merkt sich seinen Ordner auch nach dem Drehen oder Neustart.

* Das Fenster, das Sie zuletzt angetippt haben, ist **aktiv** (grüne Linie oben). Das Fenster mit der Auswahl ist die **Quelle**, das andere das **Ziel**.
* Unten steht „Von … nach …“ mit **Kopieren** und **Verschieben**. Vor dem Vorgang fragt die App noch einmal mit dem vollen Zielpfad und bietet **Ziel wechseln** an. Danach läuft alles wie sonst: Fortschritt, Abbrechen, Konfliktfrage, Ergebnisliste.
* Ist im Ziel-Fenster noch kein Ordner offen (Übersicht), bittet die App erst darum, einen zu öffnen. Die Übersicht in den kleinen Fenstern ist kompakt (Speicher, Downloads, Favoriten, Kategorien als einfache Zeilen), damit man sofort in einen Ordner kommt.
* Der Knopf in der Kopfleiste führt zurück zu einem Fenster. Schalten Sie den Modus in den Einstellungen aus, gibt es nur noch ein Fenster, wie bisher.

**Vorschau.** Fotos und Videos zeigen ein Vorschaubild, Audio das Albumbild (wenn die Datei eines hat), PDF die erste Seite. Alle anderen Dateien zeigen ein einfarbiges Symbol nach Dateiart.

**Suche.** Das Suchfeld sucht im aktuellen Ordner mit allen Unterordnern, in der Übersicht auf allen Speichern. In einer Kategorie filtert es die Liste.

**Speicher wählen.** Steckt eine SD-Karte, erscheint in den Kategorien und bei der Suche in der Übersicht ein Schalter **Beides / Intern / SD-Karte**. Standard ist „Beides“, es muss also nichts gewählt werden. In einem Ordner gibt es den Schalter nicht, weil der Ordner den Ort schon festlegt.

### Funktionen für Dateien und Ordner

Es gibt zwei Wege. Beide bieten dasselbe.

1. **Ein Element:** Tippen Sie rechts in der Zeile auf **⋮**. Es erscheint ein Menü: An den PC senden, Kopieren, Verschieben, Umbenennen, Eigenschaften, Löschen. Bei einer ZIP-Datei kommen **Entpacken** und **Inhalt ansehen** dazu.
2. **Mehrere Elemente:** Drücken Sie lange auf ein Element. Es erscheinen Kästchen, und die Leiste oben wird grün. Dort stehen die Funktionen **An PC, Kopieren, Verschieben, Löschen** und **⋮** (Alle auswählen, Umbenennen, Eigenschaften, Als ZIP packen). Tippen Sie weitere Elemente an, um sie zu wählen.

**Kopieren und Verschieben:** Wählen Sie die Elemente, tippen Sie auf Kopieren oder Verschieben, öffnen Sie den Zielordner und tippen Sie unten auf **Hierher**. Gibt es den Namen schon, hängt die App „(2)“ an. Einen Ordner in sich selbst zu kopieren oder zu verschieben lehnt sie ab.

**ZIP entpacken und packen:**

* **Entpacken:** Die App zeigt vorher, was im Archiv steckt (Dateien, Ordner, entpackte Größe), und prüft den freien Platz. Entpackt wird in einen Ordner mit dem Namen des Archivs. Bringt das Archiv schon einen eigenen obersten Ordner mit, wird gleich dort entpackt, es gibt keinen doppelten Ordner. Gibt es den Zielordner schon, fragt die App wie beim Kopieren (beide behalten, überspringen, ersetzen).
* **Schutz beim Entpacken:** Einträge mit `..`, absolutem Pfad, Laufwerksbuchstaben oder Verknüpfungspfad werden nicht entpackt, die App nennt sie vorher. Entpackt wird in eine Temporärdatei, erst danach unter dem richtigen Namen abgelegt. Ein Archiv, das mehr Daten enthält als angegeben, wird gestoppt (Schutz gegen Zip-Bomben und volle Speicher).
* **Inhalt ansehen:** Zeigt die Liste der Einträge, ohne etwas zu entpacken.
* **Als ZIP packen:** Auswahl, dann ⋮, dann „Als ZIP packen“. Das Archiv entsteht im selben Ordner (`Name.zip` oder `Archiv.zip`). Nicht lesbare Dateien kommen nicht ins Archiv, die App nennt sie.

**Mehrere Dateien umbenennen:** Wählen Sie mehrere Dateien, dann ⋮, dann **Umbenennen …**. Vier Reiter: **Ersetzen** (Text suchen und ersetzen), **Nummer** (zum Beispiel `001.jpg`, `002.jpg`, mit Anfang, erster Nummer und Stellenzahl), **Anhängen** (Text vorn und hinten, vor der Endung) und **Groß/Klein**. Eine Vorschau „alt → neu“ zeigt vorher alle Namen. Der Knopf **Umbenennen** bleibt gesperrt, solange es ein Problem gibt (leerer Name, zwei gleiche Namen, ein Name, den es schon gibt, auch bei anderer Groß-/Kleinschreibung wie auf der SD-Karte). Umbenannt wird in zwei Schritten über Zwischennamen, so funktionieren auch Tauschfälle. Geht etwas schief, wird alles zurückgenommen.

**Löschen:** Die App fragt vorher nach. Gelöscht wird **endgültig**. Es gibt keinen Papierkorb.

**Sicherheit beim Kopieren, Verschieben und Löschen:**

* **Keine halben Dateien:** Kopiert wird erst in eine Temporärdatei im Zielordner und erst danach unter dem richtigen Namen abgelegt. Nach einem Fehler oder Abbruch bleibt nichts Halbes zurück.
* **Verschieben ohne Risiko:** Auf demselben Speicher wird nur umbenannt. Zwischen Speichern (zum Beispiel SD-Karte) wird kopiert, die Kopie per Prüfsumme geprüft, und erst dann verschwindet das Original, Datei für Datei. Ein Fehler lässt das Original stehen.
* **Verknüpfungen (Symlinks) werden nie verfolgt:** Beim Löschen verschwindet nur die Verknüpfung, nie ihr Ziel. Beim Kopieren gibt es dadurch keine Endlosschleifen.
* **Ein Fehler stoppt nicht alles:** Nicht lesbare Dateien oder Ordner werden ausgelassen. Danach zeigt die App, was geklappt hat und was nicht, mit dem Grund. Ein Ordner mit Fehlern gilt nicht als erledigt.
* **Fortschritt und Abbrechen:** Dauert ein Vorgang länger als einen Augenblick, zeigt die App ein Fenster mit dem Namen der aktuellen Datei, einem Balken, der Größe und „Datei 3 von 8“. Mit **Abbrechen** stoppt der Vorgang. Dabei bleibt nichts Halbes zurück, und schon erledigte Dateien bleiben erledigt.
* **Gleiche Namen im Ziel:** Gibt es beim Kopieren oder Verschieben schon gleiche Namen, fragt die App **einmal pro Vorgang**: **Beide behalten** (die neue Datei bekommt `(2)`), **Überspringen** oder **Ersetzen**. Ordner, die es im Ziel schon gibt, werden zusammengeführt, und nur gleiche Dateien darin sind ein Konflikt. **Ersetzen** verlangt eine zweite Bestätigung mit der Liste der betroffenen Dateien.
* **Nochmal versuchen:** Nach Fehlern zeigt die App, was geklappt hat und was nicht, mit Grund. „Nochmal versuchen“ wiederholt nur das Fehlgeschlagene. Schon Kopiertes im Ziel bleibt dabei unverändert.

**Zugriff:** Für den Dateimanager braucht die App „Zugriff auf alle Dateien“ (`MANAGE_EXTERNAL_STORAGE`). Das erlauben Sie einmal in den Systemeinstellungen. Ohne ihn bleibt nur die Systemauswahl.

## Einrichten

1. Klicken Sie in Nolphin am PC in der Seitenleiste auf **Handy**. Es erscheint ein QR-Code.
2. **Erster Scan:** Scannen Sie den Code mit der Kamera des Handys. Die Seite im Browser bietet die App zum Installieren an. Laden Sie sie und erlauben Sie die Installation. Warnungen von Chrome, Play Protect oder Mi Protect sind bei Apps außerhalb des Play Store normal.
3. **Verbinden:** Öffnen Sie die App, tippen Sie auf **QR-Code scannen und verbinden** und richten Sie die Kamera auf den Code am PC. Zum Scharfstellen tippen Sie aufs Bild. Die App liest den Code mit einem **eigenen QR-Leser** ([`QrDecoder.java`](src/org/nolphin/handy/QrDecoder.java)), ohne Bibliothek und ohne Google-Dienste. Statt zu scannen können Sie am PC den Link kopieren und in der App **Link aus der Zwischenablage einfügen** wählen.
4. **Wichtig:** Handy und PC müssen im **selben WLAN** sein. In einem anderen Netz, zum Beispiel einem Hotspot, erreicht die App den PC nicht.

Der Schlüssel im Link gilt, solange der Austausch am PC läuft, höchstens eine begrenzte Zeit. Nach einem Neustart von Nolphin oder bei einem neuen Code scannen Sie einfach erneut. Beim ersten Verbinden lädt die App vorhandene Dateien nicht. Neue Dateien landen in `Download/Nolphin`.

## Aktualisieren

Hat Nolphin am PC eine neuere App (höhere `versionCode`), zeigt die Startseite **Neue Version**. Ein Tipp auf **Aktualisieren** installiert sie über die alte, ohne Deinstallieren. Die Verbindung bleibt. Beim ersten Mal erlauben Sie der App einmal „Apps installieren“.

Eine neue Version bereitstellen:

1. `versionCode` in [`AndroidManifest.xml`](AndroidManifest.xml) erhöhen.
2. `./build.sh` ausführen.
3. Die APK nach `~/.local/share/nolphin/phone/nolphin-handy.apk` kopieren, oder Nolphin installieren (`sudo ninja -C build install` im Nolphin-Ordner).

## Bauen (Entwickler)

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

* den **QR-Leser**: 163 Prüfungen (Codes des C-Encoders von Nolphin, gedreht, verzerrt, unscharf, verrauscht, ein Bildschirmfoto und ein echtes dunkles, unscharfes Handyfoto),
* die **Link-Prüfung**: 34 Prüfungen,
* die **Sortierung und die Listen für Favoriten und zuletzt geöffnete Orte** (`SortOrder`, `PlaceLists`): 22 Prüfungen (alle vier Schlüssel, beide Richtungen, „Ordner zuerst“, Gleichstand nach Namen, Endungen, Begrenzung und Reihenfolge der Listen, Pfade mit Leerzeichen und Umlauten),
* **ZIP und Stapelumbenennen** (`ZipOps`, `BatchRename`): 52 Prüfungen (Inhalt lesen, Entpacken mit Umlauten und leeren Ordnern, gemeinsamer Oberordner, unsichere Pfade, Verknüpfungen, Größe über der Angabe, Konflikte, Packen, Vorschau, Tauschfälle, Groß-/Kleinschreibung, Zurücknehmen bei Fehler),
* die **Dateioperationen** (`FileOps`): 60 Prüfungen für Kopieren, Verschieben (auch über Kopieren und Prüfen), Umbenennen, neuer Ordner, ZIP und Löschen. Dazu die Schutzfälle: Ordner nicht in sich selbst, Namenskonflikte (überspringen, umbenennen, ersetzen, Ordner zusammenführen), Verknüpfungen (nie verfolgt, keine Endlosschleife), nicht lesbare Dateien und Ordner (Rest wird erledigt, Fehler genannt), keine halben Dateien nach Fehler oder Abbruch, Original bleibt bei Fehler oder Abbruch, Fortschritt mit Dateizähler, Abbruch auch beim Löschen, Konfliktliste vorab, Wiederholen nur des Fehlgeschlagenen.

Der Test braucht den Nolphin-Quelltext im Nachbarordner `../nolphin-file-manager-master` (oder `NOLPHIN_ROOT`), außerdem `gcc` und GLib.

**Von Hand auf einem echten Handy geprüft** (mit Testdateien in einem eigenen Testordner, danach gelöscht): Neuer Ordner, Kopieren (auch mit Namenskonflikt, es entsteht `a (2).txt`), Verschieben, Umbenennen, Löschen mit Abbrechen und Bestätigen, Löschen eines ganzen Ordners, Mehrfachauswahl mit der Leiste oben, Eigenschaften, Ordner als ZIP an den PC (kam mit Unterordnern an, so läuft es noch bei älterem Nolphin) und der Schutz „Ordner nicht in sich selbst kopieren“. Der Ordner-Versand Datei für Datei ist am PC-Server automatisch getestet (Unterordner, Schutz vor `..`, Nummerierung) und auf dem Handy von Hand geprüft: Ein Testordner mit zwei Ebenen, einer Datei mit Umlauten und einem leeren Unterordner kam mit allen Dateien und Unterordnern an. Beim zweiten Senden entstanden `Name (1).txt`, nichts wurde überschrieben. Die Oberfläche selbst ist nicht automatisch getestet.

### Bilder der README neu erzeugen

```bash
google-chrome --headless=new --hide-scrollbars --virtual-time-budget=6000 \
  --force-device-scale-factor=2 --window-size=360,740 \
  --screenshot=docs/bilder/liste-hell.png "file://$PWD/docs/bilder/quelle.html#liste-hell"
```

Seiten: `start`, `uebersicht`, `liste`, `auswahl`, `dokumente`, `einfuegen`, `vompc`, `vompcauswahl`, jeweils mit `-hell` oder `-dunkel`. Die Seite `uebersicht` ist höher: `--window-size=360,900`. Die Quelle braucht beim Rendern Internet für die Symbolschrift.

## Aufbau des Quelltexts

| Datei (`src/org/nolphin/handy/`) | Aufgabe |
|---|---|
| `MainActivity` | Kopfleiste, Reiter, Verbindung, Versand |
| `HomeScreen`, `FromPcScreen`, `ToPcScreen`, `HistoryScreen`, `SettingsScreen` | Die Bildschirme |
| `SortOrder`, `PlaceLists` | Sortierreihenfolge, Favoriten und zuletzt geöffnete Orte (ohne Android, getestet) |
| `ToPcTabs`, `DualScreen` | Reiter „Zum PC“ mit einem oder zwei Fenstern |
| `ZipOps`, `BatchRename` | ZIP entpacken und packen, Stapelumbenennen mit Vorschau (ohne Android, getestet) |
| `FileOps` | Kopieren, Verschieben, Löschen, Umbenennen, ZIP (sicher, mit Ergebnisliste, Abbruch und Konfliktregeln) |
| `Ui` | Farben (hell und dunkel), Symbole, Listenzeilen |
| `Thumbs` | Vorschaubilder (Foto, Video, Albumbild, PDF, App-Symbol) |
| `SyncService` | Laden und Senden im Hintergrund |
| `Link`, `Connector`, `PcCheck`, `Conn` | Verbindung und gespeicherter Schlüssel |
| `QrDecoder`, `ScanActivity` | Eigener QR-Leser und Scanner |
| `Updater`, `Store`, `ShareActivity` | Aktualisieren, Verlauf, Teilen-Menü |

Der PC-Teil (der kleine Server in Nolphin) liegt im Projekt [nolphin-file-manager](https://github.com/kaysiebke-cell/nolphin-file-manager) (`src/nolphin-phone-server.c`, `src/nolphin-phone.c`).

## Sicherheit und Grenzen

* Die Verbindung läuft **unverschlüsselt im eigenen WLAN**. Der Schlüssel steckt im Link. Verwenden Sie das nur in einem Netz, dem Sie vertrauen.
* Löschen ist endgültig (kein Papierkorb).
* Bei einem älteren Nolphin am PC geht ein Ordner nur als ZIP.
* Der Zugriff auf alle Dateien ist mächtig. Die App nutzt ihn nur für den Dateimanager, und nur nach Ihrer Aktion.
* Die privaten Ordner anderer Apps (`Android/data`, `Android/obb`) kann Android keiner App zeigen. Cloud-Dateien und Dateien anderer Apps holen Sie über die Systemauswahl (siehe oben).
