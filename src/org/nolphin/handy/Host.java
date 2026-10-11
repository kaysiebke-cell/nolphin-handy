package org.nolphin.handy;

import android.app.Activity;
import android.net.Uri;

import java.util.List;

/** Was die Bildschirme von der Hauptseite brauchen. */
interface Host {
    int TAB_HOME = 0, TAB_FROM_PC = 1, TAB_TO_PC = 2, TAB_HISTORY = 3, TAB_SETTINGS = 4;

    Activity activity();

    void show(int tab);

    void scan();

    void disconnect();

    void cancelTransfer();

    /** Meldung des letzten Verbindungsversuchs oder null. */
    String error();

    void fetch(RemoteFile file);

    void send(List<Uri> uris);

    void pickFiles();

    boolean hasMediaAccess();

    void requestMediaAccess();
}
