package org.nolphin.handy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Regeln für „Anzeige nach Art“ und „Galerie verstecken“ (ohne Android, damit sie sich testen lassen):
 * welche obersten Ordner eine Kategorie zeigt, die SQL-Bedingung dafür und wann ein vom PC geladener Ordner
 * aus der Galerie des Handys verschwinden darf.
 */
final class CatFilter {
    private CatFilter() {}

    /** Der oberste Ordner unterhalb der Speicherwurzel ("/storage/emulated/0/DCIM/x.jpg" → "DCIM") oder null. */
    static String top(List<String> roots, String path) {
        if (path == null) {
            return null;
        }
        for (String root : roots) {
            String prefix = root.endsWith("/") ? root : root + "/";
            if (path.startsWith(prefix)) {
                String rest = path.substring(prefix.length());
                int slash = rest.indexOf('/');
                return slash > 0 ? rest.substring(0, slash) : null;   // Dateien direkt in der Wurzel haben keinen Ordner
            }
        }
        return null;
    }

    /** Bedingung für die Medienabfrage: nur Dateien aus diesen obersten Ordnern; leer, wenn alle Ordner gelten. */
    static String where(String dataColumn, List<String> roots, Collection<String> folders) {
        if (folders == null || folders.isEmpty() || roots.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (String root : roots) {
            String prefix = root.endsWith("/") ? root : root + "/";
            for (String f : folders) {
                if (!FolderFetch.safeName(f)) {
                    continue;
                }
                b.append(b.length() == 0 ? "" : " OR ").append(dataColumn).append(" LIKE '")
                        .append(like(prefix + f + "/")).append("%' ESCAPE '\\'");
            }
        }
        return b.length() == 0 ? "" : "(" + b + ")";
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

    /** Zeile für die Einstellungen: „Alle Ordner“ oder die gewählten Namen. */
    static String summary(Collection<String> folders) {
        if (folders == null || folders.isEmpty()) {
            return "Alle Ordner";
        }
        List<String> sorted = new ArrayList<>(folders);
        java.util.Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        StringBuilder b = new StringBuilder();
        for (String s : sorted) {
            b.append(b.length() == 0 ? "" : ", ").append(s);
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
