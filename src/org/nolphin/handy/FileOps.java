package org.nolphin.handy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dateien und Ordner kopieren, verschieben, löschen, umbenennen und packen (nur Java-Dateizugriff, keine Bibliotheken). */
final class FileOps {
    private FileOps() {}

    /** Freier Name im Ordner: "Name", sonst "Name (2)", "Name (3)" … (vor der Endung). */
    static File uniqueIn(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) {
            return f;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 10000; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) {
                return f;
            }
        }
        return new File(dir, base + " (" + System.currentTimeMillis() + ")" + ext);
    }

    /** Ist child gleich parent oder liegt darin? */
    static boolean isInside(File parent, File child) {
        try {
            String p = parent.getCanonicalPath();
            String c = child.getCanonicalPath();
            return c.equals(p) || c.startsWith(p + File.separator);
        } catch (IOException e) {
            return true;   // im Zweifel nicht erlauben
        }
    }

    static File copyTo(File src, File dstDir) throws IOException {
        if (src.isDirectory() && isInside(src, dstDir)) {
            throw new IOException("Ein Ordner lässt sich nicht in sich selbst kopieren");
        }
        File dst = uniqueIn(dstDir, src.getName());
        copyTree(src, dst);
        return dst;
    }

    private static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) {
                throw new IOException("Ordner nicht anlegbar: " + dst.getName());
            }
            File[] fs = src.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    copyTree(f, new File(dst, f.getName()));
                }
            }
            return;
        }
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        dst.setLastModified(src.lastModified());
    }

    /** Verschiebt; erst umbenennen (schnell), sonst kopieren und das Original löschen. */
    static File moveTo(File src, File dstDir) throws IOException {
        if (src.getParentFile() != null && src.getParentFile().equals(dstDir)) {
            return src;   // schon dort
        }
        if (src.isDirectory() && isInside(src, dstDir)) {
            throw new IOException("Ein Ordner lässt sich nicht in sich selbst verschieben");
        }
        File dst = uniqueIn(dstDir, src.getName());
        if (src.renameTo(dst)) {
            return dst;
        }
        copyTree(src, dst);
        deleteTree(src);
        return dst;
    }

    static void deleteTree(File f) throws IOException {
        if (f.getParentFile() == null) {
            throw new IOException("Die oberste Ebene wird nicht gelöscht");
        }
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File c : fs) {
                    deleteTree(c);
                }
            }
        }
        if (!f.delete() && f.exists()) {
            throw new IOException("Nicht löschbar: " + f.getName());
        }
    }

    static File rename(File f, String newName) throws IOException {
        String n = newName.trim();
        if (n.isEmpty() || n.contains("/") || n.equals(".") || n.equals("..")) {
            throw new IOException("Ungültiger Name");
        }
        File dst = new File(f.getParentFile(), n);
        if (dst.exists()) {
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
        if (d.exists()) {
            throw new IOException("Den Namen gibt es hier schon");
        }
        if (!d.mkdir()) {
            throw new IOException("Ordner nicht anlegbar");
        }
        return d;
    }

    /** Packt einen Ordner samt Inhalt in eine ZIP-Datei im Zwischenspeicher (zum Senden an den PC). */
    static File zip(File dir, File outDir) throws IOException {
        File out = new File(outDir, dir.getName() + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            addToZip(z, dir, dir.getName());
        }
        return out;
    }

    private static void addToZip(ZipOutputStream z, File f, String name) throws IOException {
        if (f.isDirectory()) {
            z.putNextEntry(new ZipEntry(name + "/"));
            z.closeEntry();
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File c : fs) {
                    addToZip(z, c, name + "/" + c.getName());
                }
            }
            return;
        }
        ZipEntry e = new ZipEntry(name);
        e.setTime(f.lastModified());
        z.putNextEntry(e);
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                z.write(buf, 0, n);
            }
        }
        z.closeEntry();
    }

    /** Gesamtgröße (Dateien in allen Unterordnern) und Anzahl der Dateien. */
    static long[] measure(File f) {
        long[] r = new long[2];
        measure(f, r);
        return r;
    }

    private static void measure(File f, long[] r) {
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
