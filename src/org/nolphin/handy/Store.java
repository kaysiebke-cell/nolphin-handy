package org.nolphin.handy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Verlauf der Übertragungen und die Orte bereits geladener Dateien (für Vorschau und Öffnen). */
final class Store {
    private Store() {}

    private static final int MAX_HISTORY = 200;
    private static final int MAX_URIS = 400;

    static final class Entry {
        long time;
        boolean down;      // true: vom PC geladen, false: an den PC gesendet
        String name;
        long size;
        boolean ok;
        String note;       // Fehlergrund oder leer
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("store", Context.MODE_PRIVATE);
    }

    static synchronized void addHistory(Context c, boolean down, String name, long size, boolean ok, String note) {
        try {
            JSONArray all = new JSONArray(prefs(c).getString("history", "[]"));
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("d", down);
            o.put("n", name);
            o.put("s", size);
            o.put("ok", ok);
            o.put("m", note == null ? "" : note);
            JSONArray out = new JSONArray();
            int start = Math.max(0, all.length() - (MAX_HISTORY - 1));
            for (int i = start; i < all.length(); i++) {
                out.put(all.get(i));
            }
            out.put(o);
            prefs(c).edit().putString("history", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Neueste zuerst. */
    static synchronized List<Entry> history(Context c) {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(prefs(c).getString("history", "[]"));
            for (int i = all.length() - 1; i >= 0; i--) {
                JSONObject o = all.getJSONObject(i);
                Entry e = new Entry();
                e.time = o.getLong("t");
                e.down = o.getBoolean("d");
                e.name = o.getString("n");
                e.size = o.getLong("s");
                e.ok = o.getBoolean("ok");
                e.note = o.optString("m", "");
                list.add(e);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    static synchronized void clearHistory(Context c) {
        prefs(c).edit().remove("history").apply();
    }

    static synchronized void setUri(Context c, String key, String uri) {
        try {
            JSONArray all = new JSONArray(prefs(c).getString("uris", "[]"));
            JSONArray out = new JSONArray();
            int start = Math.max(0, all.length() - (MAX_URIS - 1));
            for (int i = start; i < all.length(); i++) {
                JSONArray pair = all.getJSONArray(i);
                if (!pair.getString(0).equals(key)) {
                    out.put(pair);
                }
            }
            out.put(new JSONArray().put(key).put(uri));
            prefs(c).edit().putString("uris", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized String uri(Context c, String key) {
        try {
            JSONArray all = new JSONArray(prefs(c).getString("uris", "[]"));
            for (int i = all.length() - 1; i >= 0; i--) {
                JSONArray pair = all.getJSONArray(i);
                if (pair.getString(0).equals(key)) {
                    return pair.getString(1);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
