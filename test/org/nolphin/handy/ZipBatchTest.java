package org.nolphin.handy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Prüft das sichere Entpacken, das Packen und die Stapelumbenennung (ohne Handy). */
public class ZipBatchTest {
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

    static File makeZip(File where, String name, String[][] entries) throws IOException {
        File z = new File(where, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(z))) {
            for (String[] e : entries) {
                boolean isDir = e[0].endsWith("/");
                out.putNextEntry(new ZipEntry(e[0]));
                if (!isDir) {
                    out.write(e[1].getBytes("UTF-8"));
                }
                out.closeEntry();
            }
        }
        return z;
    }

    static boolean hasTemp(File d) {
        File[] fs = d.listFiles();
        if (fs == null) {
            return false;
        }
        for (File f : fs) {
            if (f.getName().startsWith(".nolphin-")) {
                return true;
            }
            if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory() && hasTemp(f)) {
                return true;
            }
        }
        return false;
    }

    static void deleteAll(File f) {
        File[] fs = f.listFiles();
        if (fs != null && !Files.isSymbolicLink(f.toPath())) {
            for (File c : fs) {
                deleteAll(c);
            }
        }
        f.delete();
    }

    public static void main(String[] a) throws Exception {
        File w = Files.createTempDirectory("zipbatch").toFile();
        try {
            entpacken(w);
            boese(w);
            packen(w);
            umbenennen(w);
            planung(w);
        } finally {
            deleteAll(w);
        }
        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void entpacken(File w) throws Exception {
        File t = dir(w, "entpacken");
        File zip = makeZip(t, "Fotos.zip", new String[][] {
            { "Fotos/", "" }, { "Fotos/a.txt", "AAA" }, { "Fotos/Unter/", "" }, { "Fotos/Unter/b.txt", "BB" },
            { "Fotos/Größe äöü.txt", "uml" }, { "Fotos/Leer/", "" } });
        ZipOps.Info info = ZipOps.inspect(zip);
        ok(info.files == 3 && info.folders == 3 && info.uncompressed == 3 + 2 + 3 && info.unsafe.isEmpty(), "inspect: Dateien, Ordner, Größe: " + info.files + "/" + info.folders + "/" + info.uncompressed);
        File dest = new File(t, "Ziel");
        FileOps.Result r = ZipOps.unzip(zip, dest, FileOps.Conflict.RENAME, Long.MAX_VALUE, null);
        ok(r.ok() && read(new File(dest, "Fotos/a.txt")).equals("AAA") && read(new File(dest, "Fotos/Unter/b.txt")).equals("BB")
                && read(new File(dest, "Fotos/Größe äöü.txt")).equals("uml") && new File(dest, "Fotos/Leer").isDirectory(),
                "entpacken: Inhalt, Unterordner, Umlaute, leerer Ordner");
        ok(!hasTemp(dest), "entpacken: keine Temporärdatei übrig");

        // Konflikte beim zweiten Entpacken in denselben Ordner
        r = ZipOps.unzip(zip, dest, FileOps.Conflict.RENAME, Long.MAX_VALUE, null);
        ok(r.ok() && read(new File(dest, "Fotos/a (2).txt")).equals("AAA") && read(new File(dest, "Fotos/a.txt")).equals("AAA"), "Konflikt umbenennen: a (2).txt");
        file(dest, "Fotos/a.txt", "ALT");
        r = ZipOps.unzip(zip, dest, FileOps.Conflict.SKIP, Long.MAX_VALUE, null);
        ok(r.ok() && read(new File(dest, "Fotos/a.txt")).equals("ALT") && !r.skipped.isEmpty(), "Konflikt überspringen: vorhandene Datei bleibt");
        r = ZipOps.unzip(zip, dest, FileOps.Conflict.REPLACE, Long.MAX_VALUE, null);
        ok(r.ok() && read(new File(dest, "Fotos/a.txt")).equals("AAA"), "Konflikt ersetzen: Datei überschrieben");

        // Speichergrenze
        File small = dir(t, "klein");
        r = ZipOps.unzip(zip, small, FileOps.Conflict.RENAME, 4, null);
        ok(!r.ok() && !r.failed.isEmpty() && !hasTemp(small), "Speichergrenze: Fehler gemeldet, keine halbe Datei");

        // Abbruch bei großem Archiv
        File big = new File(t, "gross.bin");
        byte[] block = new byte[1024 * 1024];
        try (OutputStream o = new FileOutputStream(big)) {
            for (int i = 0; i < 24; i++) {
                o.write(block);
            }
        }
        File bigZip = new File(t, "gross.zip");
        try (ZipOutputStream o = new ZipOutputStream(new FileOutputStream(bigZip))) {
            o.setLevel(0);
            o.putNextEntry(new ZipEntry("gross.bin"));
            Files.copy(big.toPath(), o);
            o.closeEntry();
        }
        final int[] calls = { 0 };
        File cancelDir = new File(t, "abbruch");
        r = ZipOps.unzip(bigZip, cancelDir, FileOps.Conflict.RENAME, Long.MAX_VALUE, new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return calls[0] >= 10;
            }

            @Override
            public void update(String name, long done, long total, int idx, int cnt) {
                calls[0]++;
            }
        });
        ok(r.cancelled && !new File(cancelDir, "gross.bin").exists() && !hasTemp(cancelDir), "Abbruch: nichts Halbes im Zielordner");
        long[] last = { 0, 0 };
        r = ZipOps.unzip(zip, dir(t, "fortschritt"), FileOps.Conflict.RENAME, Long.MAX_VALUE, new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return false;
            }

            @Override
            public void update(String name, long done, long total, int idx, int cnt) {
                last[0] = idx;
                last[1] = cnt;
            }
        });
        ok(r.ok() && last[0] == 3 && last[1] == 3, "Fortschritt: Dateizähler " + last[0] + " von " + last[1]);
        ok(!ZipOps.unzip(new File(t, "gibtsnicht.zip"), dir(t, "x"), FileOps.Conflict.RENAME, Long.MAX_VALUE, null).ok(), "fehlendes Archiv wird gemeldet");
        File notZip = file(t, "kein.zip", "das ist kein zip");
        ok(!ZipOps.unzip(notZip, dir(t, "y"), FileOps.Conflict.RENAME, Long.MAX_VALUE, null).ok(), "beschädigtes Archiv wird gemeldet");
    }

    static void boese(File w) throws Exception {
        File t = dir(w, "boese");
        File out = dir(t, "ziel");
        File zip = makeZip(t, "evil.zip", new String[][] {
            { "gut.txt", "gut" }, { "../evil1.txt", "x" }, { "a/../../evil2.txt", "x" }, { "/abs.txt", "x" }, { "C:/win.txt", "x" },
            { "..\\evil3.txt", "x" }, { "sub/ok.txt", "ok" } });
        ZipOps.Info info = ZipOps.inspect(zip);
        ok(info.unsafe.size() == 5 && info.files == 2, "inspect: 5 unsichere Einträge erkannt (" + info.unsafe.size() + ")");
        FileOps.Result r = ZipOps.unzip(zip, out, FileOps.Conflict.RENAME, Long.MAX_VALUE, null);
        ok(!r.ok() && r.failed.size() == 5, "entpacken: genau 5 Einträge abgelehnt (" + r.failed.size() + ")");
        ok(read(new File(out, "gut.txt")).equals("gut") && read(new File(out, "sub/ok.txt")).equals("ok"), "sichere Einträge wurden entpackt");
        ok(!new File(t, "evil1.txt").exists() && !new File(w, "evil1.txt").exists() && !new File(t, "evil2.txt").exists()
                && !new File(t, "evil3.txt").exists() && !new File("/abs.txt").exists(), "nichts außerhalb des Zielordners");
        ok(ZipOps.unsafeReason("a/b/c.txt") == null && ZipOps.unsafeReason("..hidden/x") == null && ZipOps.unsafeReason("a..b/c") == null,
                "harmlose Namen mit Punkten werden nicht abgelehnt");
        ok(ZipOps.unsafeReason("a/../b") != null && ZipOps.unsafeReason("..") != null && ZipOps.unsafeReason("") != null, "Pfade mit .. oder leer werden abgelehnt");

        // Weg über eine Verknüpfung im Zielordner
        File outside = dir(t, "draussen");
        File out2 = dir(t, "ziel2");
        try {
            Files.createSymbolicLink(new File(out2, "link").toPath(), outside.toPath());
            File z2 = makeZip(t, "link.zip", new String[][] { { "link/x.txt", "x" }, { "normal.txt", "n" } });
            r = ZipOps.unzip(z2, out2, FileOps.Conflict.RENAME, Long.MAX_VALUE, null);
            ok(!new File(outside, "x.txt").exists() && read(new File(out2, "normal.txt")).equals("n") && !r.failed.isEmpty(),
                    "Weg über eine Verknüpfung wird verweigert, Rest wird entpackt");
        } catch (IOException | UnsupportedOperationException e) {
            ok(true, "Verknüpfungen nicht unterstützt (übersprungen)");
        }
    }

    static void packen(File w) throws Exception {
        File t = dir(w, "packen");
        File a = file(t, "Ordner/a.txt", "A");
        file(t, "Ordner/Unter/b.txt", "BB");
        File single = file(t, "einzel.txt", "E");
        FileOps.Result r = ZipOps.zipItems(list(new File(t, "Ordner"), single), t, "Archiv.zip", null);
        ok(r.ok() && new File(t, "Archiv.zip").isFile(), "packen: ZIP angelegt");
        ZipOps.Info info = ZipOps.inspect(new File(t, "Archiv.zip"));
        ok(info.files == 3 && info.uncompressed == 4, "packen: Inhalt " + info.files + " Dateien, " + info.uncompressed + " Bytes");
        r = ZipOps.zipItems(list(single), t, "Archiv.zip", null);
        ok(r.ok() && new File(t, "Archiv (2).zip").isFile() && new File(t, "Archiv.zip").isFile(), "packen: vorhandenes Archiv wird nicht überschrieben");
        ok(!hasTemp(t), "packen: keine Temporärdatei übrig");
        // Rundreise
        File back = dir(t, "zurueck");
        r = ZipOps.unzip(new File(t, "Archiv.zip"), back, FileOps.Conflict.RENAME, Long.MAX_VALUE, null);
        ok(r.ok() && read(new File(back, "Ordner/Unter/b.txt")).equals("BB") && read(new File(back, "einzel.txt")).equals("E"), "Rundreise: packen und entpacken ergibt dieselben Dateien");
        // Abbruch beim Packen
        File big = new File(t, "gross.bin");
        byte[] block = new byte[1024 * 1024];
        try (OutputStream o = new FileOutputStream(big)) {
            for (int i = 0; i < 24; i++) {
                o.write(block);
            }
        }
        final int[] calls = { 0 };
        File od = dir(t, "abbruch");
        r = ZipOps.zipItems(list(big), od, "gross.zip", new FileOps.Progress() {
            @Override
            public boolean cancelled() {
                return calls[0] >= 10;
            }

            @Override
            public void update(String name, long done, long total, int idx, int cnt) {
                calls[0]++;
            }
        });
        ok(r.cancelled && od.list().length == 0, "Abbruch beim Packen: keine halbe ZIP-Datei (" + Arrays.toString(od.list()) + ")");
        // Verknüpfungen werden nicht gepackt
        File lk = dir(t, "mitlink");
        file(lk, "echt.txt", "e");
        File secret = dir(t, "geheim");
        file(secret, "pw.txt", "pw");
        try {
            Files.createSymbolicLink(new File(lk, "verweis").toPath(), secret.toPath());
            r = ZipOps.zipItems(list(lk), t, "mitlink.zip", null);
            ZipOps.Info i2 = ZipOps.inspect(new File(t, "mitlink.zip"));
            boolean leak = false;
            for (String nme : i2.names) {
                if (nme.contains("pw.txt")) {
                    leak = true;
                }
            }
            ok(r.ok() && !leak && i2.files == 1, "packen: Verknüpfung wird nicht verfolgt (" + i2.files + " Datei)");
        } catch (IOException | UnsupportedOperationException e) {
            ok(true, "Verknüpfungen nicht unterstützt (übersprungen)");
        }
    }

    static BatchRename.Options opts(BatchRename.Mode m) {
        BatchRename.Options o = new BatchRename.Options();
        o.mode = m;
        return o;
    }

    static void umbenennen(File w) throws Exception {
        BatchRename.Options o = opts(BatchRename.Mode.REPLACE);
        o.find = "IMG_";
        o.replacement = "Foto";
        ok(BatchRename.newName("IMG_4821.jpg", o, 0).equals("Foto4821.jpg"), "Ersetzen");
        o.find = "";
        ok(BatchRename.newName("a.txt", o, 0).equals("a.txt"), "Ersetzen mit leerem Suchtext ändert nichts");
        o = opts(BatchRename.Mode.NUMBER);
        o.prefix = "Urlaub - ";
        o.start = 1;
        o.digits = 3;
        ok(BatchRename.newName("IMG_1.jpg", o, 0).equals("Urlaub - 001.jpg") && BatchRename.newName("x.PNG", o, 11).equals("Urlaub - 012.PNG"), "Nummerieren mit Endung und Stellen");
        o.digits = 1;
        o.start = 98;
        ok(BatchRename.newName("a", o, 3).equals("Urlaub - 101"), "Nummerieren: Zahl länger als die Stellen, Name ohne Endung");
        o = opts(BatchRename.Mode.AFFIX);
        o.prefix = "2026_";
        o.suffix = "_neu";
        ok(BatchRename.newName("Brief.docx", o, 0).equals("2026_Brief_neu.docx") && BatchRename.newName(".bashrc", o, 0).equals("2026_.bashrc_neu"), "Anhängen vorn und hinten, vor der Endung");
        o = opts(BatchRename.Mode.CASE);
        o.caseMode = BatchRename.CaseMode.UPPER;
        ok(BatchRename.newName("Foto.jpg", o, 0).equals("FOTO.jpg"), "GROSS: Endung bleibt");
        o.caseMode = BatchRename.CaseMode.LOWER;
        ok(BatchRename.newName("FOTO.JPG", o, 0).equals("foto.JPG"), "klein: Endung bleibt");
        o.caseMode = BatchRename.CaseMode.FIRST;
        ok(BatchRename.newName("mEIN fOTO.jpg", o, 0).equals("Mein foto.jpg"), "Erster Buchstabe groß");
        ok(BatchRename.invalidReason("").length() > 0 && BatchRename.invalidReason("a/b") != null && BatchRename.invalidReason("..") != null
                && BatchRename.invalidReason("ok.txt") == null, "ungültige Namen");
        StringBuilder lang = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            lang.append('x');
        }
        ok(BatchRename.invalidReason(lang.toString()) != null, "zu langer Name");
    }

    static void planung(File w) throws Exception {
        File t = dir(w, "plan");
        File a = file(t, "a.txt", "A");
        File b = file(t, "b.txt", "B");
        File c = file(t, "c.txt", "C");
        // Nummerieren und ausführen
        BatchRename.Options o = opts(BatchRename.Mode.NUMBER);
        o.prefix = "Neu ";
        o.digits = 2;
        BatchRename.Plan p = BatchRename.plan(list(a, b, c), o);
        ok(p.ready() && p.changes() == 3 && p.items.get(2).newName.equals("Neu 03.txt"), "Plan: Nummerierung bereit");
        FileOps.Result r = BatchRename.apply(p, null);
        ok(r.ok() && read(new File(t, "Neu 01.txt")).equals("A") && read(new File(t, "Neu 03.txt")).equals("C") && !a.exists(), "ausführen: umbenannt, Inhalt gehört zum richtigen Namen");
        ok(!hasTemp(t), "ausführen: keine Zwischendatei übrig");

        // Tausch: a wird b, b wird a
        File x = file(t, "x.txt", "X");
        File y = file(t, "y.txt", "Y");
        BatchRename.Plan swap = new BatchRename.Plan();
        swap.items.add(new BatchRename.Item(x, "y.txt"));
        swap.items.add(new BatchRename.Item(y, "x.txt"));
        ok(!swap.hasProblem(), "Tausch: wird als gültig angesehen (Namen gehören zur Auswahl)");
        BatchRename.Plan swapPlan = BatchRename.plan(list(x, y), opts(BatchRename.Mode.REPLACE));
        BatchRename.Options sw = opts(BatchRename.Mode.REPLACE);
        // Tausch über echte Berechnung: x -> y und y -> x per Ersetzen geht nicht; deshalb direkter Plan
        r = BatchRename.apply(swap, null);
        ok(r.ok() && read(new File(t, "x.txt")).equals("Y") && read(new File(t, "y.txt")).equals("X"), "Tausch wird über Zwischennamen gelöst, nichts geht verloren");

        // Doppelte neue Namen
        File d1 = file(t, "d1.txt", "1");
        File d2 = file(t, "d2.txt", "2");
        BatchRename.Options r2 = opts(BatchRename.Mode.REPLACE);
        r2.find = "1";
        r2.replacement = "";
        BatchRename.Options r3 = opts(BatchRename.Mode.REPLACE);
        r3.find = "d";
        r3.replacement = "e";
        File e1 = file(t, "ee.txt", "x");
        BatchRename.Plan dup = BatchRename.plan(list(file(t, "p1.txt", "1"), file(t, "p2.txt", "2")), opts2("p", "q"));
        ok(dup.ready(), "Plan ohne Konflikt ist bereit");
        File g1 = file(t, "g_1.txt", "1");
        File g2 = file(t, "g1.txt", "2");
        BatchRename.Options gl = opts(BatchRename.Mode.REPLACE);
        gl.find = "_";
        gl.replacement = "";
        BatchRename.Plan clash = BatchRename.plan(list(g1, g2), gl);
        ok(clash.hasProblem() && clash.firstProblem().contains("g1.txt") && !clash.ready(), "zwei gleiche neue Namen: Problem");
        FileOps.Result refused = BatchRename.apply(clash, null);
        ok(!refused.ok() && g1.exists() && g2.exists() && read(g1).equals("1"), "Plan mit Problem wird nicht ausgeführt, nichts verändert");

        // Name gibt es schon (nicht Teil der Auswahl)
        File h1 = file(t, "h_alt.txt", "alt");
        File hexist = file(t, "h.txt", "bleibt");
        BatchRename.Options ho = opts(BatchRename.Mode.REPLACE);
        ho.find = "_alt";
        ho.replacement = "";
        BatchRename.Plan exist = BatchRename.plan(list(h1), ho);
        ok(exist.hasProblem() && exist.firstProblem().contains("gibt es hier schon"), "vorhandener Name außerhalb der Auswahl: Problem");
        ok(read(hexist).equals("bleibt") && h1.exists(), "vorhandene Datei bleibt unberührt");

        // Groß-/Kleinschreibung gilt als gleicher Name (wegen SD-Karten)
        File k1 = file(t, "Kuchen.txt", "1");
        File k2 = file(t, "kuchen2.txt", "2");
        BatchRename.Options ko = opts(BatchRename.Mode.REPLACE);
        ko.find = "2";
        ko.replacement = "";
        BatchRename.Plan caseClash = BatchRename.plan(list(k2), ko);
        ok(caseClash.hasProblem(), "gleicher Name bis auf Groß- und Kleinschreibung: Problem");

        // Änderung nur der Schreibweise derselben Datei ist erlaubt
        File m = file(t, "Mein.TXT", "m");
        BatchRename.Options mo = opts(BatchRename.Mode.CASE);
        mo.caseMode = BatchRename.CaseMode.LOWER;
        BatchRename.Plan self = BatchRename.plan(list(m), mo);
        ok(!self.hasProblem() && self.changes() == 1, "Schreibweise der eigenen Datei ändern ist kein Konflikt");
        r = BatchRename.apply(self, null);
        ok(r.ok() && read(new File(t, "mein.TXT")).equals("m"), "Schreibweise ändern wird ausgeführt");

        // Nichts zu tun
        BatchRename.Options none = opts(BatchRename.Mode.REPLACE);
        none.find = "gibtsnicht";
        BatchRename.Plan nothing = BatchRename.plan(list(file(t, "n.txt", "n")), none);
        ok(!nothing.ready() && !nothing.hasProblem() && nothing.changes() == 0, "nichts zu ändern: Knopf bleibt gesperrt, kein Problem");
    }

    static BatchRename.Options opts2(String find, String repl) {
        BatchRename.Options o = opts(BatchRename.Mode.REPLACE);
        o.find = find;
        o.replacement = repl;
        return o;
    }
}
