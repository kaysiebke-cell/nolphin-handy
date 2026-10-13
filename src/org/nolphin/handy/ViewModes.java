package org.nolphin.handy;

/** Regeln für Darstellungsform, Vorschaugröße und die Auswahl im Sortiermenü (ohne Android, damit sie sich testen lassen). */
final class ViewModes {
    private ViewModes() {}

    static final int LIST = 0, GRID = 1, COMPACT = 2;

    /** Gespeicherte Darstellung; fehlt sie (frühere Version), entscheidet die alte Einstellung Kacheln/Liste. */
    static int fromPrefs(int stored, boolean oldTiles) {
        if (stored >= LIST && stored <= COMPACT) {
            return stored;
        }
        return oldTiles ? GRID : LIST;
    }

    /** Breite einer Kachel in dp (Mindestbreite; die Kacheln dehnen sich auf die verfügbare Breite). */
    static int tileWidthDp(boolean large) {
        return large ? 128 : 84;
    }

    /** Anzahl Spalten im Raster bei gegebener Breite (dp), Rand und Abstand eingerechnet; mindestens 1. */
    static int columns(int widthDp, boolean large) {
        int tile = tileWidthDp(large);
        int usable = widthDp - 2 * 6;   // Rand links und rechts
        int cols = (usable + 4) / (tile + 4);
        return Math.max(1, cols);
    }

    /** Symbolgröße in der Liste und in der Kompaktansicht (dp). */
    static int rowIconDp(int mode, boolean large) {
        return mode == COMPACT ? (large ? 40 : 28) : (large ? 64 : 40);
    }

    /** Zeilenhöhe in dp. */
    static int rowHeightDp(int mode, boolean large) {
        return mode == COMPACT ? (large ? 60 : 48) : (large ? 80 : 56);
    }

    // Sortierauswahl: neun Einträge (0 = Keine, danach je Schlüssel auf- und absteigend)
    static final int SORT_CHOICES = 9;

    static int sortIndex(SortOrder o) {
        return o.key == SortOrder.NONE ? 0 : 1 + o.key * 2 + (o.ascending ? 0 : 1);
    }

    static SortOrder sortFromIndex(int index, boolean foldersFirst) {
        if (index <= 0 || index >= SORT_CHOICES) {
            return new SortOrder(SortOrder.NONE, true, foldersFirst);
        }
        return new SortOrder((index - 1) / 2, (index - 1) % 2 == 0, foldersFirst);
    }
}
