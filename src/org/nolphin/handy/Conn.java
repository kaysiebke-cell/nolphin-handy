package org.nolphin.handy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Gespeicherte Verbindung zum PC und die Liste der schon geladenen Dateien. */
final class Conn {
    private Conn() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nolphin", Context.MODE_PRIVATE);
    }

    /** "host:port" des PCs oder null. */
    static String host(Context c) {
        return prefs(c).getString("host", null);
    }

    static String token(Context c) {
        return prefs(c).getString("token", null);
    }

    /** Fingerabdruck des Zertifikats des PCs (aus dem QR-Code) oder null. */
    static String pin(Context c) {
        return prefs(c).getString("pin", null);
    }

    /** Eine gespeicherte Verbindung ohne Fingerabdruck (aus einer älteren, unverschlüsselten Version) gilt nicht mehr. */
    static boolean connected(Context c) {
        return host(c) != null && token(c) != null && pin(c) != null;
    }

    /** Adresse aus dem QR-Code, solange gerade eine andere (Kabelnetz) gilt; sonst null. */
    static String homeHost(Context c) {
        return prefs(c).getString("homehost", null);
    }

    /** Wechselt auf eine andere Adresse desselben PCs (zum Beispiel im Kabelnetz); die aus dem QR-Code bleibt gemerkt. */
    static void useAltHost(Context c, String host) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (homeHost(c) == null && host(c) != null) {
            e.putString("homehost", host(c));
        }
        e.putString("host", host).apply();
        NolphinWidget.refresh(c);
    }

    /** Zurück zur Adresse aus dem QR-Code. */
    static void restoreHome(Context c) {
        String home = homeHost(c);
        if (home != null) {
            prefs(c).edit().putString("host", home).remove("homehost").apply();
            NolphinWidget.refresh(c);
        }
    }

    static void clear(Context c) {
        prefs(c).edit().remove("host").remove("homehost").remove("token").remove("pin").apply();
        NolphinWidget.refresh(c);
    }

    /** Speichert eine geprüfte Verbindung (siehe Connector). Ein neuer Schlüssel gilt ab sofort. */
    static void save(Context c, Link link, String pcName) {
        prefs(c).edit().remove("homehost").putString("host", link.authority()).putString("token", link.token).putString("pin", link.fingerprint)
                .putLong("expires", link.expires).putString("pcname", pcName == null ? "" : pcName).apply();
        NolphinWidget.refresh(c);
    }

    /** Name des PCs (aus /info), leer wenn unbekannt. */
    static String pcName(Context c) {
        return prefs(c).getString("pcname", "");
    }

    /** Neue Dateien vom PC sofort laden (Vorgabe) oder nur anbieten. */
    static boolean autoLoad(Context c) {
        return prefs(c).getBoolean("autoload", true);
    }

    static void setAutoLoad(Context c, boolean value) {
        prefs(c).edit().putBoolean("autoload", value).apply();
    }

    /** Aus diesen Ordnern (Namen oder Pfade wie "Pictures/Bearbeitet", mit Unterordnern) zeigt „Zum PC“ in den Kategorien nichts. */
    static Set<String> hiddenFolders(Context c) {
        return new HashSet<>(prefs(c).getStringSet("hiddenfolders", new HashSet<String>()));
    }

    static void setHiddenFolders(Context c, Set<String> folders) {
        prefs(c).edit().putStringSet("hiddenfolders", new HashSet<>(folders)).apply();
    }

    /** Vom PC geladene Ordner aus der Galerie des Handys ausblenden (Datei „.nomedia“); Vorgabe an. */
    static boolean hideGallery(Context c) {
        return prefs(c).getBoolean("hidegallery", true);
    }

    static void setHideGallery(Context c, boolean on) {
        prefs(c).edit().putBoolean("hidegallery", on).apply();
    }

    /** Unterordner in „Downloads“ für Dateien vom PC (Vorgabe „Nolphin“). */
    static String folder(Context c) {
        String name = prefs(c).getString("folder", "Nolphin");
        name = name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        return name.isEmpty() ? "Nolphin" : (name.length() > 40 ? name.substring(0, 40) : name);
    }

    static void setFolder(Context c, String name) {
        prefs(c).edit().putString("folder", name == null ? "" : name).apply();
    }

    /** Vom Nutzer gewählter Zielordner (Handy oder SD-Karte) als Dokument-Baum-Adresse oder null für „Downloads“. */
    static String targetTree(Context c) {
        return prefs(c).getString("targettree", null);
    }

    static void setTargetTree(Context c, String uri) {
        if (uri == null) {
            prefs(c).edit().remove("targettree").apply();
        } else {
            prefs(c).edit().putString("targettree", uri).apply();
        }
    }

    /** Kachelansicht (Vorgabe) oder Liste in „Vom PC“. */
    static boolean grid(Context c) {
        return prefs(c).getBoolean("tiles", true);
    }

    static void setGrid(Context c, boolean value) {
        prefs(c).edit().putBoolean("tiles", value).apply();
    }

    static final int VIEW_LIST = ViewModes.LIST, VIEW_GRID = ViewModes.GRID, VIEW_COMPACT = ViewModes.COMPACT;

    /**
     * Einstellungen für Ansicht und Sortierung. {@code pane} ist null im einzelnen Fenster; die zwei Fenster des
     * Zwei-Fenster-Modus haben je eigene Werte ("Schlüssel@Fenster"). Hat ein Fenster noch keine eigenen, gelten die
     * des einzelnen Fensters als Ausgangswert.
     */
    private static String key(String base, String pane) {
        return pane == null ? base : base + "@" + pane;
    }

    private static String own(SharedPreferences p, String base, String pane) {
        return pane != null && p.contains(key(base, pane)) ? key(base, pane) : base;
    }

    /** Darstellung der Dateilisten: Liste, Raster oder Kompakt. Frühere Versionen kannten nur Raster oder Liste. */
    static int viewMode(Context c, String pane) {
        SharedPreferences p = prefs(c);
        return ViewModes.fromPrefs(p.getInt(own(p, "viewmode", pane), -1), p.getBoolean("tiles", true));
    }

    static void setViewMode(Context c, int mode, String pane) {
        prefs(c).edit().putInt(key("viewmode", pane), mode).apply();
    }

    /** Große Vorschau (unabhängig von der Darstellung). */
    static boolean largePreview(Context c, String pane) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(own(p, "largepreview", pane), false);
    }

    static void setLargePreview(Context c, boolean on, String pane) {
        prefs(c).edit().putBoolean(key("largepreview", pane), on).apply();
    }

    static boolean hideNames(Context c, String pane) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(own(p, "hidenames", pane), false);
    }

    static void setHideNames(Context c, boolean on, String pane) {
        prefs(c).edit().putBoolean(key("hidenames", pane), on).apply();
    }

    static boolean showHidden(Context c, String pane) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(own(p, "showhidden", pane), false);
    }

    static void setShowHidden(Context c, boolean on, String pane) {
        prefs(c).edit().putBoolean(key("showhidden", pane), on).apply();
    }

    /** Sortierung der Dateilisten (bleibt gespeichert). */
    static SortOrder sortOrder(Context c, String pane) {
        SharedPreferences p = prefs(c);
        String k = own(p, "sortkey", pane);
        String suffix = k.equals("sortkey") ? "" : "@" + pane;
        return new SortOrder(p.getInt(k, SortOrder.NAME), p.getBoolean("sortasc" + suffix, true), p.getBoolean("foldersfirst" + suffix, true));
    }

    /** Hat der Nutzer schon eine Sortierung gewählt? */
    static boolean hasSortOrder(Context c, String pane) {
        SharedPreferences p = prefs(c);
        return p.contains(key("sortkey", pane)) || p.contains("sortkey");
    }

    static void setSortOrder(Context c, SortOrder o, String pane) {
        prefs(c).edit().putInt(key("sortkey", pane), o.key).putBoolean(key("sortasc", pane), o.ascending)
                .putBoolean(key("foldersfirst", pane), o.foldersFirst).apply();
    }

    /** Setzt Ansicht, Vorschau, Namen, versteckte Dateien und Sortierung des Fensters auf den Standard zurück. */
    static void resetView(Context c, String pane) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (String base : new String[] { "viewmode", "largepreview", "hidenames", "showhidden", "sortkey", "sortasc", "foldersfirst" }) {
            e.remove(key(base, pane));
        }
        if (pane != null) {
            // Ausgangswert bleibt der des einzelnen Fensters; ein Zurücksetzen soll wirklich den Standard liefern
            SortOrder std = SortOrder.standard();
            e.putInt(key("viewmode", pane), VIEW_LIST).putBoolean(key("largepreview", pane), false)
                    .putBoolean(key("hidenames", pane), false).putBoolean(key("showhidden", pane), false)
                    .putInt(key("sortkey", pane), std.key).putBoolean(key("sortasc", pane), std.ascending)
                    .putBoolean(key("foldersfirst", pane), std.foldersFirst);
        }
        e.apply();
    }

    /** Wird der Zwei-Fenster-Modus angeboten? (Einstellung, Standard: aus) */
    static boolean dualAvailable(Context c) {
        return prefs(c).getBoolean("dualavailable", false);
    }

    static void setDualAvailable(Context c, boolean on) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean("dualavailable", on);
        if (!on) {
            e.putBoolean("dualactive", false);
        }
        e.apply();
    }

    /** Zeigt "Zum PC" gerade zwei Fenster? Bleibt beim Neustart erhalten. */
    static boolean dualActive(Context c) {
        return dualAvailable(c) && prefs(c).getBoolean("dualactive", false);
    }

    static void setDualActive(Context c, boolean on) {
        prefs(c).edit().putBoolean("dualactive", on).apply();
    }

    /** Zuletzt geöffneter Ordner eines Fensters (damit ein Fenster nach dem Drehen oder Neustart wieder dort steht). */
    static String paneDir(Context c, String paneId) {
        return prefs(c).getString("pane_" + paneId, null);
    }

    static void setPaneDir(Context c, String paneId, String path) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (path == null) {
            e.remove("pane_" + paneId);
        } else {
            e.putString("pane_" + paneId, path);
        }
        e.apply();
    }

    /** Favoriten (Ordnerpfade) in der Reihenfolge, in der sie angelegt wurden. */
    static List<String> favorites(Context c) {
        return PlaceLists.parse(prefs(c).getString("favorites", ""));
    }

    static void setFavorites(Context c, List<String> list) {
        prefs(c).edit().putString("favorites", PlaceLists.join(list)).apply();
    }

    static boolean isFavorite(Context c, String path) {
        return favorites(c).contains(path);
    }

    /** Zuletzt geöffnete Ordner, der neueste zuerst. */
    static List<String> recents(Context c) {
        return PlaceLists.parse(prefs(c).getString("recents", ""));
    }

    static void pushRecent(Context c, String path) {
        prefs(c).edit().putString("recents", PlaceLists.join(PlaceLists.pushFront(recents(c), path, PlaceLists.MAX_RECENT))).apply();
    }

    static void clearRecents(Context c) {
        prefs(c).edit().remove("recents").apply();
    }

    /** Dateien, die schon auf dem Handy sind (Name und Größe). */
    static Set<String> seen(Context c) {
        return new HashSet<>(prefs(c).getStringSet("seen", new HashSet<String>()));
    }

    static void saveSeen(Context c, Set<String> seen) {
        prefs(c).edit().putStringSet("seen", new HashSet<>(seen)).apply();
    }

    /** Beim allerersten Verbinden werden vorhandene Dateien nicht geladen. */
    static boolean seeded(Context c) {
        return prefs(c).getBoolean("seeded", false);
    }

    static void setSeeded(Context c) {
        prefs(c).edit().putBoolean("seeded", true).apply();
    }
}
