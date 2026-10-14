# Startbildschirm-Widget (Version 4.30)

Stand: 13.10.2026. Dieses Dokument beschreibt das Widget und nennt ehrlich, was geprüft ist.

## Was das Widget kann

* **Drei Einträge in der Widget-Auswahl:** **Nolphin Handy klein** (4×1: eine Zeile mit Zustand und einer Schaltfläche – Senden, Scannen, Verbinden oder Abbrechen), **Nolphin Handy kompakt** (4×2: Zustand, Übertragung, bis zu drei Schaltflächen) und **Nolphin Handy erweitert** (4×4: zusätzlich Dateien vom PC mit „Laden“ und Verlauf „Zuletzt“). Alle laufen über denselben Code (`NolphinWidget`, `NolphinWidgetSmall`, `NolphinWidgetLarge`). Hell und dunkel folgen dem System.
* **Zustand:** *Verbunden*, *Verbindung gestört* (PC nicht erreichbar oder Schlüssel ungültig), *Nicht aktiv* (Verbindungsdaten gespeichert, aber der Dienst läuft nicht) und *Nicht verbunden*. Das Widget zeigt „Verbunden“ nur, wenn Verbindungsdaten **und** der laufende Synchronisationsdienst vorhanden sind. Gespeicherte Daten allein reichen nicht.
* **Übertragung:** Name, Laden oder Senden, Prozent (wenn die Gesamtgröße bekannt ist), bisherige und gesamte Menge, **Abbrechen**. Eine Übertragung wird nur gezeigt, solange der Dienst läuft und sein Zustand höchstens 20 Sekunden alt ist.
* **Erweitert, wie im abgesegneten Entwurf:** Übertragungskarte (Name, Prozent, Balken, Menge, Abbrechen); darunter **„Bereit am PC (n)“** mit Dateien, Größe und **Laden** (`SyncService.ACTION_FETCH`), oder, wenn nichts bereit ist, **„Zuletzt“** mit den letzten Verlaufseinträgen (Symbol Laden oder Senden, Name, Uhrzeit). Beides erscheint auch während einer Übertragung. Unten zwei Schaltflächen: **Alle anzeigen** (öffnet „Vom PC“) und **Dateien senden**. Im kompakten Widget: Symbole über den Schaltflächen, rote oder grüne Statuspunkte, Handy-Symbol je nach Zustand.
* **Senden:** öffnet den Dateimanager „Zum PC“ der App. Dort wählen Sie Dateien **und Ordner** aus (Menü „Auswählen“) und senden sie; die Android-Dateiauswahl, die das Widget früher öffnete, zeigt keine Ordner. Der Versand läuft über `SyncService.ACTION_UPLOAD`.
* **Aktionen:** Öffnen, QR-Code scannen (der vorhandene Scanner), Verbinden (öffnet die App, die den Dienst startet), Trennen (löscht die Verbindungsdaten und stoppt den Dienst).

## Wie es aktualisiert wird

* Sofort, ohne Netzwerk, aus dem Zustand des Dienstes: bei neuer oder gelöschter Verbindung (`Conn.save`, `Conn.clear`), Statuswechsel, Beginn, Fortschritt (höchstens einmal pro Sekunde), Ende und Abbruch einer Übertragung, neuer Dateiliste und Start oder Ende des Dienstes, sowie bei neuen Verlaufseinträgen.
* Zusätzlich alle 30 Minuten (`updatePeriodMillis`, Android erlaubt nicht weniger) und bei Größenänderung. Es gibt keine Sekundentakt-Schleife und keine eigene Netzwerkverbindung des Widgets.
* Nach einem Prozessende zeigt das Widget nichts Falsches: Ohne laufenden Dienst gilt es als „Nicht aktiv“, Übertragungen und die Dateiliste werden nicht angezeigt.

## Sicherheit

* Die Schaltflächen, die den Dienst steuern, laufen über einen **nicht exportierten** Empfänger (`WidgetActions`) und unveränderliche `PendingIntent`. Andere Apps können sie nicht auslösen. Der exportierte Widget-Anbieter nimmt nur Systemaktualisierungen an.
* Es gibt keine neue Verbindungs- oder Anmeldelogik: Verbunden wird nur über die vorhandene Token- und Fingerabdruck-Prüfung.
* Dieselbe Datei wird nicht doppelt geladen, ein doppelt ausgelöster Versand (gleiche Dateien innerhalb von 3 Sekunden) wird ignoriert (`FetchGuard`).
* Das Widget fängt Fehler beim Aktualisieren ab und bringt weder App noch Dienst zum Absturz.

## Geänderte und neue Dateien

Neu: `NolphinWidget.java`, `WidgetActions.java`, `WidgetModel.java`, `FetchGuard.java`, `res/layout/widget_compact.xml`, `res/layout/widget_large.xml`, `res/xml/widget_info.xml`, `res/drawable/widget_*.xml`, `test/org/nolphin/handy/WidgetTest.java`.
Geändert: `SyncService.java` (Zustandsmarken, Aktualisierungsaufrufe, Schutz vor Doppelstarts), `Conn.java`, `Store.java` (Aktualisierung), `MainActivity.java` (Aktionen „Senden“ und „Vom PC“ aus dem Widget), `AndroidManifest.xml`, `res/values*/colors.xml`, `res/values/strings.xml`, `test.sh`.

## Geprüft

* `./build.sh`: APK gebaut.
* `./test.sh`: alle Prüfungen bestanden, darunter 38 neue zu den Widget-Regeln (Zustand, veraltete Übertragung, Prozent, Dateizeilen, Doppelstarts).
* **Am Handy mit verbundenem PC (13.10.2026, Xiaomi, dunkel):** Das erweiterte Widget zeigt „Verbunden“ mit PC-Namen, „Bereit am PC (n)“ mit Größen und „Laden“; „Laden“ löst den echten Download aus (57,2 MB in unter 2 Sekunden fertig; 1,46 GB mit Übertragungskarte, Prozent, Balken und Menge, die live wuchs: 2 %, 17 %, 22 %, 59 %); „Alle anzeigen“ öffnet „Vom PC“; nach einem Neuinstallieren zeigt es ehrlich „Nicht aktiv“ und nach „Verbinden“ wieder „Verbunden“; **Abbrechen** stoppte den Download (Karte verschwindet, es bleibt keine halbe Datei, die Zieldatei wurde nicht vollständig). Die erste Version von „Abbrechen“ war nur Text und traf mein Tippen nicht; sie ist jetzt ein echter Knopf.
* Am Handy (Xiaomi, dunkel): Widget erscheint in der Widget-Auswahl (4×2), lässt sich hinzufügen, zeigt „Nicht verbunden“ mit „QR-Code scannen“ und „Öffnen“, zeigt in großer Darstellung den letzten Verlaufseintrag, und „QR-Code scannen“ öffnet den Scanner der App.

## Verlauf zeigt den echten Grund

Im Verlaufsbereich steht rechts die Uhrzeit, bei Fehlern der gespeicherte Grund („abgebrochen“ oder „fehlgeschlagen“) statt immer „fehlgeschlagen“.

## Nicht geprüft

* **Das kleine Widget ist am Handy nicht hinzugefügt und nicht angesehen.** Es ist gebaut, das System kennt es als Anbieter, und seine Anzeigeregeln sind getestet (Zustände, Schaltfläche, Übertragung). Das Aussehen ist nicht mit dem Entwurf abgeglichen.

* **Nicht am Handy gesehen:** „Verbindung gestört“, Senden mit Fortschritt (Upload), Trennen, „Dateien senden“ bis zum Ende und das kompakte Widget im Zustand „Verbunden“ (es ist nicht auf dem Startbildschirm). Der Abbruch wurde am Handy nur über seine Wirkung gesehen (Karte weg, keine zweite Datei), nicht über den Verlaufseintrag.
* Verhalten nach hartem Beenden des Prozesses bei laufender Übertragung am Handy; Entfernen des Widgets; hell.
* Fehlende Berechtigungen und Netzwerkfehler am Handy.
* Das erweiterte Widget als eigener Eintrag auf dem Startbildschirm (auf der gerade offenen Seite war kein Platz mehr, deshalb habe ich es dort nicht hinzugefügt); gesehen habe ich beide Layouts nur über das vergrößerte erste Widget. Andere Launcher als der von Xiaomi.
* Das Widget zeigt ohne Icons im Vorschaubild der Auswahl nur das App-Symbol.
