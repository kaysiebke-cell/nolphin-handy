package org.nolphin.handy;

import java.util.Collection;
import java.util.List;

/**
 * Regeln für „Ordner ausblenden“ und „Galerie verstecken“ (ohne Android, damit sie sich testen lassen): die
 * SQL-Bedingung für ausgeblendete Ordner, die Prüfung eines eingetippten Namens und wann ein vom PC geladener
 * Ordner aus der Galerie des Handys verschwinden darf.
 */
final class CatFilter {
    private CatFilter() {}

    /** Bedingung für die Medienabfrage: nichts aus diesen Ordnern (auch tiefer liegenden wie "Pictures/Bearbeitet"); leer, wenn nichts ausgeschlossen ist. */
    static String whereNot(String dataColumn, List<String> roots, Collection<String> folders) {
        if (folders == null || folders.isEmpty() || roots.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (String root : roots) {
            String prefix = root.endsWith("/") ? root : root + "/";
            for (String f : folders) {
                if (f.isEmpty() || !FolderFetch.safeRelative(f)) {
                    continue;
                }
                b.append(b.length() == 0 ? "" : " AND ").append(dataColumn).append(" NOT LIKE '")
                        .append(like(prefix + f + "/")).append("%' ESCAPE '\\'");
            }
        }
        return b.length() == 0 ? "" : "(" + b + ")";
    }

    /** Eingetippter Ordnername: Leerzeichen und Schrägstriche am Rand weg; null, wenn er unsicher oder leer ist ("..", doppelte Schrägstriche). */
    static String clean(String typed) {
        if (typed == null) {
            return null;
        }
        String s = typed.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return !s.isEmpty() && FolderFetch.safeRelative(s) ? s : null;
    }

    /** Text für eine LIKE-Bedingung in einfachen Anführungszeichen: Anführungszeichen verdoppeln, % _ und \ entwerten. */
    static String like(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                b.append("''");
            } else if (ch == '%' || ch == '_' || ch == '\\') {
                b.append('\\').append(ch);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    /**
     * Darf ein vom PC geladener Ordner aus der Galerie verschwinden? Nicht, wenn man ihn selbst in die Fotoordner
     * (DCIM, Pictures) eingefügt hat: dort will man ihn in der Galerie sehen.
     */
    static boolean mayHide(String targetPath) {
        if (targetPath == null) {
            return true;
        }
        for (String part : targetPath.split("/")) {
            if (part.equalsIgnoreCase("DCIM") || part.equalsIgnoreCase("Pictures")) {
                return false;
            }
        }
        return true;
    }
}
