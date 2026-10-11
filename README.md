# Nolphin Handy (Android-App)

Kleine App für den Handy-Austausch von Nolphin. Sie ist der **Empfänger auf dem Handy**: Legen Sie am PC Dateien in „Zum Handy“, kommen sie von selbst auf das Handy. Dateien vom Handy schicken Sie über das Teilen-Menü an den PC.

Nur das Android-Framework, **keine Bibliotheken**, kein Gradle. Mindestens Android 10.

## Einrichten (Anwender)

1. Klicken Sie in Nolphin in der Seitenleiste auf **Handy** und scannen Sie den QR-Code mit der Kamera des Handys.
2. **Erster Scan:** Die Seite im Handy-Browser bietet die App zum Installieren an. Laden Sie sie und erlauben Sie die Installation (Warnungen von Chrome, Play Protect oder Mi Protect sind bei Apps außerhalb des Play Store normal).
3. **Verbinden:** Öffnen Sie die App, tippen Sie auf **QR-Code scannen** und richten Sie die Kamera auf den QR-Code am PC. Die App liest ihn mit einem **eigenen QR-Leser** (`QrDecoder.java`, ohne Bibliothek und ohne Google-Dienste). Zum Scharfstellen tippen Sie aufs Bild. Alternativ scannen Sie mit der Kamera des Handys: Die Seite öffnet dann die App und verbindet sie.
4. Der Schlüssel im Code gilt, solange der Austausch am PC läuft; nach einem Neustart von Nolphin scannen Sie einfach erneut.

Die App lädt neue Dateien nach `Download/Nolphin`. Beim allerersten Verbinden werden bereits vorhandene Dateien nicht geladen.

## Bauen (Entwickler)

Voraussetzungen: JDK (`javac`, `keytool`) und das Android-SDK (`android.jar`, `aapt2`, `d8`, `zipalign`, `apksigner`).

```bash
android/build.sh
```

Ergebnis: `android/build/nolphin-handy.apk`.

Der QR-Leser lässt sich ohne Handy prüfen (`android/test.sh`): Er liest Codes des C-Encoders von Nolphin, gedreht, verzerrt, unscharf und verrauscht gezeichnet, einen Bildschirmfoto-Ausschnitt und ein echtes, dunkles und unscharfes Handyfoto des Codes. Beim ersten Lauf entsteht ein Signaturschlüssel in `~/.config/nolphin/` (nicht im Repository); sein Passwort liegt im Systemschlüsselbund (`secret-tool`, Paket `libsecret-tools`), nie in einer Datei. Liegt die APK vor dem Installieren von Nolphin da, wird sie nach `<datadir>/nolphin/phone/` mitinstalliert und der Handy-Austausch bietet sie zum Laden an. Ohne APK läuft alles weiter im Handy-Browser.
