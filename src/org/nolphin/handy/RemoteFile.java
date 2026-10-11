package org.nolphin.handy;

/** Eine Datei im Ordner „Handy“ am PC (Name und Größe aus /wait). */
final class RemoteFile {
    final String name;
    final long size;

    RemoteFile(String name, long size) {
        this.name = name;
        this.size = size;
    }

    String key() {
        return name + "|" + size;
    }
}
