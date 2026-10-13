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
        ok(ViewModes.columns(360, false) == 3, "360 dp breit: 3 Spalten: " + ViewModes.columns(360, false));
        ok(ViewModes.columns(412, false) == 3 && ViewModes.columns(480, false) == 4, "breiter: mehr Spalten");
        ok(ViewModes.columns(360, true) == 2 && ViewModes.columns(600, true) == 3, "große Vorschau: größere Kacheln, weniger Spalten");
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
        ok(ViewModes.rowHeightDp(ViewModes.COMPACT, false) >= 40 && ViewModes.rowHeightDp(ViewModes.LIST, false) >= 48, "Touch-Ziele nicht zu klein");

        // Sortiermenü: neun Einträge, jeder rechnet hin und zurück
        int[] seen = new int[9];
        int count = 0;
        SortOrder none = new SortOrder(SortOrder.NONE, true, true);
        ok(ViewModes.sortKey(ViewModes.sortId(none)) == SortOrder.NONE && ViewModes.sortAscending(ViewModes.sortId(none)), "Keine Sortierung hin und zurück");
        seen[count++] = ViewModes.sortId(none);
        for (int k = SortOrder.NAME; k <= SortOrder.TYPE; k++) {
            for (int d = 0; d < 2; d++) {
                SortOrder o = new SortOrder(k, d == 0, true);
                int id = ViewModes.sortId(o);
                ok(ViewModes.sortKey(id) == k && ViewModes.sortAscending(id) == (d == 0), "Sortierung " + k + "/" + d + " hin und zurück");
                seen[count++] = id;
            }
        }
        boolean distinct = true;
        for (int i = 0; i < seen.length; i++) {
            for (int j = i + 1; j < seen.length; j++) {
                distinct &= seen[i] != seen[j];
            }
        }
        ok(count == 9 && distinct, "neun verschiedene Einträge im Sortiermenü");
        SortOrder std = SortOrder.standard();
        ok(ViewModes.sortKey(ViewModes.sortId(std)) == SortOrder.DATE && !ViewModes.sortAscending(ViewModes.sortId(std)), "Standard im Menü: Datum absteigend");

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
