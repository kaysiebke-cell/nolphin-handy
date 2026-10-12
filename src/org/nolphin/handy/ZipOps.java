package org.nolphin.handy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * ZIP-Archive prüfen, sicher entpacken und packen (nur Java, keine Bibliotheken).
 *
 * Beim Entpacken gilt: Kein Eintrag darf außerhalb des Zielordners landen (kein "..", kein absoluter Pfad, kein
 * Laufwerksbuchstabe, kein Weg über eine Verknüpfung). Geschrieben wird erst in eine Temporärdatei; nach Fehler oder
 * Abbruch bleibt nichts Halbes zurück. Der Speicherplatz wird vorher geprüft, und es wird nie mehr geschrieben, als
 * das Archiv angibt (Schutz vor Archiven, die viel größer entpacken als angegeben).
 */
final class ZipOps {
    private ZipOps() {}

    /** Was in einem Archiv steckt (aus dem Inhaltsverzeichnis, ohne zu entpacken). */
    static final class Info {
        int files;
        int folders;
        long compressed;
        long uncompressed;
        /** Einträge, die nicht entpackt würden, mit Grund ("../x: Pfad führt aus dem Ordner heraus"). */
        final List<String> unsafe = new ArrayList<>();
        /** Die ersten Einträge für die Ansicht "Inhalt ansehen" (höchstens 300). */
        final List<String> names = new ArrayList<>();
        /** Ein gemeinsamer oberster Ordner aller sicheren Einträge (ohne Schrägstrich), sonst null. */
        String root;
        private boolean rootOpen = true;
    }

    static Info inspect(File zip) throws IOException {
        Info info = new Info();
        try (ZipFile z = new ZipFile(zip, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String why = unsafeReason(e.getName());
                if (why != null) {
                    info.unsafe.add(e.getName() + ": " + why);
                    continue;
                }
                String top = e.getName().replace('\\', '/');
                int slash = top.indexOf('/');
                boolean nested = slash > 0 && (slash < top.length() - 1 || e.isDirectory());
                String first = nested ? top.substring(0, slash) : null;
                if (info.rootOpen) {
                    if (first == null || (info.root != null && !info.root.equals(first))) {
                        info.root = null;
                        info.rootOpen = false;
                    } else {
                        info.root = first;
                    }
                }
                if (e.isDirectory()) {
                    info.folders++;
                } else {
                    info.files++;
                    info.compressed += Math.max(0, e.getCompressedSize());
                    info.uncompressed += Math.max(0, e.getSize());
                }
                if (info.names.size() < 300) {
                    info.names.add(e.getName() + (e.isDirectory() ? "" : "  (" + sizeText(Math.max(0, e.getSize())) + ")"));
                }
            }
        }
        return info;
    }

    /** Größe als Text (ohne Android, damit die Klasse am PC getestet werden kann). */
    static String sizeText(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024L * 1024) {
            return (b / 1024) + " KB";
        }
        if (b < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.GERMANY, "%.1f MB", b / 1048576.0);
        }
        return String.format(java.util.Locale.GERMANY, "%.2f GB", b / 1073741824.0);
    }

    /** Warum ein Eintragsname nicht entpackt wird, oder null, wenn er sicher ist. */
    static String unsafeReason(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "leerer Name";
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/")) {
            return "absoluter Pfad";
        }
        if (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0))) {
            return "Laufwerksangabe";
        }
        if (name.indexOf('\0') >= 0) {
            return "ungültiges Zeichen";
        }
        for (String part : name.split("/")) {
            if (part.equals("..")) {
                return "Pfad führt aus dem Ordner heraus";
            }
        }
        return null;
    }

    private static boolean noLinkOnPath(File root, File target) {
        // Jeder schon vorhandene Ordner zwischen Wurzel und Ziel muss ein echter Ordner sein (keine Verknüpfung)
        File cur = target.getParentFile();
        while (cur != null && !cur.equals(root)) {
            if (Files.isSymbolicLink(cur.toPath())) {
                return false;
            }
            cur = cur.getParentFile();
        }
        return !Files.isSymbolicLink(root.toPath());
    }

    /**
     * Entpackt in {@code destRoot} (der Ordner wird bei Bedarf angelegt).
     *
     * @param conflict was bei vorhandenen Dateien geschieht (umbenennen, überspringen, ersetzen)
     * @param maxBytes höchstens so viele Bytes werden geschrieben (zum Beispiel der freie Speicherplatz)
     */
    static FileOps.Result unzip(File zip, File destRoot, FileOps.Conflict conflict, long maxBytes, FileOps.Progress progress) {
        FileOps.Result r = new FileOps.Result();
        FileOps.Progress p = progress != null ? progress : new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return false;
            }

            @Override
            public void update(String name, long done, long total, int fileIndex, int fileCount) {
            }
        };
        FileOps.Conflict policy = conflict == null ? FileOps.Conflict.RENAME : conflict;
        try (ZipFile z = new ZipFile(zip, StandardCharsets.UTF_8)) {
            long total = 0;
            int count = 0;
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && unsafeReason(e.getName()) == null) {
                    total += Math.max(0, e.getSize());
                    count++;
                }
            }
            if (!destRoot.isDirectory() && !destRoot.mkdirs()) {
                r.failed.add(destRoot.getName() + ": Zielordner nicht anlegbar");
                return r;
            }
            String rootPath = destRoot.getCanonicalPath();
            byte[] buf = new byte[64 * 1024];
            long written = 0;
            int index = 0;
            en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (p.cancelled()) {
                    r.cancelled = true;
                    return r;
                }
                String why = unsafeReason(e.getName());
                if (why != null) {
                    r.failed.add(e.getName() + ": " + why);
                    continue;
                }
                String name = e.getName().replace('\\', '/');
                File target = new File(destRoot, name);
                String canonical = target.getCanonicalPath();
                if (!canonical.equals(rootPath) && !canonical.startsWith(rootPath + File.separator)) {
                    r.failed.add(e.getName() + ": Pfad führt aus dem Ordner heraus");
                    continue;
                }
                if (!noLinkOnPath(destRoot, target)) {
                    r.failed.add(e.getName() + ": Pfad führt über eine Verknüpfung");
                    continue;
                }
                if (e.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        r.failed.add(e.getName() + ": Ordner nicht anlegbar");
                    }
                    continue;
                }
                index++;
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    r.failed.add(e.getName() + ": Ordner nicht anlegbar");
                    continue;
                }
                if (Files.exists(target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    if (policy == FileOps.Conflict.SKIP) {
                        r.skipped.add(e.getName() + ": gibt es schon");
                        continue;
                    }
                    if (policy == FileOps.Conflict.RENAME) {
                        target = FileOps.uniqueIn(parent, target.getName());
                    } else if (target.isDirectory()) {
                        r.failed.add(e.getName() + ": im Ziel gibt es einen Ordner mit diesem Namen");
                        continue;
                    }
                }
                File tmp = null;
                try {
                    tmp = File.createTempFile(".nolphin-", ".tmp", parent);
                    long thisSize = e.getSize();
                    long wroteHere = 0;
                    try (InputStream in = z.getInputStream(e); OutputStream out = new FileOutputStream(tmp)) {
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (p.cancelled()) {
                                r.cancelled = true;
                                out.close();
                                tmp.delete();
                                return r;
                            }
                            wroteHere += n;
                            written += n;
                            if (written > maxBytes) {
                                throw new IOException("kein Speicherplatz mehr frei");
                            }
                            if (thisSize >= 0 && wroteHere > thisSize) {
                                throw new IOException("Eintrag ist größer als angegeben (beschädigtes oder gefährliches Archiv)");
                            }
                            out.write(buf, 0, n);
                            p.update(e.getName(), written, total, index, count);
                        }
                    }
                    if (thisSize >= 0 && wroteHere != thisSize) {
                        throw new IOException("Größe stimmt nicht");
                    }
                    long when = e.getTime();
                    if (when > 0) {
                        tmp.setLastModified(when);
                    }
                    if (policy == FileOps.Conflict.REPLACE && Files.exists(target.toPath())) {
                        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.move(tmp.toPath(), target.toPath());
                    }
                    tmp = null;
                    r.touch(target);
                } catch (IOException ex) {
                    r.failed.add(e.getName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
                } finally {
                    if (tmp != null) {
                        tmp.delete();
                    }
                }
            }
            if (r.failed.isEmpty() && !r.cancelled) {
                r.done.add(zip.getName());
            }
        } catch (IOException ex) {
            r.failed.add(zip.getName() + ": " + (ex.getMessage() != null ? ex.getMessage() : "Archiv nicht lesbar"));
        }
        return r;
    }

    /**
     * Packt Dateien und Ordner in eine ZIP-Datei im Ordner {@code outDir}. Verknüpfungen und nicht lesbare Ordner
     * werden ausgelassen. Die Datei entsteht erst vollständig als Temporärdatei; bei Abbruch oder Fehler bleibt nichts zurück.
     */
    static FileOps.Result zipItems(List<File> items, File outDir, String zipName, FileOps.Progress progress) {
        FileOps.Result r = new FileOps.Result();
        FileOps.Progress p = progress != null ? progress : new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return false;
            }

            @Override
            public void update(String name, long done, long total, int fileIndex, int fileCount) {
            }
        };
        long total = 0;
        int count = 0;
        for (File f : items) {
            long[] m = FileOps.measure(f);
            total += m[0];
            count += (int) m[1];
        }
        File out = FileOps.uniqueIn(outDir, zipName);
        File tmp = null;
        try {
            tmp = File.createTempFile(".nolphin-", ".tmp", outDir);
            State st = new State(total, count, p);
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(tmp))) {
                for (File f : items) {
                    if (!st.add(z, f, f.getName(), r)) {
                        break;
                    }
                }
            }
            if (p.cancelled()) {
                r.cancelled = true;
                tmp.delete();
                return r;
            }
            Files.move(tmp.toPath(), out.toPath());
            tmp = null;
            r.touch(out);
            r.done.add(out.getName());
        } catch (IOException e) {
            r.failed.add(zipName + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
        return r;
    }

    private static final class State {
        final long total;
        final int count;
        final FileOps.Progress p;
        final byte[] buf = new byte[64 * 1024];
        long done;
        int index;

        State(long total, int count, FileOps.Progress p) {
            this.total = total;
            this.count = count;
            this.p = p;
        }

        /** @return false, wenn abgebrochen wurde */
        boolean add(ZipOutputStream z, File f, String name, FileOps.Result r) throws IOException {
            if (p.cancelled()) {
                return false;
            }
            if (Files.isSymbolicLink(f.toPath())) {
                r.skipped.add(f.getName() + ": Verknüpfung nicht gepackt");
                return true;
            }
            if (f.isDirectory()) {
                z.putNextEntry(new ZipEntry(name + "/"));
                z.closeEntry();
                File[] kids = f.listFiles();
                if (kids == null) {
                    r.failed.add(f.getName() + ": Ordner nicht lesbar");
                    return true;
                }
                java.util.Arrays.sort(kids);
                for (File c : kids) {
                    if (!add(z, c, name + "/" + c.getName(), r)) {
                        return false;
                    }
                }
                return true;
            }
            index++;
            InputStream in;
            try {
                in = new FileInputStream(f);   // zuerst öffnen: eine nicht lesbare Datei bekommt gar keinen Eintrag
            } catch (IOException ex) {
                r.failed.add(f.getName() + ": " + (ex.getMessage() != null ? ex.getMessage() : "nicht lesbar"));
                return true;
            }
            try (InputStream src = in) {
                ZipEntry e = new ZipEntry(name);
                e.setTime(f.lastModified());
                z.putNextEntry(e);
                int n;
                while ((n = src.read(buf)) > 0) {
                    if (p.cancelled()) {
                        return false;
                    }
                    z.write(buf, 0, n);
                    done += n;
                    p.update(f.getName(), done, total, index, count);
                }
                z.closeEntry();
            }
            return true;
        }
    }
}
