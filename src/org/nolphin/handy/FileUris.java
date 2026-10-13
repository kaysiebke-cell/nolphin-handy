package org.nolphin.handy;

import java.util.List;

/** Reine Regeln für das Öffnen von Dateien in anderen Apps (ohne Android, damit sie sich testen lassen). */
final class FileUris {
    private FileUris() {}

    /**
     * Darf diese Datei (kanonischer Pfad) über {@link FilesProvider} herausgegeben werden? Nur unterhalb der
     * Speicher-Wurzeln und nie aus den privaten App-Ordnern anderer Apps.
     */
    static boolean isAllowed(String canonical, List<String> roots) {
        if (canonical == null || canonical.isEmpty() || !canonical.startsWith("/")) {
            return false;
        }
        String p = canonical + "/";
        if (p.contains("/Android/data/") || p.contains("/Android/obb/")) {
            return false;
        }
        for (String r : roots) {
            if (canonical.startsWith(r + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Was nach der Rückkehr aus einer anderen App mit der Datei geschehen ist.
     *
     * @return null, wenn die Datei nur angesehen wurde und unverändert ist; sonst ein kurzer Hinweis
     */
    static String afterReturn(boolean edit, boolean exists, long sizeBefore, long modBefore, long sizeAfter, long modAfter) {
        if (!exists) {
            return "Die Datei liegt nicht mehr am alten Ort.";
        }
        boolean changed = sizeBefore != sizeAfter || modBefore != modAfter;
        if (changed) {
            return "Die Datei wurde geändert.";
        }
        if (edit) {
            return "Die Originaldatei ist unverändert. Manche Apps speichern eine Kopie an anderer Stelle.";
        }
        return null;
    }
}
