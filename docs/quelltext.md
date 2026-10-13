# Aufbau des Quelltexts

Welche Datei was tut.

[← Zurück zur Übersicht](../README.md)

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
