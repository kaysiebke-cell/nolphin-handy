package org.nolphin.handy;

import android.view.View;

/** Ein Bildschirm der App (Reiter oder Einstellungen). */
interface Screen {
    View view();

    /** Wird aufgerufen, wenn der Bildschirm sichtbar wird. */
    void onShow();

    /** Wird einmal pro Sekunde aufgerufen, solange der Bildschirm sichtbar ist. */
    void refresh();

    /** Zurück-Taste: true, wenn der Bildschirm sie selbst verwendet hat (zum Beispiel eine Ordnerebene hoch). */
    default boolean onBack() {
        return false;
    }
}
