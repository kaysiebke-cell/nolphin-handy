![Nolphin Handy: im Hintergrund der Dateimanager Nolphin am PC, davor die App auf dem Handy, hell und dunkel. Darunter der Titel und die Zeile: Dateien vom Handy zum PC – und ein Dateimanager für unterwegs.](docs/bilder/titelbild.png)

# Nolphin Handy

Android-App für den Dateiaustausch mit **Nolphin** am PC, in beide Richtungen, und zugleich ein Dateimanager für das Handy.

* **Vom PC:** Dateien aus dem Ordner „Handy“ am PC kommen aufs Handy.
* **Zum PC:** Fotos, Dateien und Ordner vom Handy gehen an den PC.
* **Dateiverwaltung:** Kopieren, Verschieben, Löschen, Umbenennen, neuer Ordner, Eigenschaften, mit internem Speicher und SD-Karte.

Die Verbindung zum PC ist **verschlüsselt** (TLS mit einem Zertifikat, dessen Fingerabdruck im QR-Code steht). Nur das Android-Framework. **Keine Bibliotheken, kein Gradle.** Mindestens Android 10.

Das Aussehen folgt Linux Mint (Mint-Grün), sachlich und ruhig: flache Listen, einfarbige Symbole, große klare Schrift mit etwas mehr Abstand. Es gibt einen hellen und einen dunklen Modus. Beide folgen der Einstellung des Handys.

> Die Bilder unten sind **nachgezeichnet mit Beispieldaten** (Quelle: [`docs/bilder/quelle.html`](docs/bilder/quelle.html)). Sie zeigen das Aussehen der App, aber keine echten Dateien oder Netzwerkdaten.

Der ganze Funktionsumfang in einem Durchlauf (15 Bilder, jeweils mit Beschriftung):

| Hell | Dunkel |
|:--:|:--:|
| ![Alle Funktionen der App, hell](docs/bilder/funktionen-hell.gif) | ![Alle Funktionen der App, dunkel](docs/bilder/funktionen-dunkel.gif) |

Gezeigt werden: Start, Übersicht, Ordner und Dateien, Sortieren, Dokumente mit Reitern und Speicher-Schalter, Mehrfachauswahl, Kopieren und Verschieben, Zwei-Fenster-Modus, ZIP entpacken, mehrere Dateien umbenennen, Löschen in den Papierkorb, Papierkorb, Speicheranalyse, Vom PC und Vom PC mit Auswahl.

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

**Menü, Ansicht und Sortierung.** Die Kopfleiste ist schmal: links das App-Symbol, rechts das **Menü (☰)** mit *Suchen*, *Neuer Ordner*, *Ansicht und Sortierung*, *Zwei Fenster* (wenn eingeschaltet) und *Einstellungen*. In „Zum PC“ gibt es zusätzlich die **Lupe**: Sie klappt das Suchfeld auf und wieder zu (Tippen auf das Kreuz leert erst, dann schließt es). Das Suchfeld ist beim Start eingeklappt.

*Ansicht und Sortierung* ist ein Dialog, jede Änderung gilt sofort und bleibt nach einem Neustart erhalten:

* **Ansicht:** Liste / Details, Raster oder Kompakt.
* **Vorschau:** Standard oder groß. Das ist unabhängig von der Ansicht (Raster: größere Kacheln, Liste und Kompakt: größere Symbole und Zeilen).
* **Dateinamen ausblenden** (wirkt nur im Raster) und **Ausgeblendete Dateien anzeigen** (Dateien mit Punkt am Anfang; der Papierkorb der App bleibt immer verborgen).
* **Sortieren nach:** Keine Sortierung, Name, Größe, Datum oder Typ, jeweils ▲ aufsteigend oder ▼ absteigend, dazu *Ordner zuerst*. **Standard: Datum absteigend**, die neuesten Dateien stehen oben. Gleiche Werte werden nach Namen geordnet. *Standard* im Dialog setzt die Sortierung zurück.

Das **Raster** passt Spalten und Kachelbreite an die Bildschirmbreite an. Die Kacheln haben keinen Rahmen: das Symbol (Ordner, Dateityp) oder das Vorschaubild füllt die ganze Kachelbreite, der Name steht darunter. Fotos füllen die Fläche, Musik zeigt das eingebettete Albumbild. Bei Musik stehen Titel, Interpret und Album in der Liste, wenn die Datei sie enthält.

**Mit anderen Apps öffnen.** Im Menü einer Datei (⋮ oder Datei gedrückt halten, „Mehr“) stehen **Öffnen mit …** und **Bearbeiten mit …**. Die App gibt die Datei über einen eigenen Datei-Anbieter (`content://`) nur an die gewählte App heraus, nur für diese eine Datei, nur unterhalb des Speichers und nie aus privaten App-Ordnern. Nach der Rückkehr lädt die Liste neu und die App meldet, ob sich die Datei geändert hat. Manche Apps speichern eine Kopie an anderer Stelle statt der Originaldatei; dann meldet die App „Original unverändert“.

**Favoriten und zuletzt geöffnete Orte.** Im Menü **⋮** eines Ordners steht **Zu Favoriten hinzufügen**. In der Übersicht erscheinen die Favoriten oben mit einem Stern, darunter die **zuletzt geöffneten Ordner** (die letzten fünf). Ein Favorit hat ein eigenes Menü mit „Öffnen“ und „Aus Favoriten entfernen“. Ist ein Ordner nicht mehr da (zum Beispiel SD-Karte entfernt), steht dort „Nicht verfügbar“. Die Liste der zuletzt geöffneten Ordner löschen Sie in den Einstellungen.

**Zwei Fenster.** In „Zum PC“ gibt es dafür ein **Symbol in der Leiste** (neben der Lupe) und den Menüeintrag **Zwei Fenster** im ☰-Menü. Ist der Modus noch nicht eingeschaltet, schaltet der Menüeintrag ihn ein (in den Einstellungen unter „Dateimanager“ steht der Schalter **Zwei-Fenster-Modus anbieten**; ohne ihn zeigt die Leiste das Symbol nicht). Im Hochformat stehen die Fenster untereinander, im Querformat nebeneinander. Es gibt keine zusätzliche Kopfzeile: Jedes Fenster hat nur seine **Pfadzeile** mit der Marke „Fenster 1/2“ links und dem eigenen ☰-Menü (Neuer Ordner, Ansicht und Sortierung, Speicheranalyse) rechts; „Ein Fenster“ steht im ☰-Menü der oberen Leiste und merkt sich seinen Ordner auch nach dem Drehen oder Neustart. **Jedes Fenster hat seine eigene Ansicht, Vorschau, Namensanzeige, Anzeige versteckter Dateien und Sortierung**; sie bleiben gespeichert. Ein Fenster, das noch nichts eigenes hat, übernimmt die Werte des einzelnen Fensters als Ausgangswert. 

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

**Speicheranalyse (nur auf Wunsch):** Ein Tipp auf **Hauptspeicher** oder **SD-Karte** öffnet den Speicher sofort, ohne Analyse. Die Analyse starten Sie im ☰-Menü mit **Speicheranalyse**, und zwar für den Bereich, in dem Sie gerade sind: in einem **Ordner** zählt sie diesen Ordner, in **Bilder, Audio, Videos oder Dokumente** den gewählten Speicher mit dieser Gruppe im Mittelpunkt („Audio hier: …“), auf der Übersicht fragt sie, für welchen Speicher. Die App zählt im Hintergrund (mit Zähler und **Abbrechen**) und zeigt dann:

* oben die Belegung und einen **Balken nach Gruppen** (Bilder, Videos, Audio, Dokumente, Apps, Sonstiges) mit Größen,
* die **größten Ordner** und die **größten Dateien** mit Anteilsbalken. Ein Tipp öffnet den Ordner,
* den Knopf **Dateien ansehen**, der den Speicher wie gewohnt öffnet. Mit dem Symbol oben rechts zählen Sie neu.

Das Ergebnis bleibt bis zum Neuzählen oder Beenden der App erhalten. Die Analyse **liest nur** und ändert nichts. Android erlaubt der App nicht, die Daten des Systems und anderer Apps zu sehen. Die App nennt deshalb, wie viel „nicht lesbar“ ist.

**ZIP entpacken und packen:**

* **Entpacken:** Die App zeigt vorher, was im Archiv steckt (Dateien, Ordner, entpackte Größe), und prüft den freien Platz. Entpackt wird in einen Ordner mit dem Namen des Archivs. Bringt das Archiv schon einen eigenen obersten Ordner mit, wird gleich dort entpackt, es gibt keinen doppelten Ordner. Gibt es den Zielordner schon, fragt die App wie beim Kopieren (beide behalten, überspringen, ersetzen).
* **Schutz beim Entpacken:** Einträge mit `..`, absolutem Pfad, Laufwerksbuchstaben oder Verknüpfungspfad werden nicht entpackt, die App nennt sie vorher. Entpackt wird in eine Temporärdatei, erst danach unter dem richtigen Namen abgelegt. Ein Archiv, das mehr Daten enthält als angegeben, wird gestoppt (Schutz gegen Zip-Bomben und volle Speicher).
* **Inhalt ansehen:** Zeigt die Liste der Einträge, ohne etwas zu entpacken.
* **Als ZIP packen:** Auswahl, dann ⋮, dann „Als ZIP packen“. Das Archiv entsteht im selben Ordner (`Name.zip` oder `Archiv.zip`). Nicht lesbare Dateien kommen nicht ins Archiv, die App nennt sie.

**Mehrere Dateien umbenennen:** Wählen Sie mehrere Dateien, dann ⋮, dann **Umbenennen …**. Vier Reiter: **Ersetzen** (Text suchen und ersetzen), **Nummer** (zum Beispiel `001.jpg`, `002.jpg`, mit Anfang, erster Nummer und Stellenzahl), **Anhängen** (Text vorn und hinten, vor der Endung) und **Groß/Klein**. Eine Vorschau „alt → neu“ zeigt vorher alle Namen. Der Knopf **Umbenennen** bleibt gesperrt, solange es ein Problem gibt (leerer Name, zwei gleiche Namen, ein Name, den es schon gibt, auch bei anderer Groß-/Kleinschreibung wie auf der SD-Karte). Umbenannt wird in zwei Schritten über Zwischennamen, so funktionieren auch Tauschfälle. Geht etwas schief, wird alles zurückgenommen.

**Löschen und Papierkorb:** Die App fragt vorher nach („In den Papierkorb?“). Gelöschtes wandert in den **Papierkorb**, nicht ins Nichts:

* **Wo er liegt:** Jeder Speicher hat seinen eigenen Papierkorb im versteckten Ordner `.nolphin-papierkorb` auf dem Speicher selbst. Es wird nichts zwischen Speichern kopiert. Der Papierkorb belegt weiter Platz, bis er geleert wird.
* **Nur umbenennen:** Das Element wird nur umbenannt/verschoben, nie kopiert und nie ersatzweise gelöscht. Geht das nicht, bleibt das Original unberührt, und die App nennt den Grund.
* **Aufrufen:** In „Zum PC“ gibt es die Zeile **Papierkorb** mit Anzahl und Größe. Im Papierkorb wählen Sie Elemente und tippen **Zurück** (wiederherstellen) oder **Löschen** (endgültig, mit Nachfrage). Das Menü ⋮ hat „Alle auswählen“ und „Papierkorb leeren“.
* **Wiederherstellen:** Das Element kommt an seinen alten Ort zurück. Fehlt der Ordner, wird er neu angelegt. Gibt es dort schon etwas Gleichnamiges, heißt das Zurückgeholte „(2)“, es wird nie etwas überschrieben.
* **30 Tage:** Was älter ist, wird beim Öffnen der Übersicht automatisch endgültig gelöscht.
* **Grenzen:** Der Papierkorb gilt **nur für Löschen in dieser App**, nicht für Löschen durch andere Apps oder am PC. Löschen von Dateien, die schon im Papierkorb liegen, und das Löschen von etwas außerhalb der bekannten Speicher ist endgültig (die App sagt das im Dialog).

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

1. **Am PC:** Klicken Sie in Nolphin in der Seitenleiste auf **Handy**. Es erscheint ein QR-Code. (Nolphin braucht dafür `openssl` und `glib-networking`; fehlt eines, startet der Austausch nicht und sagt es.)
2. **App installieren (einmalig, von Hand):** Der Austausch ist verschlüsselt und hat deshalb keine Webseite mehr, über die sich die App laden ließe. Kopieren Sie die Datei `nolphin-handy.apk` aufs Handy (zum Beispiel per USB-Kabel; nach der Installation von Nolphin liegt sie unter `/usr/local/share/nolphin/phone/`, sonst im Ordner `build/` dieses Projekts) und öffnen Sie sie dort. Erlauben Sie die Installation. Warnungen von Chrome, Play Protect oder Mi Protect sind bei Apps außerhalb des Play Store normal.
3. **Verbinden:** Öffnen Sie die App und tippen Sie auf **QR-Code scannen und verbinden** (oder drücken Sie lange auf das App-Symbol und wählen Sie **QR-Code scannen**). Halten Sie die Kamera der App **ruhig und gerade** auf den Code am PC, etwa 15 bis 30 cm entfernt; zum Scharfstellen tippen Sie aufs Bild. Die App liest den Code mit einem **eigenen QR-Leser** ([`QrDecoder.java`](src/org/nolphin/handy/QrDecoder.java)), ohne Bibliothek und ohne Google-Dienste, auch bei schräg gehaltenem Handy, Spiegelungen und teilweise verdecktem Code. Sie verbindet sich sofort. Ein Scanner des Herstellers (zum Beispiel der von Xiaomi) gibt das Ergebnis nicht an die App zurück und wird nicht benutzt.
4. **Wenn das Scannen nicht klappt:** Kopieren Sie am PC den Link (Knopf neben dem Link im Panel) und tippen Sie in der App auf **Link aus der Zwischenablage einfügen**. Ein frisch kopierter Link wird beim Öffnen der App auch von selbst angeboten.
5. **Wichtig:** Handy und PC müssen im **selben WLAN** sein. In einem anderen Netz, zum Beispiel einem Hotspot, erreicht die App den PC nicht.

**Woran die App den richtigen PC erkennt:** Der QR-Code enthält den Schlüssel und den **Fingerabdruck** des Zertifikats, das Nolphin für diesen Austausch erzeugt. Die App verbindet sich nur mit genau diesem Zertifikat. Passt es nicht (neuer Code am PC, oder jemand gibt sich als Ihr PC aus), meldet sie „Das Zertifikat des PCs passt nicht zum QR-Code“ und sendet nichts. Ein Link, der **von außen** kommt (Kamera-App, Browser, andere App), verbindet erst nach einer Rückfrage mit der Anzeige der PC-Adresse; bestätigen Sie nur, wenn Sie den Code gerade selbst an Ihrem PC angezeigt haben. Links der alten, unverschlüsselten Version lehnt die App ab. Zeigen Sie den QR-Code nur Ihrem eigenen Handy: Er enthält den Schlüssel.

Der Schlüssel im Link gilt, solange der Austausch am PC läuft, höchstens eine begrenzte Zeit. Nach einem Neustart von Nolphin oder bei einem neuen Code (der auch ein neues Zertifikat bringt) scannen Sie einfach erneut. Beim ersten Verbinden lädt die App vorhandene Dateien nicht. Neue Dateien landen in `Download/Nolphin`.

## Aktualisieren

Hat Nolphin am PC eine neuere App (höhere `versionCode`), zeigt die Startseite **Neue Version**. Die neue App kommt über die verschlüsselte Verbindung. Ein Tipp auf **Aktualisieren** installiert sie über die alte, ohne Deinstallieren. Die Verbindung bleibt. Beim ersten Mal erlauben Sie der App einmal „Apps installieren“.

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

Das Skript zeichnet das Titelbild (`docs/bilder/titelbild-quelle.html`, mit dem Nolphin-Fenster aus dem Nolphin-Projekt und dem App-Symbol) und die Seiten aus `docs/bilder/quelle.html` mit Chrome oder Chromium (Beispieldaten, keine echten Dateien) und setzt sie mit `docs/bilder/gif.py` (Python mit Pillow) zu den beiden GIFs `funktionen-hell.gif` und `funktionen-dunkel.gif` zusammen. Die Reihenfolge und die Beschriftungen stehen am Anfang von `gif.py`, die Seiten in `quelle.html` (`#<seite>-<hell|dunkel>`). Die Quelle braucht beim Rendern Internet für die Symbolschrift.

## Aufbau des Quelltexts

| Datei (`src/org/nolphin/handy/`) | Aufgabe |
|---|---|
| `MainActivity` | Kopfleiste, Reiter, Verbindung, Versand |
| `HomeScreen`, `FromPcScreen`, `ToPcScreen`, `HistoryScreen`, `SettingsScreen` | Die Bildschirme |
| `ViewModes`, `FileUris`, `FilesProvider`, `SquareImage` | Regeln für Ansicht und Öffnen mit (ohne Android, getestet), Datei-Anbieter für andere Apps, quadratische Bildfläche der Kacheln |
| `SortOrder`, `PlaceLists` | Sortierreihenfolge, Favoriten und zuletzt geöffnete Orte (ohne Android, getestet) |
| `ToPcTabs`, `DualScreen` | Reiter „Zum PC“ mit einem oder zwei Fenstern |
| `TrashOps`, `TrashScreen` | Papierkorb: Logik (ohne Android, getestet) und Fenster |
| `StorageScan`, `StorageAnalysis` | Speicheranalyse: Zählen (ohne Android, getestet) und Fenster |
| `ZipOps`, `BatchRename` | ZIP entpacken und packen, Stapelumbenennen mit Vorschau (ohne Android, getestet) |
| `FileOps` | Kopieren, Verschieben, Löschen, Umbenennen, ZIP (sicher, mit Ergebnisliste, Abbruch und Konfliktregeln) |
| `Ui` | Farben (hell und dunkel), Symbole, Listenzeilen |
| `Thumbs` | Vorschaubilder (Foto, Video, Albumbild, PDF, App-Symbol) |
| `SyncService` | Laden und Senden im Hintergrund |
| `Link`, `Connector`, `PcCheck`, `Conn` | Link lesen (Version 2), Verbindung prüfen, gespeicherter Schlüssel und Fingerabdruck |
| `Secure` | der einzige Weg zum PC: nur `https`, nur mit dem Zertifikat des Fingerabdrucks (ohne Android, getestet) |
| `QrDecoder`, `ScanActivity` | Eigener QR-Leser und Scanner |
| `Updater`, `Store`, `ShareActivity` | Aktualisieren, Verlauf, Teilen-Menü |

Der PC-Teil (der kleine Server in Nolphin) liegt im Projekt [nolphin-file-manager](https://github.com/kaysiebke-cell/nolphin-file-manager) (`src/nolphin-phone-server.c`, `src/nolphin-phone.c`).

## Sicherheit und Grenzen

* Die Verbindung ist **verschlüsselt** (TLS 1.2/1.3) und an das Zertifikat aus dem QR-Code gebunden. Ein Mitlesen im WLAN nützt nichts. Der QR-Code enthält aber den **Schlüssel und den Fingerabdruck**: Wer ihn sieht oder fotografiert, kann sich bis zum Widerruf verbinden. Zeigen Sie ihn nur Ihrem eigenen Handy und beenden Sie den Austausch in Nolphin, wenn Sie fertig sind. **Neuen QR-Code erzeugen** widerruft sofort.
* Klartext ist ausgeschlossen: Die App spricht nur `https` und verbietet unverschlüsselten Verkehr auch auf Systemebene. Es gibt keinen Weg über den Browser des Handys. Wer am **PC selbst** Zugriff hat, wird dadurch nicht aufgehalten.
* Nolphin braucht am PC `openssl` und `glib-networking`. Die Erstinstallation der App erfolgt von Hand; Aktualisierungen kommen über die verschlüsselte Verbindung.
* Löschen geht in den Papierkorb (30 Tage), nur in dieser App. Aus dem Papierkorb heraus ist Löschen endgültig.
* Bei einem älteren Nolphin am PC geht ein Ordner nur als ZIP.
* Der Zugriff auf alle Dateien ist mächtig. Die App nutzt ihn nur für den Dateimanager, und nur nach Ihrer Aktion.
* Die privaten Ordner anderer Apps (`Android/data`, `Android/obb`) kann Android keiner App zeigen. Cloud-Dateien und Dateien anderer Apps holen Sie über die Systemauswahl (siehe oben).
