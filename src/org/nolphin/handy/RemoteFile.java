package org.nolphin.handy;

import android.net.Uri;

/**
 * Eine Datei am PC: im Ordner „Handy“ oder einem seiner Unterordner (dir, leer = oberste Ebene) oder im
 * persönlichen Ordner (dir beginnt mit "~", zum Beispiel "~/Bilder").
 */
final class RemoteFile {
    final String dir;
    final String name;
    final long size;

    RemoteFile(String name, long size) {
        this("", name, size);
    }

    RemoteFile(String dir, String name, long size) {
        this.dir = dir;
        this.name = name;
        this.size = size;
    }

    String key() {
        return (dir.isEmpty() ? "" : dir + "/") + name + "|" + size;
    }

    /** Anfrage-Parameter für /dir und /dl: Wurzel (r=home beim persönlichen Ordner) und Unterordner (p). */
    static String params(String dir) {
        boolean home = dir.equals("~") || dir.startsWith("~/");
        String p = home ? (dir.length() > 2 ? dir.substring(2) : "") : dir;
        return (home ? "&r=home" : "") + (p.isEmpty() ? "" : "&p=" + Uri.encode(p));
    }
}
