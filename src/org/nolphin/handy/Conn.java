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
    static void save(Context c, Link link) {
        prefs(c).edit().putString("host", link.authority()).putString("token", link.token)
                .putLong("expires", link.expires).apply();
    }

    /** Vor dem Laden fragen, statt neue Dateien vom PC sofort zu laden. */
    static boolean confirmBeforeDownload(Context c) {
        return prefs(c).getBoolean("confirm", false);
    }

    static void setConfirmBeforeDownload(Context c, boolean value) {
        prefs(c).edit().putBoolean("confirm", value).apply();
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
