package org.nolphin.handy;

import android.app.Activity;
import android.net.Uri;

import java.util.List;

/** Was die Bildschirme von der Hauptseite brauchen. */
interface Host {
    int TAB_HOME = 0, TAB_FROM_PC = 1, TAB_TO_PC = 2, TAB_HISTORY = 3, TAB_SETTINGS = 4;

    Activity activity();

    void show(int tab);

    /** Blendet die Reiterleiste unten aus oder ein (während einer Auswahl steht dort die Aktionsleiste). */
    void setNavVisible(boolean visible);

    void scan();

    /** Verbindet mit dem Link aus der Zwischenablage (Ersatz für den QR-Scanner). */
    void pasteLink();

    void disconnect();

    void cancelTransfer();

    /** Meldung des letzten Verbindungsversuchs oder null. */
    String error();

    void fetch(RemoteFile file);

    void send(List<Uri> uris);

    void pickFiles();

    /** Fotos und Videos aus der Fotoauswahl des Systems wählen; dort erscheinen auch die Bilder aus Google Fotos (Cloud). */
    void pickPhotos();

    /** Dateien des Handy-Speichers (Pfade) an den PC senden. */
    void sendPaths(List<String> paths);

    /** Darf die App alle Ordner lesen (Dateimanager)? */
    boolean hasFilesAccess();

    void requestFilesAccess();

    /** Zielordner für Dateien vom PC wählen (auch SD-Karte). */
    void pickTargetFolder();

    /** Eine geladene Datei an einen frei gewählten Ort kopieren („Speichern unter …“). */
    void saveAs(String name, String mime, Uri source);

    boolean hasMediaAccess();

    void requestMediaAccess();
}
