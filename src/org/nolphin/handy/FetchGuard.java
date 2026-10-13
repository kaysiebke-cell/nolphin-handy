package org.nolphin.handy;

import java.util.HashSet;
import java.util.Set;

/** Verhindert doppelte Downloads derselben Datei und doppelt ausgelöste Uploads (ohne Android, getestet). */
final class FetchGuard {
    private final Set<String> fetching = new HashSet<>();
    private String lastUpload = "";
    private long lastUploadAt;
    private final long window;

    FetchGuard(long uploadWindowMs) {
        this.window = uploadWindowMs;
    }

    /** true, wenn das Laden starten darf; false, wenn dieselbe Datei schon geladen wird. */
    synchronized boolean beginFetch(String key) {
        return fetching.add(key);
    }

    synchronized void endFetch(String key) {
        fetching.remove(key);
    }

    /** true, wenn derselbe Upload nicht gerade eben schon gestartet wurde. */
    synchronized boolean beginUpload(String key, long now) {
        if (key.equals(lastUpload) && now - lastUploadAt < window) {
            return false;
        }
        lastUpload = key;
        lastUploadAt = now;
        return true;
    }
}
