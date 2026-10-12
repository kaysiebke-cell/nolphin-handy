package org.nolphin.handy;

import java.util.ArrayList;
import java.util.List;

/** Listen von Ordnerpfaden für Favoriten und zuletzt geöffnete Orte (ohne Android-Abhängigkeit, damit sie sich testen lassen). */
final class PlaceLists {
    private PlaceLists() {}

    static final int MAX_RECENT = 8;

    /** Liest eine gespeicherte Liste (ein Pfad pro Zeile); leere Zeilen und Doppelte entfallen. */
    static List<String> parse(String stored) {
        List<String> out = new ArrayList<>();
        if (stored == null) {
            return out;
        }
        for (String line : stored.split("\n")) {
            String p = line.trim();
            if (!p.isEmpty() && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(s);
        }
        return b.toString();
    }

    /** Setzt einen Pfad an den Anfang; war er schon in der Liste, rückt er nur nach vorn. Die Liste bleibt auf max Einträge begrenzt. */
    static List<String> pushFront(List<String> list, String path, int max) {
        List<String> out = new ArrayList<>();
        out.add(path);
        for (String s : list) {
            if (!s.equals(path) && out.size() < max) {
                out.add(s);
            }
        }
        return out;
    }

    /** Fügt einen Pfad hinten an, falls er noch fehlt (Favoriten behalten die Reihenfolge, in der man sie anlegt). */
    static List<String> add(List<String> list, String path) {
        List<String> out = new ArrayList<>(list);
        if (!out.contains(path)) {
            out.add(path);
        }
        return out;
    }

    static List<String> remove(List<String> list, String path) {
        List<String> out = new ArrayList<>(list);
        out.remove(path);
        return out;
    }
}
