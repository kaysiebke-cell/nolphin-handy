package org.nolphin.handy;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speicheranalyse (nur Java, ohne Android): zählt, was auf einem Speicher liegt, nach Gruppen, größten Ordnern und
 * größten Dateien. Liest nur, ändert nichts, folgt keinen Verknüpfungen (Symlinks) und lässt sich abbrechen.
 */
final class StorageScan {
    private StorageScan() {}

    static final String[] CATS = { "Bilder", "Videos", "Audio", "Dokumente", "Apps", "Sonstiges" };
    static final int PICTURES = 0, VIDEOS = 1, AUDIO = 2, DOCUMENTS = 3, APPS = 4, OTHER = 5;

    static final int TOP_FOLDERS = 6;
    static final int TOP_FILES = 5;

    interface Progress {
        void update(long files, long bytes);
    }

    /** Ein Ordner oder eine Datei mit Größe. */
    static final class Item {
        final File file;
        final long bytes;

        Item(File file, long bytes) {
            this.file = file;
            this.bytes = bytes;
        }
    }

    static final class Result {
        long files;
        long folders;
        long bytes;
        /** Dateien und Ordner, die nicht gelesen werden durften. */
        long unreadable;
        final long[] byCat = new long[CATS.length];
        final List<Item> topFolders = new ArrayList<>();
        final List<Item> topFiles = new ArrayList<>();
        boolean cancelled;
        long finishedAt;
    }

    /** Die Gruppe einer Datei; alles unter dem obersten Ordner "Android" gehört zu "Apps". */
    static int category(String name, boolean inAppData) {
        if (inAppData) {
            return APPS;
        }
        int dot = name.lastIndexOf('.');
        String e = dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (e) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "heic": case "heif": case "bmp": case "svg":
            case "raw": case "dng": case "tif": case "tiff":
                return PICTURES;
            case "mp4": case "mkv": case "webm": case "3gp": case "mov": case "avi": case "m4v": case "mpg": case "mpeg":
                return VIDEOS;
            case "mp3": case "m4a": case "ogg": case "oga": case "opus": case "flac": case "wav": case "aac": case "wma": case "amr":
                return AUDIO;
            case "pdf": case "doc": case "docx": case "odt": case "rtf": case "txt": case "md": case "xls": case "xlsx":
            case "ods": case "csv": case "ppt": case "pptx": case "odp": case "epub":
                return DOCUMENTS;
            case "apk": case "xapk": case "apks": case "aab":
                return APPS;
            default:
                return OTHER;
        }
    }

    private static boolean isLink(File f) {
        return Files.isSymbolicLink(f.toPath());
    }

    /**
     * Zählt alles unter root. Die größten Ordner sind die direkten Unterordner von root; die größten Dateien kommen
     * aus dem ganzen Baum. Bei Abbruch ist das Ergebnis unvollständig und {@code cancelled} gesetzt.
     */
    static Result scan(File root, Progress progress, AtomicBoolean cancel) {
        Result r = new Result();
        PriorityQueue<Item> files = new PriorityQueue<>(TOP_FILES + 1, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(a.bytes, b.bytes);
            }
        });
        File[] top = root.listFiles();
        if (top == null) {
            r.unreadable++;
            r.finishedAt = System.currentTimeMillis();
            return r;
        }
        long tick = 0;
        for (File child : top) {
            if (cancel != null && cancel.get()) {
                r.cancelled = true;
                break;
            }
            if (isLink(child)) {
                continue;
            }
            boolean appData = child.isDirectory() && child.getName().equals("Android");
            long sizeOfChild = 0;
            if (child.isFile()) {
                long len = child.length();
                sizeOfChild = len;
                r.files++;
                r.bytes += len;
                r.byCat[category(child.getName(), false)] += len;
                offer(files, child, len);
                continue;
            }
            if (!child.isDirectory()) {
                continue;
            }
            r.folders++;
            ArrayDeque<File> stack = new ArrayDeque<>();
            stack.push(child);
            while (!stack.isEmpty()) {
                if (cancel != null && cancel.get()) {
                    r.cancelled = true;
                    break;
                }
                if (r.cancelled) {
                    break;
                }
                File d = stack.pop();
                File[] list = d.listFiles();
                if (list == null) {
                    r.unreadable++;
                    continue;
                }
                for (File f : list) {
                    if (isLink(f)) {
                        continue;
                    }
                    if (f.isDirectory()) {
                        r.folders++;
                        stack.push(f);
                    } else if (f.isFile()) {
                        long len = f.length();
                        sizeOfChild += len;
                        r.files++;
                        r.bytes += len;
                        r.byCat[category(f.getName(), appData)] += len;
                        offer(files, f, len);
                        if ((++tick & 255) == 0) {
                            if (progress != null) {
                                progress.update(r.files, r.bytes);
                            }
                            if (cancel != null && cancel.get()) {
                                r.cancelled = true;
                                break;
                            }
                        }
                    }
                }
            }
            r.topFolders.add(new Item(child, sizeOfChild));
            if (r.cancelled) {
                break;
            }
        }
        Collections.sort(r.topFolders, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.bytes, a.bytes);
            }
        });
        while (r.topFolders.size() > TOP_FOLDERS) {
            r.topFolders.remove(r.topFolders.size() - 1);
        }
        r.topFiles.addAll(files);
        Collections.sort(r.topFiles, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.bytes, a.bytes);
            }
        });
        if (progress != null) {
            progress.update(r.files, r.bytes);
        }
        r.finishedAt = System.currentTimeMillis();
        return r;
    }

    private static void offer(PriorityQueue<Item> q, File f, long len) {
        if (q.size() < TOP_FILES) {
            q.add(new Item(f, len));
        } else if (len > q.peek().bytes) {
            q.poll();
            q.add(new Item(f, len));
        }
    }
}
