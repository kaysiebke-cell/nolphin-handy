# Startbildschirm-Widget (Version 4.30)

Stand: 13.10.2026. Dieses Dokument beschreibt das Widget und nennt ehrlich, was geprüft ist.

## Was das Widget kann

* **Zwei Größen:** kompakt (ab 4×2 Zellen: Zustand, Übertragung, bis zu drei Schaltflächen) und erweitert (ab etwa 180 dp Höhe und Breite: zusätzlich Dateien vom PC mit „Laden“, letzter Verlaufseintrag). Hell und dunkel folgen dem System.
* **Zustand:** *Verbunden*, *Verbindung gestört* (PC nicht erreichbar oder Schlüssel ungültig), *Nicht aktiv* (Verbindungsdaten gespeichert, aber der Dienst läuft nicht) und *Nicht verbunden*. Das Widget zeigt „Verbunden“ nur, wenn Verbindungsdaten **und** der laufende Synchronisationsdienst vorhanden sind. Gespeicherte Daten allein reichen nicht.
* **Übertragung:** Name, Laden oder Senden, Prozent (wenn die Gesamtgröße bekannt ist), bisherige und gesamte Menge, **Abbrechen**. Eine Übertragung wird nur gezeigt, solange der Dienst läuft und sein Zustand höchstens 20 Sekunden alt ist.
* **Dateien vom PC:** bis zu zwei bis drei Dateien (nach Höhe) mit Größe und **Laden** (`SyncService.ACTION_FETCH`), dazu „Alle anzeigen“ (öffnet „Vom PC“ in der App).
* **Senden:** öffnet die vorhandene Dateiauswahl der App; der Versand läuft über `SyncService.ACTION_UPLOAD` mit den bisherigen URI-Berechtigungen.
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
* Am Handy (Xiaomi, dunkel): Widget erscheint in der Widget-Auswahl (4×2), lässt sich hinzufügen, zeigt „Nicht verbunden“ mit „QR-Code scannen“ und „Öffnen“, zeigt in großer Darstellung den letzten Verlaufseintrag, und „QR-Code scannen“ öffnet den Scanner der App.

## Nicht geprüft

* **Alle Zustände mit PC-Verbindung:** Verbunden, Verbindung gestört, Dateien vom PC mit „Laden“, Fortschritt, Abbrechen, Senden und Trennen am Handy. Dafür braucht es einen verbundenen PC; ohne ihn lassen sie sich nicht ehrlich zeigen.
* Verhalten nach hartem Beenden des Prozesses bei laufender Übertragung am Handy; Entfernen des Widgets; hell.
* Fehlende Berechtigungen und Netzwerkfehler am Handy.
* Größen zwischen kompakt und erweitert und andere Launcher als der von Xiaomi.
* Das Widget zeigt ohne Icons im Vorschaubild der Auswahl nur das App-Symbol.
