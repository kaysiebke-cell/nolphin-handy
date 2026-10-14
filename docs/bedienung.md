# Bedienung der App

Die Reiter, der Dateimanager „Zum PC“ mit Ansicht, Sortierung, Auswahl, Papierkorb, ZIP, Speicheranalyse und zwei Fenstern, und wo die Dateien am PC landen.

[← Zurück zur Übersicht](../README.md)

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

**Auswahl mit einer Hand.** Wählen Sie im ☰-Menü **Auswählen** (in „Vom PC“ der Knopf **Auswählen**): Dann hat **jede Datei und jeder Ordner ein Kästchen**, und Antippen hakt an oder ab. Ohne den Modus wählt ein langer Druck aus. Sobald etwas gewählt ist, erscheint **oben** eine schmale Leiste: ✕ (Auswahl beenden), „n gewählt“, **Alles auswählen** und **Auswahl umkehren**. **Unten** stehen die Funktionen in Daumenreichweite: **Senden, Kopieren, Verschieben, Löschen** und **Mehr** (Umbenennen, Eigenschaften, Als ZIP packen, Öffnen mit …, Bearbeiten mit …; das Menü öffnet über der Leiste). Die Reiter (Start, Vom PC, Zum PC, Verlauf) sind währenddessen ausgeblendet und kommen mit ✕ oder nach der Aktion zurück. In „Vom PC“ gilt dasselbe mit **Laden, Teilen, Speichern**. Bei gewählten **Ordnern** zeigt „Vom PC“ nur **Laden**: Die App liest den Ordner samt Unterordnern am PC und lädt jede Datei in `Download/Nolphin/<Ordnername>` (abbrechbar, mit Meldung am Ende; höchstens 12 Ebenen und 5000 Dateien). **Kopieren und Einfügen vom PC:** In „Vom PC“ stehen bei gewählten Dateien und Ordnern **Kopieren, Laden, Teilen, Speichern**. **Kopieren** merkt die Auswahl; die App wechselt zu „Zum PC“. Gehen Sie in den Zielordner am Handy (auch auf die SD-Karte) und tippen Sie unten auf **Hierher** („N Einträge vom PC einfügen“). Die App lädt die Dateien samt Unterordnern direkt dorthin, auch leere Ordner. Vorhandene Dateien werden nie überschrieben (es entsteht „name (2).jpg“). Es gibt nur eine Zwischenablage: Kopieren auf dem Handy ersetzt die vom PC. **Laden** (Pfeil) bleibt die Abkürzung für den festen Ordner. Umgekehrt senden Sie Ordner vom Handy mit **Senden** in „Zum PC“; der Ordner kommt mit allen Unterordnern in `~/Handy/Vom Handy`. In den kleinen Fenstern des Zwei-Fenster-Modus bleibt die Leiste oben mit allen Funktionen.

**Meldungszeile.** Über den Reitern steht bei Bedarf eine schmale Zeile: die laufende Übertragung („Lade …: 40 %“), „2 neu am PC“ (wenn „Neue Dateien automatisch laden“ aus ist), „Macros eingefügt, 269 Dateien“ oder ein Fehler in Rot mit dem Namen der ersten fehlgeschlagenen Datei. Es gibt immer nur die neueste Meldung. Infos verschwinden nach 8 Sekunden, Fehler bleiben, bis Sie tippen oder nach unten wischen. Ein Tipp öffnet den passenden Reiter.

**Ordner ausblenden und Galerie.** In den Einstellungen trägst du unter **Ordner ausblenden** Ordnernamen ein (auch tiefer wie `Pictures/Bearbeitet`). „Zum PC“ zeigt dann in Bilder, Audio, Videos und Dokumente nichts aus diesen Ordnern, Unterordner eingeschlossen. Mit ✕ entfernst du einen Eintrag wieder. Die Ordner bleiben im Dateimanager erreichbar. Unter **Galerie des Handys** versteckt der Schalter „Vom PC geladene Ordner in der Galerie verstecken“ (Vorgabe: an) diese Ordner vor der Galerie-App des Handys: Sie bekommen die Datei `.nomedia`, die Dateien bleiben unverändert. Beim Einschalten gilt das auch für Ordner, die schon in `Download/Nolphin` liegen. Ordner, die du in DCIM oder Pictures einfügst, bleiben sichtbar. Die Galerie liest danach nach einer Weile neu ein.

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
