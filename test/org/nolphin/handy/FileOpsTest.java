package org.nolphin.handy;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
/** Prüft die Dateioperationen der Handy-App auf dem PC (kopieren, verschieben, umbenennen, packen, löschen). */
public class FileOpsTest {
    static int fails = 0, n = 0;
    static void ok(boolean c, String what) { n++; if (!c) { fails++; System.out.println("FEHLER: " + what); } }
    static void deleteAll(File f) { File[] fs = f.listFiles(); if (fs != null) for (File c : fs) deleteAll(c); f.delete(); }
    public static void main(String[] a) throws Exception {
        File w = java.nio.file.Files.createTempDirectory("fileops").toFile();
        File src = new File(w, "Quelle"); File sub = new File(src, "Unter"); sub.mkdirs();
        Files.write(new File(src, "a.txt").toPath(), "hallo".getBytes());
        Files.write(new File(sub, "b.pdf").toPath(), "welt".getBytes());
        File dst = new File(w, "Ziel"); dst.mkdirs();
        // Kopieren
        File c = FileOps.copyTo(src, dst);
        ok(c.getName().equals("Quelle") && new File(c, "Unter/b.pdf").isFile() && src.isDirectory(), "kopieren Ordner");
        File c2 = FileOps.copyTo(src, dst);
        ok(c2.getName().equals("Quelle (2)"), "Namenskonflikt -> (2): " + c2.getName());
        File f2 = FileOps.copyTo(new File(src, "a.txt"), dst);
        File f3 = FileOps.copyTo(new File(src, "a.txt"), dst);
        ok(f2.getName().equals("a.txt") && f3.getName().equals("a (2).txt"), "Datei (2) vor Endung: " + f3.getName());
        // in sich selbst
        boolean threw = false; try { FileOps.copyTo(src, sub); } catch (IOException e) { threw = true; }
        ok(threw, "Ordner nicht in sich selbst kopieren");
        threw = false; try { FileOps.moveTo(src, sub); } catch (IOException e) { threw = true; }
        ok(threw && src.isDirectory(), "Ordner nicht in sich selbst verschieben");
        // Verschieben
        File m = FileOps.moveTo(c2, new File(w, "Anderswo") {{ mkdirs(); }});
        ok(m.isDirectory() && !c2.exists() && new File(m, "a.txt").isFile(), "verschieben");
        ok(FileOps.moveTo(f2, dst).equals(f2), "verschieben in gleichen Ordner = nichts");
        // Umbenennen
        File r = FileOps.rename(f3, "neu.txt");
        ok(r.isFile() && !f3.exists(), "umbenennen");
        threw = false; try { FileOps.rename(r, "a.txt"); } catch (IOException e) { threw = true; }
        ok(threw, "Umbenennen auf vorhandenen Namen abgelehnt");
        threw = false; try { FileOps.rename(r, "../x"); } catch (IOException e) { threw = true; }
        ok(threw, "Umbenennen mit / abgelehnt");
        // Neuer Ordner
        File nf = FileOps.newFolder(w, "Neu");
        ok(nf.isDirectory(), "neuer Ordner");
        threw = false; try { FileOps.newFolder(w, "Neu"); } catch (IOException e) { threw = true; }
        ok(threw, "doppelter Ordnername abgelehnt");
        // ZIP
        File z = FileOps.zip(src, w);
        int entries = 0; boolean hasB = false;
        try (ZipInputStream zi = new ZipInputStream(new FileInputStream(z))) { ZipEntry e; while ((e = zi.getNextEntry()) != null) { entries++; if (e.getName().equals("Quelle/Unter/b.pdf")) hasB = true; } }
        ok(hasB && entries == 4, "zip Inhalt (" + entries + " Einträge)");
        // Löschen
        FileOps.deleteTree(c);
        ok(!c.exists(), "löschen Baum");
        long[] ms = FileOps.measure(src);
        ok(ms[0] == 9 && ms[1] == 2, "messen " + ms[0] + "/" + ms[1]);
        threw = false; try { FileOps.deleteTree(new File("/")); } catch (IOException e) { threw = true; }
        ok(threw, "Wurzel nicht löschbar");
        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden");
        deleteAll(w);
        System.exit(fails == 0 ? 0 : 1);
    }
}
