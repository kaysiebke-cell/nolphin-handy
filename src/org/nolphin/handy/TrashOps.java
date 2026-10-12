package org.nolphin.handy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Papierkorb (nur Java, ohne Android). Jeder Speicher hat seinen eigenen Papierkorb im Ordner
 * {@code .nolphin-papierkorb} auf dem Speicher selbst. Gelöscht wird dorthin <b>nur durch Umbenennen</b>
 * (kein Kopieren, kein Löschen), und jedes Element liegt in einem eigenen Unterordner mit einer Infodatei
 * (alter Pfad, Zeitpunkt). Geht das Umbenennen nicht, bleibt das Original unberührt, es wird nie ersatzweise gelöscht.
 */
final class TrashOps {
    private TrashOps() {}

    static final String DIR_NAME = ".nolphin-papierkorb";
    static final long MAX_AGE_MS = 30L * 24 * 3600 * 1000;

    /** Ein Element im Papierkorb. */
    static final class Entry {
        /** Unterordner mit dem abgelegten Element. */
        final File box;
        /** Infodatei (kann fehlen, wenn ein Absturz dazwischenkam). */
        final File info;
        /** Das abgelegte Element selbst. */
        final File stored;
        final String name;
        /** Der Ort, an dem es vorher lag. */
        final String origPath;
        final long when;
        final boolean dir;

        Entry(File box, File info, File stored, String name, String origPath, long when) {
            this.box = box;
            this.info = info;
            this.stored = stored;
            this.name = name;
            this.origPath = origPath;
            this.when = when;
            this.dir = !Files.isSymbolicLink(stored.toPath()) && stored.isDirectory();
        }

        File originalParent() {
            return new File(origPath).getParentFile();
        }
    }

    static File trashDir(File volumeRoot) {
        return new File(volumeRoot, DIR_NAME);
    }

    /** Der Speicher (längster passender Anfang), auf dem die Datei liegt, oder null. */
    static File rootOf(File f, List<File> roots) {
        File best = null;
        String p = f.getPath();
        for (File r : roots) {
            String rp = r.getPath();
            if ((p.equals(rp) || p.startsWith(rp + "/")) && (best == null || rp.length() > best.getPath().length())) {
                best = r;
            }
        }
        return best;
    }

    static boolean isInTrash(File f, List<File> roots) {
        File root = rootOf(f, roots);
        File p = f.getParentFile();
        if (root == null || p == null) {
            return false;
        }
        // Der Ort zählt, nicht das Ziel einer Verknüpfung: eine Verknüpfung auf den Papierkorb darf selbst gelöscht werden.
        return FileOps.isInside(trashDir(root), p) || (f.getName().equals(DIR_NAME) && p.getPath().equals(root.getPath()));
    }

    /** Ob alle Elemente in den Papierkorb gelegt werden können (auf einem bekannten Speicher, nicht schon darin). */
    static boolean canTrashAll(List<File> items, List<File> roots) {
        for (File f : items) {
            File root = rootOf(f, roots);
            if (root == null || f.equals(root) || isInTrash(f, roots)) {
                return false;
            }
        }
        return !items.isEmpty();
    }

    private static boolean exists(File f) {
        return Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static void saveInfo(File info, String path, long when) throws IOException {
        Properties p = new Properties();
        p.setProperty("path", path);
        p.setProperty("when", Long.toString(when));
        try (OutputStream o = new FileOutputStream(info)) {
            p.store(o, null);
        }
    }

    private static int counter;

    private static synchronized String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + "-" + Integer.toString(++counter, 36)
                + "-" + Integer.toString((int) (System.nanoTime() & 0xffff), 36);
    }

    // ---- In den Papierkorb legen ---------------------------------------------------------------

    static FileOps.Result moveToTrash(List<File> items, List<File> roots, FileOps.Progress progress) {
        FileOps.Result r = new FileOps.Result();
        int index = 0;
        for (File f : items) {
            if (progress != null && progress.cancelled()) {
                r.cancelled = true;
                break;
            }
            index++;
            File root = rootOf(f, roots);
            if (root == null || f.equals(root)) {
                r.failed.add(f.getName() + ": liegt nicht auf einem bekannten Speicher");
                r.retry.add(f);
                continue;
            }
            if (isInTrash(f, roots)) {
                r.failed.add(f.getName() + ": ist schon im Papierkorb");
                continue;
            }
            if (!exists(f)) {
                r.failed.add(f.getName() + ": gibt es nicht mehr");
                continue;
            }
            File trash = trashDir(root);
            File box = new File(trash, newId());
            File dst = new File(box, f.getName());
            File info = new File(trash, box.getName() + ".info");
            try {
                if (!trash.isDirectory() && !trash.mkdirs()) {
                    throw new IOException("Papierkorb lässt sich nicht anlegen");
                }
                File nomedia = new File(trash, ".nomedia");
                if (!nomedia.exists()) {
                    nomedia.createNewFile();
                }
                if (!box.mkdir()) {
                    throw new IOException("Fach im Papierkorb lässt sich nicht anlegen");
                }
                try {
                    Files.move(f.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    box.delete();
                    throw new IOException("liegt auf einem anderen Laufwerk als der Papierkorb");
                }
                try {
                    saveInfo(info, f.getPath(), System.currentTimeMillis());
                } catch (IOException e) {
                    // ohne Infodatei lässt sich nichts zurückholen: lieber alles zurücknehmen
                    Files.move(dst.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    box.delete();
                    info.delete();
                    throw e;
                }
                r.done.add(f.getName());
                r.touch(f);
            } catch (IOException | RuntimeException e) {
                r.failed.add(f.getName() + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                r.retry.add(f);
                box.delete();
            }
            if (progress != null) {
                progress.update(f.getName(), index, items.size(), index, items.size());
            }
        }
        return r;
    }

    // ---- Auflisten ---------------------------------------------------------------------------

    /** Alle Elemente in den Papierkörben der Speicher, neueste zuerst. */
    static List<Entry> list(List<File> roots) {
        List<Entry> out = new ArrayList<>();
        for (File root : roots) {
            File trash = trashDir(root);
            File[] boxes = trash.listFiles();
            if (boxes == null) {
                continue;
            }
            for (File box : boxes) {
                if (!box.isDirectory() || Files.isSymbolicLink(box.toPath())) {
                    continue;
                }
                File[] inside = box.listFiles();
                if (inside == null || inside.length != 1) {
                    if (inside != null && inside.length == 0) {
                        box.delete();     // leeres Fach (Rest eines Abbruchs)
                        new File(trash, box.getName() + ".info").delete();
                    }
                    continue;
                }
                File stored = inside[0];
                File info = new File(trash, box.getName() + ".info");
                String path = new File(root, stored.getName()).getPath();
                long when = box.lastModified();
                if (info.isFile()) {
                    Properties p = new Properties();
                    try (InputStream in = new FileInputStream(info)) {
                        p.load(in);
                        String sp = p.getProperty("path");
                        if (sp != null && !sp.isEmpty()) {
                            path = sp;
                        }
                        when = Long.parseLong(p.getProperty("when", Long.toString(when)));
                    } catch (IOException | RuntimeException ignored) {
                    }
                }
                out.add(new Entry(box, info, stored, stored.getName(), path, when));
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.when, a.when);
            }
        });
        return out;
    }

    // ---- Zurückholen und endgültig löschen ----------------------------------------------------

    /** Holt Elemente an ihren alten Ort zurück. Gibt es dort schon etwas Gleichnamiges, bekommt das Zurückgeholte "(2)". */
    static FileOps.Result restore(List<Entry> entries, FileOps.Progress progress) {
        FileOps.Result r = new FileOps.Result();
        int index = 0;
        for (Entry e : entries) {
            if (progress != null && progress.cancelled()) {
                r.cancelled = true;
                break;
            }
            index++;
            File parent = e.originalParent();
            try {
                if (parent == null) {
                    throw new IOException("alter Ort unbekannt");
                }
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("alter Ordner lässt sich nicht anlegen");
                }
                File target = new File(parent, e.name);
                if (exists(target)) {
                    target = FileOps.uniqueIn(parent, e.name);
                }
                try {
                    Files.move(e.stored.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    throw new IOException("alter Ort liegt auf einem anderen Laufwerk");
                }
                e.box.delete();
                e.info.delete();
                r.done.add(e.name);
                r.touch(target);
            } catch (IOException | RuntimeException ex) {
                r.failed.add(e.name + ": " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
            if (progress != null) {
                progress.update(e.name, index, entries.size(), index, entries.size());
            }
        }
        return r;
    }

    /** Löscht Elemente aus dem Papierkorb endgültig. */
    static FileOps.Result deleteForever(List<Entry> entries, FileOps.Progress progress) {
        List<File> files = new ArrayList<>();
        for (Entry e : entries) {
            files.add(e.stored);
        }
        FileOps.Result r = FileOps.deleteAll(files, progress);
        for (Entry e : entries) {
            if (!exists(e.stored)) {
                e.box.delete();
                e.info.delete();
            }
        }
        return r;
    }

    /** Löscht alles, was älter als {@code maxAgeMs} ist. Gibt die Anzahl gelöschter Elemente zurück. */
    static int purge(List<File> roots, long maxAgeMs, long now) {
        List<Entry> old = new ArrayList<>();
        for (Entry e : list(roots)) {
            if (now - e.when > maxAgeMs) {
                old.add(e);
            }
        }
        if (old.isEmpty()) {
            return 0;
        }
        return deleteForever(old, null).done.size();
    }

    /** Anzahl Elemente und Gesamtgröße in Bytes. */
    static long[] summary(List<File> roots) {
        long[] s = new long[2];
        for (Entry e : list(roots)) {
            s[0]++;
            s[1] += FileOps.measure(e.stored)[0];
        }
        return s;
    }

    /** "vor 3 Tagen" und ähnlich. */
    static String age(long when, long now) {
        long min = Math.max(0, (now - when) / 60000);
        if (min < 1) {
            return "gerade eben";
        }
        if (min < 60) {
            return "vor " + min + (min == 1 ? " Minute" : " Minuten");
        }
        long h = min / 60;
        if (h < 24) {
            return "vor " + h + (h == 1 ? " Stunde" : " Stunden");
        }
        long d = h / 24;
        return "vor " + d + (d == 1 ? " Tag" : " Tagen");
    }
}
