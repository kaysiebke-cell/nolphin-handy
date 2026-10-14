package org.nolphin.handy;

/**
 * Auswahl-Modus der Dateilisten (ohne Android, damit er sich testen lässt). Man startet ihn über das Menü „Auswählen“;
 * danach wählt jedes Antippen einen Eintrag (auch Ordner) aus oder ab. Er endet mit ✕ oder von selbst, sobald nach einer
 * Auswahl nichts mehr gewählt ist (zum Beispiel nach dem Senden oder wenn der letzte Eintrag abgewählt wird).
 */
final class SelectionMode {
    private boolean on;
    private boolean had;

    /** Menü „Auswählen“: der Modus beginnt, auch wenn noch nichts gewählt ist. */
    void begin() {
        on = true;
        had = false;
    }

    /** ✕ oder Zurück-Taste. */
    void end() {
        on = false;
        had = false;
    }

    /** Nach jeder Änderung der Auswahl aufrufen. */
    void update(int selectedCount) {
        if (selectedCount > 0) {
            had = true;
        } else if (had) {
            on = false;
            had = false;
        }
    }

    /** Zeigt die Liste gerade den Auswahl-Modus (Kästchen, Auswahlleiste)? */
    boolean active(int selectedCount) {
        return on || selectedCount > 0;
    }

    /** Wählt ein Antippen aus statt etwas zu öffnen? */
    boolean taps(int selectedCount) {
        return active(selectedCount);
    }
}
