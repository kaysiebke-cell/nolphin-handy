package org.nolphin.handy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
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

    static boolean connected(Context c) {
        return host(c) != null && token(c) != null;
    }

    static void clear(Context c) {
        prefs(c).edit().remove("host").remove("token").apply();
    }

    /** Speichert eine geprüfte Verbindung (siehe Connector). Ein neuer Schlüssel gilt ab sofort. */
    static void save(Context c, Link link, String pcName) {
        prefs(c).edit().putString("host", link.authority()).putString("token", link.token)
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
