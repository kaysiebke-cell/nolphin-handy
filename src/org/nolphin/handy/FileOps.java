package org.nolphin.handy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Dateien und Ordner kopieren, verschieben, löschen, umbenennen und packen (nur Java-Dateizugriff, keine Bibliotheken).
 *
 * Grundregeln:
 * - Verknüpfungen (Symlinks) werden nie verfolgt: Beim Löschen verschwindet nur die Verknüpfung, nicht ihr Ziel.
 * - Kopiert wird immer erst in eine Temporärdatei im Zielordner und erst danach unter dem endgültigen Namen abgelegt.
 *   Nach einem Fehler oder Abbruch bleibt keine halbe Datei zurück.
 * - Beim Verschieben über Speichergrenzen wird das Original erst gelöscht, wenn die Kopie vollständig und
 *   (per Prüfsumme) unverändert ist.
 * - Ein Fehler bei einer Datei stoppt nicht alles: Das Ergebnis nennt erfolgreiche, übersprungene und fehlgeschlagene Elemente.
 */
final class FileOps {
    private FileOps() {}

    /** Was bei einem vorhandenen Namen im Ziel geschieht. */
    enum Conflict { RENAME, SKIP, REPLACE }

    /** Meldet den Fortschritt und fragt nach, ob abgebrochen werden soll. */
    interface Progress {
        boolean cancelled();

        /**
         * @param total      Gesamtgröße aller Dateien des Vorgangs in Bytes (0 = unbekannt)
         * @param fileIndex  die wievielte Datei gerade bearbeitet wird (ab 1)
         * @param fileCount  Anzahl aller Dateien des Vorgangs
         */
        void update(String name, long done, long total, int fileIndex, int fileCount);
    }

    private static final Progress NO_PROGRESS = new Progress() {
        @Override
        public boolean cancelled() {
            return false;
        }

        @Override
        public void update(String name, long done, long total, int fileIndex, int fileCount) {
        }
    };

    /** Ergebnis eines Vorgangs mit mehreren Elementen. */
    static final class Result {
        /** Namen der Elemente, die vollständig erledigt sind. */
        final List<String> done = new ArrayList<>();
        /** "Name: Grund" für Elemente, bei denen etwas schiefging. */
        final List<String> failed = new ArrayList<>();
        /** Elemente, die bewusst nicht angefasst wurden (zum Beispiel wegen eines vorhandenen Namens). */
        final List<String> skipped = new ArrayList<>();
        /** Dateien, die entstanden oder verschwunden sind (für die Mediensuche; höchstens 500). */
        final List<File> touched = new ArrayList<>();
        /** Die Elemente der Auswahl, bei denen etwas schiefging: für "Nochmal versuchen". */
        final List<File> retry = new ArrayList<>();
        boolean cancelled;

        boolean ok() {
            return failed.isEmpty() && !cancelled;
        }

        private void touch(File f) {
            if (touched.size() < 500) {
                touched.add(f);
            }
        }

        /** Kurze Meldung für den Nutzer, zum Beispiel "3 Elemente kopiert, 1 fehlgeschlagen". */
        String summary(String verbPast) {
            StringBuilder b = new StringBuilder();
            b.append(done.size()).append(done.size() == 1 ? " Element " : " Elemente ").append(verbPast);
            if (!skipped.isEmpty()) {
                b.append(", ").append(skipped.size()).append(" übersprungen");
            }
            if (!failed.isEmpty()) {
                b.append(", ").append(failed.size()).append(" fehlgeschlagen");
            }
            if (cancelled) {
                b.append(". Abgebrochen");
            }
            return b.toString();
        }
    }

    // ---- Namen und Pfade ------------------------------------------------------------------------

    /** Freier Name im Ordner: "Name", sonst "Name (2)", "Name (3)" … (vor der Endung). */
    static File uniqueIn(File dir, String name) {
        File f = new File(dir, name);
        if (!exists(f)) {
            return f;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 10000; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!exists(f)) {
                return f;
            }
        }
        return new File(dir, base + " (" + System.currentTimeMillis() + ")" + ext);
    }

    /** Gibt es den Namen (auch als kaputte Verknüpfung)? */
    private static boolean exists(File f) {
        return f.exists() || Files.isSymbolicLink(f.toPath());
    }

    private static boolean isLink(File f) {
        return Files.isSymbolicLink(f.toPath());
    }

    /** Ist child gleich parent oder liegt darin? Im Zweifel ja (dann wird nichts getan). */
    static boolean isInside(File parent, File child) {
        try {
            String p = parent.getCanonicalPath();
            String c = child.getCanonicalPath();
            return c.equals(p) || c.startsWith(p + File.separator);
        } catch (IOException e) {
            return true;
        }
    }

    // ---- Konflikte vorab ermitteln ---------------------------------------------------------------

    /**
     * Welche Namen gibt es im Ziel schon? Ordner, die es dort schon gibt, werden durchsucht: dann zählen nur die
     * gleichen Dateien darin. Die Namen sind relativ zum Zielordner ("Ordner/Unter/datei.txt"). Höchstens 500.
     */
    static List<String> conflicts(List<File> sources, File dstDir) {
        List<String> out = new ArrayList<>();
        for (File src : sources) {
            File dst = new File(dstDir, src.getName());
            if (src.getParentFile() != null && src.getParentFile().equals(dstDir)) {
                continue;   // liegt schon dort (beim Verschieben ohne Wirkung)
            }
            collectConflicts(src, dst, src.getName(), out);
        }
        return out;
    }

    private static void collectConflicts(File src, File dst, String rel, List<String> out) {
        if (out.size() >= 500 || !exists(dst)) {
            return;
        }
        boolean bothDirs = src.isDirectory() && !isLink(src) && dst.isDirectory() && !isLink(dst);
        if (!bothDirs) {
            out.add(rel);
            return;
        }
        File[] children = src.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            collectConflicts(c, new File(dst, c.getName()), rel + "/" + c.getName(), out);
        }
    }

    // ---- Kopieren -------------------------------------------------------------------------------

    static Result copyAll(List<File> sources, File dstDir, Conflict conflict, Progress progress) {
        return transfer(sources, dstDir, conflict, progress, false, false);
    }

    static Result moveAll(List<File> sources, File dstDir, Conflict conflict, Progress progress) {
        return transfer(sources, dstDir, conflict, progress, true, false);
    }

    /** Für Tests: erzwingt bei moveAll den Weg über Kopieren und Prüfen statt Umbenennen. */
    static Result moveAllByCopy(List<File> sources, File dstDir, Conflict conflict, Progress progress) {
        return transfer(sources, dstDir, conflict, progress, true, true);
    }

    private static final int OK = 0, SKIPPED = 1, FAILED = 2, CANCELLED = 3;

    private static final class Ctx {
        final Conflict conflict;
        final Progress progress;
        final Result result;
        final boolean moving;
        final boolean forceCopy;
        final byte[] buf = new byte[64 * 1024];
        long done;
        long total;
        int files;
        int index;
        String name = "";

        Ctx(Conflict conflict, Progress progress, Result result, boolean moving, boolean forceCopy) {
            this.conflict = conflict;
            this.progress = progress == null ? NO_PROGRESS : progress;
            this.result = result;
            this.moving = moving;
            this.forceCopy = forceCopy;
        }

        boolean cancelled() {
            if (progress.cancelled()) {
                result.cancelled = true;
            }
            return result.cancelled;
        }
    }

    private static Result transfer(List<File> sources, File dstDir, Conflict conflict, Progress progress,
                                   boolean moving, boolean forceCopy) {
        Result r = new Result();
        Ctx x = new Ctx(conflict == null ? Conflict.RENAME : conflict, progress, r, moving, forceCopy);
        for (File s : sources) {
            long[] m = measure(s);
            x.total += m[0];
            x.files += (int) m[1];
        }
        if (!dstDir.isDirectory()) {
            r.failed.add(dstDir.getName() + ": Zielordner nicht vorhanden");
            return r;
        }
        for (File src : sources) {
            if (x.cancelled()) {
                break;
            }
            String name = src.getName();
            if (src.isDirectory() && !isLink(src) && isInside(src, dstDir)) {
                r.failed.add(name + ": ein Ordner lässt sich nicht in sich selbst " + (moving ? "verschieben" : "kopieren"));
                continue;
            }
            if (moving && src.getParentFile() != null && src.getParentFile().equals(dstDir)) {
                r.skipped.add(name + ": liegt schon dort");
                continue;
            }
            File dst = new File(dstDir, name);
            if (exists(dst)) {
                // Ordner werden bei "überspringen" und "ersetzen" zusammengeführt: nur gleiche Dateien sind ein Konflikt
                boolean mergeDirs = x.conflict != Conflict.RENAME && src.isDirectory() && !isLink(src) && dst.isDirectory() && !isLink(dst);
                if (!mergeDirs) {
                    if (x.conflict == Conflict.SKIP) {
                        r.skipped.add(name + ": gibt es im Ziel schon");
                        x.index += (int) measure(src)[1];
                        continue;
                    }
                    if (x.conflict == Conflict.RENAME) {
                        dst = uniqueIn(dstDir, name);
                    }
                }
            }
            x.name = name;
            int failedBefore = r.failed.size();
            int st = x.moving ? moveNode(x, src, dst) : copyNode(x, src, dst);
            if (st == OK) {
                r.done.add(name);
            } else if (st == SKIPPED) {
                r.skipped.add(name);
            } else if (st == CANCELLED) {
                break;
            }
            // bei FAILED steht der Grund schon in r.failed
            if (st == FAILED || r.failed.size() > failedBefore) {
                r.retry.add(src);
            }
        }
        return r;
    }

    /** Kopiert eine Datei oder einen Ordner. Ordner, die es im Ziel schon gibt, werden zusammengeführt. */
    private static int copyNode(Ctx x, File src, File dst) {
        if (x.cancelled()) {
            return CANCELLED;
        }
        if (isLink(src)) {
            return copyLink(x, src, dst);
        }
        if (src.isDirectory()) {
            return copyDir(x, src, dst, false);
        }
        return copyFile(x, src, dst, false);
    }

    private static int copyLink(Ctx x, File src, File dst) {
        try {
            if (exists(dst)) {
                if (x.conflict == Conflict.SKIP) {
                    return SKIPPED;
                }
                if (x.conflict == Conflict.RENAME) {
                    dst = uniqueIn(dst.getParentFile(), dst.getName());
                } else {
                    Files.delete(dst.toPath());
                }
            }
            Files.createSymbolicLink(dst.toPath(), Files.readSymbolicLink(src.toPath()));
            return OK;
        } catch (IOException | UnsupportedOperationException e) {
            // Auf manchen Speichern (zum Beispiel SD-Karte) gibt es keine Verknüpfungen
            x.result.skipped.add(src.getName() + ": Verknüpfung nicht kopiert");
            return SKIPPED;
        }
    }

    /** @param deleteSource beim Verschieben über Kopieren: Originale nach erfolgreicher Prüfung löschen */
    private static int copyDir(Ctx x, File src, File dst, boolean deleteSource) {
        if (exists(dst) && (isLink(dst) || !dst.isDirectory())) {
            x.result.failed.add(dst.getName() + ": im Ziel gibt es eine Datei mit diesem Namen");
            return FAILED;
        }
        if (!dst.isDirectory() && !dst.mkdir()) {
            x.result.failed.add(dst.getName() + ": Ordner im Ziel nicht anlegbar");
            return FAILED;
        }
        File[] children = src.listFiles();
        if (children == null) {
            x.result.failed.add(src.getName() + ": Ordner nicht lesbar");
            return FAILED;
        }
        boolean failed = false;
        boolean skipped = false;
        for (File c : children) {
            File d = new File(dst, c.getName());
            int st;
            if (isLink(c)) {
                st = copyLink(x, c, d);
            } else if (c.isDirectory()) {
                st = copyDir(x, c, d, deleteSource);
            } else {
                st = copyFile(x, c, d, deleteSource);
            }
            if (st == CANCELLED) {
                return CANCELLED;
            }
            if (st == FAILED) {
                failed = true;
            } else if (st == SKIPPED) {
                skipped = true;
            }
        }
        // Original-Ordner erst entfernen, wenn alles daraus erledigt ist (nichts übersprungen, nichts fehlgeschlagen)
        if (!failed && !skipped && deleteSource) {
            File[] left = src.listFiles();
            if (left != null && left.length == 0 && !src.delete()) {
                x.result.failed.add(src.getName() + ": Original-Ordner nicht gelöscht");
                return FAILED;
            }
        }
        return failed ? FAILED : OK;
    }

    /**
     * Kopiert eine Datei über eine Temporärdatei und legt sie erst danach ab. Bei einem Fehler oder Abbruch wird die
     * Temporärdatei gelöscht. Beim Verschieben wird die Kopie per Prüfsumme geprüft, bevor das Original verschwindet.
     */
    private static int copyFile(Ctx x, File src, File dst, boolean deleteSource) {
        File dir = dst.getParentFile();
        File target = dst;
        if (exists(target)) {
            if (x.conflict == Conflict.SKIP) {
                x.result.skipped.add(src.getName() + ": gibt es im Ziel schon");
                return SKIPPED;
            }
            if (x.conflict == Conflict.RENAME) {
                target = uniqueIn(dir, dst.getName());
            } else if (target.isDirectory() && !isLink(target)) {
                x.result.failed.add(src.getName() + ": im Ziel gibt es einen Ordner mit diesem Namen");
                return FAILED;
            }
        }
        File tmp = null;
        try {
            tmp = File.createTempFile(".nolphin-", ".tmp", dir);
            CRC32 srcCrc = new CRC32();
            long copied = 0;
            x.index++;
            x.progress.update(src.getName(), x.done, x.total, x.index, x.files);
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(tmp)) {
                int n;
                while ((n = in.read(x.buf)) > 0) {
                    if (x.cancelled()) {
                        out.close();
                        tmp.delete();
                        return CANCELLED;
                    }
                    out.write(x.buf, 0, n);
                    srcCrc.update(x.buf, 0, n);
                    copied += n;
                    x.done += n;
                    x.progress.update(src.getName(), x.done, x.total, x.index, x.files);
                }
                out.flush();
            }
            if (x.cancelled()) {
                tmp.delete();
                return CANCELLED;
            }
            if (tmp.length() != copied || copied != src.length()) {
                throw new IOException("Größe stimmt nicht (Quelle wurde währenddessen verändert)");
            }
            if (deleteSource && crcOf(tmp, x.buf) != srcCrc.getValue()) {
                throw new IOException("Prüfsumme der Kopie stimmt nicht");
            }
            tmp.setLastModified(src.lastModified());
            if (x.conflict == Conflict.REPLACE && exists(target)) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tmp.toPath(), target.toPath());
            }
            tmp = null;
            x.result.touch(target);
            if (deleteSource) {
                if (!src.delete() && src.exists()) {
                    x.result.failed.add(src.getName() + ": kopiert, aber das Original ließ sich nicht löschen");
                    return FAILED;
                }
                x.result.touch(src);
            }
            return OK;
        } catch (IOException e) {
            x.result.failed.add(src.getName() + ": " + reason(e));
            return FAILED;
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    private static long crcOf(File f, byte[] buf) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                crc.update(buf, 0, n);
            }
        }
        return crc.getValue();
    }

    private static String reason(Exception e) {
        String m = e.getMessage();
        if (e instanceof java.io.FileNotFoundException && m != null && m.contains("Permission denied")) {
            return "kein Zugriff";
        }
        if (e instanceof java.nio.file.NoSuchFileException) {
            return "Datei fehlt";
        }
        if (m != null && m.contains("No space left")) {
            return "kein Speicherplatz mehr frei";
        }
        return m != null ? m : e.getClass().getSimpleName();
    }

    // ---- Verschieben ----------------------------------------------------------------------------

    /** Verschiebt: erst Umbenennen (schnell, ohne Datenverlust), sonst kopieren, prüfen und erst dann löschen. */
    private static int moveNode(Ctx x, File src, File dst) {
        if (x.cancelled()) {
            return CANCELLED;
        }
        if (!x.forceCopy && !exists(dst)) {
            long size = measure(src)[0];
            if (src.renameTo(dst)) {
                x.done += size;
                x.index += (int) measure(dst)[1];
                x.progress.update(src.getName(), x.done, x.total, x.index, x.files);
                x.result.touch(dst);
                x.result.touch(src);
                return OK;
            }
        }
        if (isLink(src)) {
            int st = copyLink(x, src, dst);
            if (st == OK) {
                try {
                    Files.delete(src.toPath());
                } catch (IOException e) {
                    x.result.failed.add(src.getName() + ": Verknüpfung kopiert, Original nicht gelöscht");
                    return FAILED;
                }
            }
            return st;
        }
        if (src.isDirectory()) {
            return copyDir(x, src, dst, true);
        }
        return copyFile(x, src, dst, true);
    }

    // ---- Löschen --------------------------------------------------------------------------------

    /** Löscht Dateien und Ordner endgültig. Verknüpfungen werden nur selbst entfernt, ihr Ziel bleibt unberührt. */
    static Result deleteAll(List<File> items, Progress progress) {
        Result r = new Result();
        Ctx x = new Ctx(Conflict.RENAME, progress, r, false, false);
        for (File f : items) {
            if (f.getParentFile() == null) {
                continue;   // die oberste Ebene wird nie gelöscht, also auch nicht gemessen
            }
            long[] m = measure(f);
            x.total += m[0];
            x.files += (int) m[1];
        }
        for (File f : items) {
            if (x.cancelled()) {
                break;
            }
            if (f.getParentFile() == null) {
                r.failed.add(f.getName() + ": die oberste Ebene wird nicht gelöscht");
                continue;
            }
            int before = r.failed.size();
            deleteNode(x, f);
            if (r.failed.size() == before && !r.cancelled) {
                r.done.add(f.getName());
            } else if (r.failed.size() > before) {
                r.retry.add(f);
            }
        }
        return r;
    }

    private static void deleteNode(Ctx x, File f) {
        if (x.cancelled()) {
            return;
        }
        if (!isLink(f) && f.isDirectory()) {
            File[] children = f.listFiles();
            if (children == null) {
                x.result.failed.add(f.getName() + ": Ordner nicht lesbar");
                return;
            }
            for (File c : children) {
                deleteNode(x, c);
            }
            if (x.result.cancelled) {
                return;
            }
        }
        boolean plainFile = isLink(f) || !f.isDirectory();
        long size = plainFile && !isLink(f) ? f.length() : 0;
        try {
            Files.delete(f.toPath());
            x.result.touch(f);
            if (plainFile) {
                x.done += size;
                x.index++;
                x.progress.update(f.getName(), x.done, x.total, x.index, x.files);
            }
        } catch (IOException e) {
            x.result.failed.add(f.getName() + ": " + (f.isDirectory() ? "Ordner nicht leer oder nicht löschbar" : reason(e)));
        }
    }

    // ---- Umbenennen, neuer Ordner ----------------------------------------------------------------

    static File rename(File f, String newName) throws IOException {
        String n = newName.trim();
        if (n.isEmpty() || n.contains("/") || n.equals(".") || n.equals("..")) {
            throw new IOException("Ungültiger Name");
        }
        File dst = new File(f.getParentFile(), n);
        if (exists(dst)) {
            throw new IOException("Den Namen gibt es hier schon");
        }
        if (!f.renameTo(dst)) {
            throw new IOException("Umbenennen nicht möglich");
        }
        return dst;
    }

    static File newFolder(File parent, String name) throws IOException {
        String n = name.trim();
        if (n.isEmpty() || n.contains("/") || n.equals(".") || n.equals("..")) {
            throw new IOException("Ungültiger Name");
        }
        File d = new File(parent, n);
        if (exists(d)) {
            throw new IOException("Den Namen gibt es hier schon");
        }
        if (!d.mkdir()) {
            throw new IOException("Ordner nicht anlegbar");
        }
        return d;
    }

    // ---- ZIP --------------------------------------------------------------------------------------

    /**
     * Packt einen Ordner samt Inhalt in eine ZIP-Datei im Zwischenspeicher (zum Senden an den PC).
     * Verknüpfungen und nicht lesbare Ordner werden ausgelassen. Die Datei entsteht erst vollständig als
     * Temporärdatei und wird dann unter ihrem Namen abgelegt.
     */
    static File zip(File dir, File outDir) throws IOException {
        File out = new File(outDir, dir.getName() + ".zip");
        File tmp = File.createTempFile(".nolphin-", ".tmp", outDir);
        boolean ok = false;
        try {
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(tmp))) {
                addToZip(z, dir, dir.getName(), new byte[64 * 1024]);
            }
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ok = true;
        } finally {
            if (!ok) {
                tmp.delete();
            }
        }
        return out;
    }

    private static void addToZip(ZipOutputStream z, File f, String name, byte[] buf) throws IOException {
        if (isLink(f)) {
            return;
        }
        if (f.isDirectory()) {
            z.putNextEntry(new ZipEntry(name + "/"));
            z.closeEntry();
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File c : fs) {
                    addToZip(z, c, name + "/" + c.getName(), buf);
                }
            }
            return;
        }
        ZipEntry e = new ZipEntry(name);
        e.setTime(f.lastModified());
        z.putNextEntry(e);
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                z.write(buf, 0, n);
            }
        }
        z.closeEntry();
    }

    // ---- Größe --------------------------------------------------------------------------------------

    /** Gesamtgröße (Dateien in allen Unterordnern) und Anzahl der Dateien. Verknüpfungen zählen nicht mit. */
    static long[] measure(File f) {
        long[] r = new long[2];
        measure(f, r);
        return r;
    }

    private static void measure(File f, long[] r) {
        if (isLink(f)) {
            return;
        }
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File c : fs) {
                    measure(c, r);
                }
            }
        } else {
            r[0] += f.length();
            r[1]++;
        }
    }
}
