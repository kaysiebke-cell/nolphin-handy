# Sicherheit und Grenzen

Wie die Verbindung geschützt ist und wo die Grenzen liegen.

[← Zurück zur Übersicht](../README.md)

## Sicherheit und Grenzen

* Die Verbindung ist **verschlüsselt** (TLS 1.2/1.3) und an das Zertifikat aus dem QR-Code gebunden. Ein Mitlesen im WLAN nützt nichts. Der QR-Code enthält aber den **Schlüssel und den Fingerabdruck**: Wer ihn sieht oder fotografiert, kann sich bis zum Widerruf verbinden. Zeigen Sie ihn nur Ihrem eigenen Handy und beenden Sie den Austausch in Nolphin, wenn Sie fertig sind. **Neuen QR-Code erzeugen** widerruft sofort.
* Klartext ist ausgeschlossen: Die App spricht nur `https` und verbietet unverschlüsselten Verkehr auch auf Systemebene. Es gibt keinen Weg über den Browser des Handys. Wer am **PC selbst** Zugriff hat, wird dadurch nicht aufgehalten.
* Nolphin braucht am PC `openssl` und `glib-networking`. Die Erstinstallation der App erfolgt von Hand; Aktualisierungen kommen über die verschlüsselte Verbindung.
* Löschen geht in den Papierkorb (30 Tage), nur in dieser App. Aus dem Papierkorb heraus ist Löschen endgültig.
* Bei einem älteren Nolphin am PC geht ein Ordner nur als ZIP.
* Der Zugriff auf alle Dateien ist mächtig. Die App nutzt ihn nur für den Dateimanager, und nur nach Ihrer Aktion.
* Die privaten Ordner anderer Apps (`Android/data`, `Android/obb`) kann Android keiner App zeigen. Cloud-Dateien und Dateien anderer Apps holen Sie über die Systemauswahl (siehe oben).
