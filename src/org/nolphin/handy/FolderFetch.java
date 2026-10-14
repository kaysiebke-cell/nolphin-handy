package org.nolphin.handy;

import java.util.ArrayList;
import java.util.List;

/** Regeln fuers Laden ganzer Ordner vom PC (ohne Android, damit sie sich testen lassen). */
final class FolderFetch {
    private FolderFetch() {}

    static final int MAX_DEPTH = 12;
    static final int MAX_FILES = 5000;

    /** Ein einzelner Name vom PC: nie leer, nie "." oder "..", kein Schraegstrich, kein Null-Zeichen. */
    static boolean safeName(String n) {
        return n != null && !n.isEmpty() && !n.equals(".") && !n.equals("..") && n.indexOf('/') < 0
                && n.indexOf('\\') < 0 && n.indexOf('\0') < 0;
    }

    /** Pfad innerhalb des geladenen Ordners ("Ordner/Unter"): jeder Teil muss ein sicherer Name sein. */
    static boolean safeRelative(String rel) {
        if (rel == null) {
            return false;
        }
        if (rel.isEmpty()) {
            return true;
        }
        for (String part : rel.split("/", -1)) {
            if (!safeName(part)) {
                return false;
            }
        }
        return true;
    }

    /** Pfad am PC fuer einen Unterordner: Wurzel und Name zusammensetzen ("" + "A" = "A", "A" + "B" = "A/B", "~" + "B" = "~/B"). */
    static String child(String parent, String name) {
        return parent.isEmpty() ? name : parent + "/" + name;
    }

    /** Ob unter den Auswahlschluesseln Ordner sind (Schluessel beginnen mit "d:"). */
    static boolean isDirKey(String key) {
        return key != null && key.startsWith("d:");
    }

    static String dirKey(String path) {
        return "d:" + path;
    }

    static String pathOf(String key) {
        return key.substring(2);
    }

    static int countDirs(Iterable<String> keys) {
        int n = 0;
        for (String k : keys) {
            if (isDirKey(k)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Welche Auswahl nach dem Neuladen bleibt: Dateien nur, wenn es sie noch gibt, Ordner nur, wenn sie noch in der Liste stehen.
     * (Früher blieben nur Dateien, dadurch verschwand jeder angehakte Ordner sofort wieder.)
     */
    static java.util.Set<String> keep(java.util.Collection<String> selected, java.util.Collection<String> fileKeys,
                                      java.util.Collection<String> dirKeys) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String k : selected) {
            if (isDirKey(k) ? dirKeys.contains(k) : fileKeys.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /** Zusammenfassung fuers Protokoll und die Meldung. */
    static String summary(int ok, int failed, boolean cancelled) {
        if (cancelled) {
            return "abgebrochen nach " + ok + (ok == 1 ? " Datei" : " Dateien");
        }
        if (failed == 0) {
            return ok + (ok == 1 ? " Datei" : " Dateien");
        }
        return ok + " geladen, " + failed + " fehlgeschlagen";
    }

    /** Zielpfad unterhalb des Download-Ordners: "Download/Nolphin/Ordner/Unter". Nur bei sicheren Namen. */
    static String targetPath(String root, String folderName, String rel) {
        if (!safeName(folderName) || !safeRelative(rel)) {
            return null;
        }
        return root + "/" + folderName + (rel.isEmpty() ? "" : "/" + rel);
    }

    /** Aufgabe: Datei (Ordner am PC, Name, Groesse) mit ihrem Zielunterordner. */
    static final class Item {
        final String pcDir;
        final String name;
        final long size;
        final String rel;

        Item(String pcDir, String name, long size, String rel) {
            this.pcDir = pcDir;
            this.name = name;
            this.size = size;
            this.rel = rel;
        }
    }

    static List<Item> none() {
        return new ArrayList<>();
    }
}
