package org.nolphin.handy;

/** Eine Datei im Ordner „Handy“ am PC oder in einem seiner Unterordner (dir, leer = oberste Ebene). */
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
}
