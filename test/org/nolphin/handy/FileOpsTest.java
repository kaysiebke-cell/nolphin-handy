package org.nolphin.handy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Prüft die Dateioperationen der Handy-App auf dem PC (kopieren, verschieben, umbenennen, packen, löschen). */
public class FileOpsTest {
    static int fails = 0, n = 0;

    static void ok(boolean c, String what) {
        n++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    static File dir(File parent, String name) {
        File d = new File(parent, name);
        d.mkdirs();
        return d;
    }

    static File file(File parent, String name, String content) throws IOException {
        File f = new File(parent, name);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes("UTF-8"));
        return f;
    }

    static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), "UTF-8");
    }

    static List<File> list(File... f) {
        return new ArrayList<>(Arrays.asList(f));
    }

    /** Gibt es irgendwo unter dir eine Temporärdatei von FileOps? */
    static boolean hasTemp(File d) {
        File[] fs = d.listFiles();
        if (fs == null) {
            return false;
        }
        for (File f : fs) {
            if (f.getName().startsWith(".nolphin-") && f.getName().endsWith(".tmp")) {
                return true;
            }
            if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory() && hasTemp(f)) {
                return true;
            }
        }
        return false;
    }

    static boolean runningAsRoot(File probe) throws IOException {
        File f = file(probe, "probe-root.txt", "x");
        f.setReadable(false);
        boolean readable = f.canRead();
        f.setReadable(true);
        f.delete();
        return readable;
    }

    public static void main(String[] a) throws Exception {
        File w = Files.createTempDirectory("fileops").toFile();
        boolean root = runningAsRoot(w);
        try {
            kopieren(w);
            konflikte(w);
            verschieben(w);
            loeschenUndLinks(w);
            fehlerfaelle(w, root);
            abbruch(w);
            namen(w);
            zip(w);
        } finally {
            deleteAll(w);
        }
        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden" + (root ? " (als root: Zugriffstests übersprungen)" : ""));
        System.exit(fails == 0 ? 0 : 1);
    }

    static void deleteAll(File f) {
        File[] fs = f.listFiles();
        if (fs != null && !Files.isSymbolicLink(f.toPath())) {
            for (File c : fs) {
                c.setReadable(true);
                c.setExecutable(true);
                deleteAll(c);
            }
        }
        f.setWritable(true);
        f.delete();
    }

    static void kopieren(File w) throws Exception {
        File t = dir(w, "kopieren");
        File src = dir(t, "Quelle");
        file(src, "a.txt", "hallo");
        file(src, "Unter/b.pdf", "welt");
        File dst = dir(t, "Ziel");

        FileOps.Result r = FileOps.copyAll(list(src), dst, FileOps.Conflict.RENAME, null);
        ok(r.ok() && r.done.contains("Quelle") && new File(dst, "Quelle/Unter/b.pdf").isFile() && src.isDirectory(), "kopieren Ordner");
        ok(read(new File(dst, "Quelle/a.txt")).equals("hallo"), "kopieren: Inhalt stimmt");
        ok(!hasTemp(dst), "kopieren: keine Temporärdatei übrig");

        r = FileOps.copyAll(list(src), dst, FileOps.Conflict.RENAME, null);
        ok(r.ok() && new File(dst, "Quelle (2)/a.txt").isFile(), "Namenskonflikt Ordner: 'Quelle (2)'");

        File one = new File(src, "a.txt");
        FileOps.copyAll(list(one), dst, FileOps.Conflict.RENAME, null);
        FileOps.copyAll(list(one), dst, FileOps.Conflict.RENAME, null);
        ok(new File(dst, "a.txt").isFile() && new File(dst, "a (2).txt").isFile(), "Namenskonflikt Datei: 'a (2).txt' vor der Endung");

        File sub = new File(src, "Unter");
        r = FileOps.copyAll(list(src), sub, FileOps.Conflict.RENAME, null);
        ok(!r.ok() && r.failed.size() == 1 && src.isDirectory() && sub.list().length == 1, "Ordner nicht in sich selbst kopieren");
        r = FileOps.copyAll(list(src), new File(t, "gibtsnicht"), FileOps.Conflict.RENAME, null);
        ok(!r.ok(), "fehlender Zielordner wird gemeldet");
    }

    static void konflikte(File w) throws Exception {
        File t = dir(w, "konflikte");
        File src = dir(t, "q");
        File dst = dir(t, "z");
        File f = file(src, "doc.txt", "NEU");
        file(dst, "doc.txt", "ALT");

        FileOps.Result r = FileOps.copyAll(list(f), dst, FileOps.Conflict.SKIP, null);
        ok(r.skipped.size() == 1 && r.done.isEmpty() && read(new File(dst, "doc.txt")).equals("ALT"), "überspringen: vorhandene Datei bleibt");
        r = FileOps.copyAll(list(f), dst, FileOps.Conflict.REPLACE, null);
        ok(r.ok() && read(new File(dst, "doc.txt")).equals("NEU") && dst.list().length == 1, "ersetzen: Datei überschrieben, nichts übrig");
        file(dst, "doc.txt", "ALT");
        r = FileOps.copyAll(list(f), dst, FileOps.Conflict.RENAME, null);
        ok(r.ok() && read(new File(dst, "doc.txt")).equals("ALT") && read(new File(dst, "doc (2).txt")).equals("NEU"), "umbenennen: beide bleiben");

        // Ordner zusammenführen
        File folder = dir(t, "Ordner");
        file(folder, "neu.txt", "n");
        file(folder, "gleich.txt", "QUELLE");
        File target = dir(t, "ziel2");
        file(target, "Ordner/gleich.txt", "ZIEL");
        file(target, "Ordner/nur-im-ziel.txt", "z");
        r = FileOps.copyAll(list(folder), target, FileOps.Conflict.REPLACE, null);
        ok(r.ok() && read(new File(target, "Ordner/gleich.txt")).equals("QUELLE") && new File(target, "Ordner/nur-im-ziel.txt").isFile()
                && new File(target, "Ordner/neu.txt").isFile(), "ersetzen mit Ordner: zusammengeführt, fremde Dateien bleiben");
        File target3 = dir(t, "ziel3");
        file(target3, "Ordner/gleich.txt", "ZIEL");
        r = FileOps.copyAll(list(folder), target3, FileOps.Conflict.SKIP, null);
        ok(r.skipped.size() == 1 && read(new File(target3, "Ordner/gleich.txt")).equals("ZIEL") && !new File(target3, "Ordner/neu.txt").exists(),
                "überspringen mit Ordner: vorhandener Ordner bleibt unberührt");
    }

    static void verschieben(File w) throws Exception {
        File t = dir(w, "verschieben");
        File src = dir(t, "Quelle");
        file(src, "a.txt", "A");
        file(src, "Unter/b.txt", "B");
        File dst = dir(t, "Ziel");

        FileOps.Result r = FileOps.moveAll(list(src), dst, FileOps.Conflict.RENAME, null);
        ok(r.ok() && !src.exists() && read(new File(dst, "Quelle/Unter/b.txt")).equals("B"), "verschieben (Umbenennen)");

        File src2 = dir(t, "Quelle2");
        file(src2, "a.txt", "AA");
        file(src2, "Unter/tief/b.txt", "BB");
        File dst2 = dir(t, "Ziel2");
        r = FileOps.moveAllByCopy(list(src2), dst2, FileOps.Conflict.RENAME, null);
        ok(r.ok() && !src2.exists() && read(new File(dst2, "Quelle2/Unter/tief/b.txt")).equals("BB") && read(new File(dst2, "Quelle2/a.txt")).equals("AA"),
                "verschieben über Kopieren und Prüfen: alles angekommen, Original weg");
        ok(!hasTemp(dst2), "verschieben: keine Temporärdatei übrig");

        File same = file(dst2, "x.txt", "x");
        r = FileOps.moveAll(list(same), dst2, FileOps.Conflict.RENAME, null);
        ok(r.skipped.size() == 1 && same.isFile(), "verschieben in den gleichen Ordner: nichts passiert");

        File src3 = dir(t, "Quelle3");
        file(src3, "Unter/x.txt", "x");
        r = FileOps.moveAll(list(src3), new File(src3, "Unter"), FileOps.Conflict.RENAME, null);
        ok(!r.ok() && src3.isDirectory() && new File(src3, "Unter/x.txt").isFile(), "Ordner nicht in sich selbst verschieben");
    }

    static void loeschenUndLinks(File w) throws Exception {
        File t = dir(w, "loeschen");
        File outside = dir(t, "draussen");
        File keep = file(outside, "wichtig.txt", "bleibt");
        File victim = dir(t, "Opfer");
        file(victim, "a.txt", "a");
        file(victim, "Unter/b.txt", "b");
        boolean links = true;
        try {
            Files.createSymbolicLink(new File(victim, "verweis").toPath(), outside.toPath());
            Files.createSymbolicLink(new File(victim, "kaputt").toPath(), new File(t, "gibtsnicht").toPath());
        } catch (IOException | UnsupportedOperationException e) {
            links = false;
        }
        FileOps.Result r = FileOps.deleteAll(list(victim), null);
        ok(r.ok() && !victim.exists(), "löschen: Ordner samt Inhalt weg");
        ok(keep.isFile() && read(keep).equals("bleibt"), "löschen folgt keiner Verknüpfung: Ziel außerhalb bleibt erhalten (" + (links ? "mit" : "ohne") + " Verknüpfung getestet)");

        // Verknüpfung direkt löschen
        if (links) {
            File l = new File(t, "direkt");
            Files.createSymbolicLink(l.toPath(), outside.toPath());
            r = FileOps.deleteAll(list(l), null);
            ok(r.ok() && !Files.isSymbolicLink(l.toPath()) && keep.isFile(), "Verknüpfung auf Ordner löschen: nur die Verknüpfung verschwindet");

            // Kopieren: Verknüpfung auf den Elternordner darf keine Endlosschleife auslösen
            File loop = dir(t, "schleife");
            file(loop, "x.txt", "x");
            Files.createSymbolicLink(new File(loop, "zurueck").toPath(), loop.toPath());
            File dst = dir(t, "schleife-ziel");
            long start = System.currentTimeMillis();
            r = FileOps.copyAll(list(loop), dst, FileOps.Conflict.RENAME, null);
            ok(System.currentTimeMillis() - start < 5000 && new File(dst, "schleife/x.txt").isFile(), "Kopieren mit Verknüpfung auf den Elternordner: keine Endlosschleife");
            File copied = new File(dst, "schleife/zurueck");
            ok(Files.isSymbolicLink(copied.toPath()) || !copied.exists(), "Verknüpfung wird als Verknüpfung kopiert (oder ausgelassen), nicht als echter Ordner");
        }
        long[] m = FileOps.measure(dir(t, "messen"));
        ok(m[0] == 0 && m[1] == 0, "messen: leerer Ordner");

        File root = new File("/");
        r = FileOps.deleteAll(list(root), null);
        ok(!r.ok() && r.done.isEmpty(), "oberste Ebene nicht löschbar");
    }

    static void fehlerfaelle(File w, boolean root) throws Exception {
        if (root) {
            return;
        }
        File t = dir(w, "fehler");
        // nicht lesbare Datei: Rest wird trotzdem kopiert, Fehler wird genannt, keine halbe Datei
        File src = dir(t, "Quelle");
        file(src, "gut1.txt", "1");
        File bad = file(src, "schlecht.txt", "2");
        file(src, "Unter/gut2.txt", "3");
        bad.setReadable(false);
        File dst = dir(t, "Ziel");
        FileOps.Result r = FileOps.copyAll(list(src), dst, FileOps.Conflict.RENAME, null);
        ok(!r.ok() && r.failed.size() == 1 && r.failed.get(0).startsWith("schlecht.txt"), "nicht lesbare Datei: genau ein Fehler mit Namen (" + r.failed + ")");
        ok(new File(dst, "Quelle/gut1.txt").isFile() && new File(dst, "Quelle/Unter/gut2.txt").isFile() && !new File(dst, "Quelle/schlecht.txt").exists(),
                "nicht lesbare Datei: Rest wurde kopiert, keine halbe Datei");
        ok(!hasTemp(dst), "nicht lesbare Datei: keine Temporärdatei übrig");
        ok(!r.done.contains("Quelle"), "unvollständiger Ordner gilt nicht als erledigt");

        // Verschieben über Kopieren: Original der nicht lesbaren Datei bleibt, die anderen sind weg
        File src2 = dir(t, "Quelle2");
        file(src2, "gut.txt", "g");
        File bad2 = file(src2, "schlecht.txt", "s");
        bad2.setReadable(false);
        File dst2 = dir(t, "Ziel2");
        r = FileOps.moveAllByCopy(list(src2), dst2, FileOps.Conflict.RENAME, null);
        ok(!r.ok() && bad2.exists() && !new File(src2, "gut.txt").exists() && new File(dst2, "Quelle2/gut.txt").isFile(),
                "Verschieben mit Fehler: Original der fehlerhaften Datei bleibt, geprüfte Dateien sind verschoben");
        ok(src2.isDirectory(), "Verschieben mit Fehler: Quellordner bleibt erhalten");
        ok(!hasTemp(dst2), "Verschieben mit Fehler: keine Temporärdatei übrig");

        // nicht lesbarer Unterordner beim Kopieren
        File src3 = dir(t, "Quelle3");
        file(src3, "offen.txt", "o");
        File closed = dir(src3, "zu");
        file(closed, "geheim.txt", "g");
        closed.setReadable(false);
        closed.setExecutable(false);
        File dst3 = dir(t, "Ziel3");
        r = FileOps.copyAll(list(src3), dst3, FileOps.Conflict.RENAME, null);
        ok(!r.ok() && new File(dst3, "Quelle3/offen.txt").isFile(), "nicht lesbarer Unterordner: Rest kopiert, Fehler gemeldet (" + r.failed + ")");
        closed.setReadable(true);
        closed.setExecutable(true);

        // Löschen: nicht lesbarer Ordner
        File del = dir(t, "Loeschen");
        file(del, "a.txt", "a");
        File closed2 = dir(del, "zu");
        file(closed2, "b.txt", "b");
        closed2.setReadable(false);
        closed2.setExecutable(false);
        r = FileOps.deleteAll(list(del), null);
        ok(!r.ok() && !new File(del, "a.txt").exists(), "Löschen mit nicht lesbarem Ordner: Rest gelöscht, Fehler gemeldet");
        closed2.setReadable(true);
        closed2.setExecutable(true);
    }

    static void abbruch(File w) throws Exception {
        File t = dir(w, "abbruch");
        File big = new File(t, "gross.bin");
        byte[] block = new byte[1024 * 1024];
        try (java.io.OutputStream o = new java.io.FileOutputStream(big)) {
            for (int i = 0; i < 16; i++) {
                o.write(block);
            }
        }
        File dst = dir(t, "Ziel");
        final int[] updates = { 0 };
        FileOps.Progress cancelAfterSome = new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return updates[0] >= 20;
            }

            @Override
            public void update(String name, long done, long total) {
                updates[0]++;
            }
        };
        FileOps.Result r = FileOps.copyAll(list(big), dst, FileOps.Conflict.RENAME, cancelAfterSome);
        ok(r.cancelled && !r.ok(), "Abbruch wird gemeldet");
        ok(dst.list().length == 0, "Abbruch: keine halbe Datei und keine Temporärdatei im Ziel (" + Arrays.toString(dst.list()) + ")");
        ok(big.length() == 16L * 1024 * 1024, "Abbruch: Original unverändert");

        // Fortschritt erreicht am Ende die Gesamtgröße
        final long[] last = { 0, 0 };
        FileOps.Result r2 = FileOps.copyAll(list(big), dir(t, "Ziel2"), FileOps.Conflict.RENAME, new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return false;
            }

            @Override
            public void update(String name, long done, long total) {
                last[0] = done;
                last[1] = total;
            }
        });
        ok(r2.ok() && last[0] == last[1] && last[1] == 16L * 1024 * 1024, "Fortschritt: am Ende gleich der Gesamtgröße (" + last[0] + "/" + last[1] + ")");

        // Abbruch beim Verschieben über Kopieren: Original bleibt
        updates[0] = 0;
        File mv = new File(t, "mv.bin");
        Files.copy(big.toPath(), mv.toPath());
        File dst3 = dir(t, "Ziel3");
        FileOps.Result r3 = FileOps.moveAllByCopy(list(mv), dst3, FileOps.Conflict.RENAME, cancelAfterSome);
        ok(r3.cancelled && mv.isFile() && mv.length() == 16L * 1024 * 1024 && dst3.list().length == 0, "Abbruch beim Verschieben: Original bleibt, nichts im Ziel");
    }

    static void namen(File w) throws Exception {
        File t = dir(w, "namen");
        File f = file(t, "alt.txt", "x");
        File r = FileOps.rename(f, "neu.txt");
        ok(r.isFile() && !f.exists(), "umbenennen");
        file(t, "a.txt", "a");
        boolean threw = false;
        try {
            FileOps.rename(r, "a.txt");
        } catch (IOException e) {
            threw = true;
        }
        ok(threw, "Umbenennen auf vorhandenen Namen abgelehnt");
        for (String bad : new String[] { "../x", "", ".", ".." }) {
            threw = false;
            try {
                FileOps.rename(r, bad);
            } catch (IOException e) {
                threw = true;
            }
            ok(threw, "Umbenennen mit ungültigem Namen '" + bad + "' abgelehnt");
        }
        File nf = FileOps.newFolder(t, "Neu");
        ok(nf.isDirectory(), "neuer Ordner");
        threw = false;
        try {
            FileOps.newFolder(t, "Neu");
        } catch (IOException e) {
            threw = true;
        }
        ok(threw, "doppelter Ordnername abgelehnt");
        // kaputte Verknüpfung blockiert den Namen
        try {
            Files.createSymbolicLink(new File(t, "kaputt").toPath(), new File(t, "gibtsnicht").toPath());
            threw = false;
            try {
                FileOps.newFolder(t, "kaputt");
            } catch (IOException e) {
                threw = true;
            }
            ok(threw, "Name einer kaputten Verknüpfung gilt als vergeben");
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    static void zip(File w) throws Exception {
        File t = dir(w, "zip");
        File src = dir(t, "Quelle");
        file(src, "a.txt", "hallo");
        file(src, "Unter/b.txt", "welt");
        File outside = dir(t, "draussen");
        file(outside, "geheim.txt", "nein");
        try {
            Files.createSymbolicLink(new File(src, "verweis").toPath(), outside.toPath());
        } catch (IOException | UnsupportedOperationException ignored) {
        }
        File out = dir(t, "out");
        File z = FileOps.zip(src, out);
        int entries = 0;
        boolean hasB = false, leak = false;
        try (ZipInputStream zi = new ZipInputStream(new FileInputStream(z))) {
            ZipEntry e;
            while ((e = zi.getNextEntry()) != null) {
                entries++;
                if (e.getName().equals("Quelle/Unter/b.txt")) {
                    hasB = true;
                }
                if (e.getName().contains("geheim")) {
                    leak = true;
                }
            }
        }
        ok(hasB && entries == 4 && !leak, "zip: Inhalt stimmt, Verknüpfungen nicht verfolgt (" + entries + " Einträge)");
        ok(!hasTemp(out), "zip: keine Temporärdatei übrig");
        long[] m = FileOps.measure(src);
        ok(m[0] == 9 && m[1] == 2, "messen: " + m[0] + " Bytes, " + m[1] + " Dateien (Verknüpfung zählt nicht)");
    }
}
