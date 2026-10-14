package org.nolphin.handy;

import java.util.ArrayList;
import java.util.List;

/**
 * Zwischenablage für „Vom PC kopieren, im Handy-Ordner einfügen“: merkt Dateien und Ordner am PC, bis sie eingefügt
 * oder verworfen werden. Reines Java, ohne Android-Klassen.
 */
final class PcClip {
    private PcClip() {}

    /** Ein Ordner am PC: Pfad und Name. */
    static final class Dir {
        final String path;
        final String name;

        Dir(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private static final List<RemoteFile> files = new ArrayList<>();
    private static final List<Dir> dirs = new ArrayList<>();

    static synchronized void set(List<RemoteFile> newFiles, List<Dir> newDirs) {
        files.clear();
        dirs.clear();
        files.addAll(newFiles);
        for (Dir d : newDirs) {
            if (FolderFetch.safeName(d.name)) {
                dirs.add(d);
            }
        }
    }

    static synchronized void clear() {
        files.clear();
        dirs.clear();
    }

    static synchronized boolean isEmpty() {
        return files.isEmpty() && dirs.isEmpty();
    }

    static synchronized int count() {
        return files.size() + dirs.size();
    }

    static synchronized List<RemoteFile> files() {
        return new ArrayList<>(files);
    }

    static synchronized List<Dir> dirs() {
        return new ArrayList<>(dirs);
    }

    /** Text für die Leiste im Handy-Ordner, zum Beispiel „3 Einträge vom PC einfügen“. */
    static synchronized String label() {
        int n = count();
        return n + (n == 1 ? " Eintrag" : " Einträge") + " vom PC einfügen";
    }
}
