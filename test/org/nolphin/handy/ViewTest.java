package org.nolphin.handy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Prüft Ansichtsregeln (Liste, Raster, Kompakt, Vorschaugröße, Sortiermenü) und die Regeln für externe Apps (ohne Handy). */
public class ViewTest {
    static int fails = 0, n = 0;

    static void ok(boolean c, String what) {
        n++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    public static void main(String[] a) {
        // Darstellung wird aus den Einstellungen gelesen; fehlt sie, gilt die alte Einstellung
        ok(ViewModes.fromPrefs(0, true) == ViewModes.LIST, "gespeicherte Liste");
        ok(ViewModes.fromPrefs(1, false) == ViewModes.GRID, "gespeichertes Raster");
        ok(ViewModes.fromPrefs(2, true) == ViewModes.COMPACT, "gespeicherte Kompaktansicht");
        ok(ViewModes.fromPrefs(-1, true) == ViewModes.GRID && ViewModes.fromPrefs(-1, false) == ViewModes.LIST, "alte Einstellung Kacheln/Liste");
        ok(ViewModes.fromPrefs(7, true) == ViewModes.GRID, "ungültiger Wert fällt auf die alte Einstellung zurück");

        // Raster passt sich der Breite an und bleibt dicht
        ok(ViewModes.columns(360, false) == 4, "360 dp breit: 4 Spalten: " + ViewModes.columns(360, false));
        ok(ViewModes.columns(412, false) == 4 && ViewModes.columns(480, false) == 5, "breiter: mehr Spalten");
        ok(ViewModes.columns(360, true) == 2 && ViewModes.columns(600, true) == 4, "große Vorschau: größere Kacheln, weniger Spalten");
        ok(ViewModes.columns(100, true) == 1 && ViewModes.columns(0, false) == 1, "nie weniger als eine Spalte");
        for (int w = 280; w <= 900; w += 10) {
            ok(ViewModes.columns(w, true) <= ViewModes.columns(w, false), "große Vorschau hat nie mehr Spalten als die Standard-Vorschau bei " + w);
            int cols = ViewModes.columns(w, false);
            int tile = (w - 12 - (cols - 1) * 4) / cols;
            ok(tile >= ViewModes.tileWidthDp(false), "Kachel mindestens so breit wie vorgesehen bei " + w + ": " + tile);
        }

        // Vorschaugröße und Darstellung sind unabhängig
        for (int mode = 0; mode <= 2; mode++) {
            ok(ViewModes.rowIconDp(mode, true) > ViewModes.rowIconDp(mode, false), "große Vorschau macht Symbole größer, Modus " + mode);
            ok(ViewModes.rowHeightDp(mode, true) > ViewModes.rowHeightDp(mode, false), "und Zeilen höher, Modus " + mode);
        }
        ok(ViewModes.rowHeightDp(ViewModes.COMPACT, false) < ViewModes.rowHeightDp(ViewModes.LIST, false), "Kompakt ist niedriger als Liste");
        ok(ViewModes.rowIconDp(ViewModes.COMPACT, false) <= 28, "kompakte Symbole klein");
        ok(ViewModes.rowHeightDp(ViewModes.COMPACT, false) >= 48 && ViewModes.rowHeightDp(ViewModes.LIST, false) >= 56, "zwei Textzeilen passen in jede Zeile, Touch-Ziele nicht zu klein");

        // Sortierauswahl: neun Einträge, jeder rechnet hin und zurück
        boolean[] seen = new boolean[ViewModes.SORT_CHOICES];
        for (int i = 0; i < ViewModes.SORT_CHOICES; i++) {
            SortOrder o = ViewModes.sortFromIndex(i, true);
            ok(ViewModes.sortIndex(o) == i, "Eintrag " + i + " hin und zurück");
            ok(o.foldersFirst, "Ordner zuerst bleibt erhalten, Eintrag " + i);
            seen[ViewModes.sortIndex(o)] = true;
        }
        boolean all = true;
        for (boolean b : seen) {
            all &= b;
        }
        ok(all && ViewModes.SORT_CHOICES == 9, "neun verschiedene Einträge");
        ok(ViewModes.sortFromIndex(0, false).key == SortOrder.NONE && !ViewModes.sortFromIndex(0, false).foldersFirst, "Eintrag 0 ist Keine Sortierung");
        ok(ViewModes.sortFromIndex(99, true).key == SortOrder.NONE && ViewModes.sortFromIndex(-3, true).key == SortOrder.NONE, "ungültiger Eintrag gilt als Keine");
        for (int k = SortOrder.NAME; k <= SortOrder.TYPE; k++) {
            ok(ViewModes.sortIndex(new SortOrder(k, true, true)) == 1 + k * 2 && ViewModes.sortIndex(new SortOrder(k, false, true)) == 2 + k * 2,
                    "Schlüssel " + k + ": aufsteigend vor absteigend");
        }
        SortOrder std = SortOrder.standard();
        ok(ViewModes.sortIndex(std) == 2 + SortOrder.DATE * 2, "Standard im Menü: Datum absteigend");

        // Externe Apps: nur Dateien unterhalb der Speicher, nie private App-Ordner
        List<String> roots = Collections.singletonList("/storage");
        ok(FileUris.isAllowed("/storage/emulated/0/DCIM/foto.jpg", roots), "Foto im Speicher erlaubt");
        ok(FileUris.isAllowed("/storage/1234-ABCD/Musik/lied.mp3", roots), "Datei auf der SD-Karte erlaubt");
        ok(!FileUris.isAllowed("/data/data/org.nolphin.handy/shared_prefs/store.xml", roots), "private Daten der App nicht erlaubt");
        ok(!FileUris.isAllowed("/storage/emulated/0/Android/data/andere.app/datei.db", roots), "private Ordner anderer Apps nicht erlaubt");
        ok(!FileUris.isAllowed("/storage/emulated/0/Android/obb/x/y.obb", roots), "obb nicht erlaubt");
        ok(!FileUris.isAllowed("/storagefremd/x", roots) && !FileUris.isAllowed("/storage", roots), "nur echte Unterpfade");
        ok(!FileUris.isAllowed("", roots) && !FileUris.isAllowed(null, roots) && !FileUris.isAllowed("storage/x/y", roots), "leer und relativ nicht erlaubt");
        ok(!FileUris.isAllowed("/etc/passwd", roots), "Systemdateien nicht erlaubt");

        // Meldung nach der Rückkehr
        ok(FileUris.afterReturn(true, true, 10, 100, 12, 200).equals("Die Datei wurde geändert."), "geändert erkannt");
        ok(FileUris.afterReturn(true, true, 10, 100, 10, 200).equals("Die Datei wurde geändert."), "nur Zeitstempel geändert");
        ok(FileUris.afterReturn(true, true, 10, 100, 10, 100).startsWith("Die Originaldatei ist unverändert"), "Bearbeiten ohne Änderung: ehrlicher Hinweis");
        ok(FileUris.afterReturn(false, true, 10, 100, 10, 100) == null, "nur Ansehen ohne Änderung: keine Meldung");
        ok(FileUris.afterReturn(false, false, 10, 100, 0, 0).contains("nicht mehr"), "Datei weg");
        ok(FileUris.afterReturn(true, false, 10, 100, 0, 0).contains("nicht mehr"), "Datei weg beim Bearbeiten");

        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden");
        if (fails > 0) {
            System.exit(1);
        }
    }
}
