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

    static void clear(Context c) {
        prefs(c).edit().remove("host").remove("token").remove("pin").apply();
    }

    /** Speichert eine geprüfte Verbindung (siehe Connector). Ein neuer Schlüssel gilt ab sofort. */
    static void save(Context c, Link link, String pcName) {
        prefs(c).edit().putString("host", link.authority()).putString("token", link.token).putString("pin", link.fingerprint)
                .putLong("expires", link.expires).putString("pcname", pcName == null ? "" : pcName).apply();
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

    /** Sortierung der Dateilisten (bleibt gespeichert). */
    static SortOrder sortOrder(Context c) {
        SharedPreferences p = prefs(c);
        return new SortOrder(p.getInt("sortkey", SortOrder.NAME), p.getBoolean("sortasc", true), p.getBoolean("foldersfirst", true));
    }

    /** Hat der Nutzer schon eine Sortierung gewählt? */
    static boolean hasSortOrder(Context c) {
        return prefs(c).contains("sortkey");
    }

    static void setSortOrder(Context c, SortOrder o) {
        prefs(c).edit().putInt("sortkey", o.key).putBoolean("sortasc", o.ascending).putBoolean("foldersfirst", o.foldersFirst).apply();
    }

    static void clearSortOrder(Context c) {
        prefs(c).edit().remove("sortkey").remove("sortasc").remove("foldersfirst").apply();
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
