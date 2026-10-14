package org.nolphin.handy;

/**
 * Die eine Meldung der schmalen Leiste über den Reitern („2 neu am PC“, „Macros eingefügt“, Fehler). Es gibt immer nur die
 * neueste (sie ersetzt die vorige). Eine Info verschwindet nach {@link #INFO_MS}, ein Fehler bleibt, bis man ihn wegwischt oder antippt.
 * Reines Java, ohne Android-Klassen.
 */
final class StatusNote {
    private StatusNote() {}

    static final long INFO_MS = 8000;

    static final class Note {
        final String text;
        final boolean error;
        final int tab;        // Reiter, der beim Tippen aufgeht (Host.TAB_*)
        final long at;
        final int id;

        Note(String text, boolean error, int tab, long at, int id) {
            this.text = text;
            this.error = error;
            this.tab = tab;
            this.at = at;
            this.id = id;
        }
    }

    private static Note current;
    private static int nextId = 1;

    static synchronized void post(String text, boolean error, int tab, long now) {
        if (text == null || text.isEmpty()) {
            return;
        }
        current = new Note(text, error, tab, now, nextId++);
    }

    /** Die Meldung, die jetzt zu zeigen ist, oder null. */
    static synchronized Note current(long now) {
        if (current != null && !current.error && now - current.at >= INFO_MS) {
            current = null;
        }
        return current;
    }

    static synchronized void dismiss() {
        current = null;
    }

    /** Die Zeile für eine Übertragung vom PC, die gerade läuft, oder null ({@code transfer} wie in SyncService). */
    static String progressLine(String transfer) {
        return transfer == null || transfer.isEmpty() ? null : transfer;
    }

    /** Text für Dateien oder Ordner, die angekommen sind: „Macros eingefügt, 269 Dateien“. */
    static String arrived(String name, boolean pasted, int files) {
        String verb = pasted ? "eingefügt" : "geladen";
        return name + " " + verb + (files > 1 ? ", " + files + " Dateien" : "");
    }

    /** „2 neu am PC“ oder „strand.jpg neu am PC“. */
    static String fresh(int count, String firstName) {
        return count == 1 && firstName != null ? firstName + " neu am PC" : count + " neu am PC";
    }
}
