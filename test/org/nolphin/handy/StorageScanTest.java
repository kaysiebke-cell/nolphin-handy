package org.nolphin.handy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Prüft die Speicheranalyse (StorageScan) am echten Dateisystem. */
public final class StorageScanTest {
    static int fails, checks;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    static void make(File f, int bytes) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), new byte[bytes]);
    }

    static void rmrf(File f) {
        File[] l = f.listFiles();
        if (l != null && !Files.isSymbolicLink(f.toPath())) {
            for (File c : l) {
                rmrf(c);
            }
        }
        f.setReadable(true);
        f.setExecutable(true);
        f.delete();
    }

    public static void main(String[] a) throws Exception {
        File w = Files.createTempDirectory("scan").toFile();
        try {
            run(w);
        } finally {
            rmrf(w);
        }
        System.out.println(checks - fails + " von " + checks + " Prüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void run(File w) throws Exception {
        File r = new File(w, "root");
        make(new File(r, "DCIM/a.jpg"), 1000);
        make(new File(r, "DCIM/Kamera/b.JPEG"), 2000);
        make(new File(r, "Movies/v.mp4"), 5000);
        make(new File(r, "Music/s.mp3"), 700);
        make(new File(r, "Documents/d.pdf"), 300);
        make(new File(r, "Android/data/app/x.jpg"), 4000);
        make(new File(r, "Android/obb/y.obb"), 100);
        make(new File(r, "Download/setup.apk"), 900);
        make(new File(r, "Download/noext"), 50);
        make(new File(r, "lose.txt"), 10);
        new File(r, "Leer").mkdirs();

        StorageScan.Result res = StorageScan.scan(r, null, null);
        long total = 1000 + 2000 + 5000 + 700 + 300 + 4000 + 100 + 900 + 50 + 10;
        ok(res.bytes == total, "Summe: " + res.bytes + " statt " + total);
        ok(res.files == 10, "Dateizahl: " + res.files);
        ok(res.byCat[StorageScan.PICTURES] == 3000, "Bilder (Endung auch groß): " + res.byCat[0]);
        ok(res.byCat[StorageScan.VIDEOS] == 5000, "Videos");
        ok(res.byCat[StorageScan.AUDIO] == 700, "Audio");
        ok(res.byCat[StorageScan.DOCUMENTS] == 310, "Dokumente (auch lose Datei im Wurzelordner): " + res.byCat[3]);
        ok(res.byCat[StorageScan.APPS] == 4000 + 100 + 900, "Apps = Android-Ordner + apk: " + res.byCat[4]);
        ok(res.byCat[StorageScan.OTHER] == 50, "Sonstiges (ohne Endung)");
        long sum = 0;
        for (long v : res.byCat) {
            sum += v;
        }
        ok(sum == res.bytes, "Gruppen ergeben zusammen die Summe");

        ok(res.topFolders.get(0).file.getName().equals("Movies") && res.topFolders.get(0).bytes == 5000
                || res.topFolders.get(0).file.getName().equals("Android"), "größter Ordner vorn");
        ok(res.topFolders.get(0).file.getName().equals("Movies"), "Reihenfolge: Movies (5000) vor Android (4100): " + res.topFolders.get(0).file.getName());
        ok(res.topFolders.get(1).file.getName().equals("Android") && res.topFolders.get(1).bytes == 4100, "Android 4100");
        ok(res.topFolders.get(2).file.getName().equals("DCIM") && res.topFolders.get(2).bytes == 3000, "DCIM 3000");
        for (int i = 1; i < res.topFolders.size(); i++) {
            ok(res.topFolders.get(i - 1).bytes >= res.topFolders.get(i).bytes, "Ordner absteigend sortiert " + i);
        }
        ok(res.topFiles.size() == StorageScan.TOP_FILES, "höchstens " + StorageScan.TOP_FILES + " größte Dateien: " + res.topFiles.size());
        ok(res.topFiles.get(0).bytes == 5000 && res.topFiles.get(0).file.getName().equals("v.mp4"), "größte Datei vorn");
        for (int i = 1; i < res.topFiles.size(); i++) {
            ok(res.topFiles.get(i - 1).bytes >= res.topFiles.get(i).bytes, "Dateien absteigend sortiert " + i);
        }
        ok(!res.cancelled && res.finishedAt > 0 && res.unreadable == 0, "nicht abgebrochen, Zeit gesetzt, nichts unlesbar");

        // Verknüpfungen werden nie verfolgt (auch nicht als Schleife)
        File l = new File(w, "links");
        make(new File(l, "ordner/f.bin"), 111);
        Files.createSymbolicLink(new File(l, "ordner/schleife").toPath(), l.toPath());
        Files.createSymbolicLink(new File(l, "ziel").toPath(), r.toPath());
        Files.createSymbolicLink(new File(l, "dateilink").toPath(), new File(r, "Movies/v.mp4").toPath());
        StorageScan.Result lr = StorageScan.scan(l, null, null);
        ok(lr.bytes == 111 && lr.files == 1, "Verknüpfungen nicht verfolgt, keine Endlosschleife: " + lr.bytes + "/" + lr.files);

        // Leerer und fehlender Ordner
        File empty = new File(w, "leer");
        empty.mkdirs();
        StorageScan.Result er = StorageScan.scan(empty, null, null);
        ok(er.bytes == 0 && er.files == 0 && er.topFolders.isEmpty() && er.topFiles.isEmpty(), "leerer Speicher");
        StorageScan.Result nr = StorageScan.scan(new File(w, "gibtesnicht"), null, null);
        ok(nr.unreadable == 1 && nr.bytes == 0, "fehlender Ordner wird als unlesbar gezählt");

        // Nicht lesbarer Unterordner: Rest wird gezählt, Fehler gezählt
        File u = new File(w, "unlesbar");
        make(new File(u, "gut/a.bin"), 10);
        make(new File(u, "gesperrt/b.bin"), 20);
        new File(u, "gesperrt").setReadable(false);
        new File(u, "gesperrt").setExecutable(false);
        StorageScan.Result ur = StorageScan.scan(u, null, null);
        boolean root = new File(u, "gesperrt").canRead();
        if (!root) {
            ok(ur.bytes == 10 && ur.unreadable >= 1, "nicht lesbarer Ordner: Rest gezählt, Fehler gezählt: " + ur.bytes + "/" + ur.unreadable);
        }

        // Abbruch
        File big = new File(w, "viele");
        for (int i = 0; i < 20; i++) {
            make(new File(big, "o" + i + "/f.bin"), 5);
        }
        final AtomicBoolean cancel = new AtomicBoolean(true);
        StorageScan.Result cr = StorageScan.scan(big, null, cancel);
        ok(cr.cancelled && cr.files == 0, "Abbruch vor dem Start: nichts gezählt, cancelled");
        final AtomicBoolean c2 = new AtomicBoolean(false);
        final AtomicInteger calls = new AtomicInteger();
        File viele = new File(w, "sehrviele");
        for (int i = 0; i < 600; i++) {
            make(new File(viele, "d/f" + i + ".bin"), 1);
        }
        StorageScan.Result pr = StorageScan.scan(viele, new StorageScan.Progress() {
            @Override
            public void update(long files, long bytes) {
                calls.incrementAndGet();
                if (files >= 256) {
                    c2.set(true);
                }
            }
        }, c2);
        ok(calls.get() >= 2, "Fortschritt wird gemeldet: " + calls.get());
        ok(pr.cancelled && pr.files < 600, "Abbruch mittendrin: " + pr.files + " von 600, cancelled=" + pr.cancelled);

        // Gruppenzuordnung einzeln
        ok(StorageScan.category("x.PNG", false) == StorageScan.PICTURES, "PNG groß");
        ok(StorageScan.category("x.flac", false) == StorageScan.AUDIO, "flac");
        ok(StorageScan.category("x.mkv", false) == StorageScan.VIDEOS, "mkv");
        ok(StorageScan.category("x.docx", false) == StorageScan.DOCUMENTS, "docx");
        ok(StorageScan.category("x.jpg", true) == StorageScan.APPS, "in App-Daten gilt alles als Apps");
        ok(StorageScan.category(".versteckt", false) == StorageScan.OTHER || StorageScan.category(".versteckt", false) == StorageScan.OTHER, "Punktdatei ohne Endung");
        ok(StorageScan.category("ende.", false) == StorageScan.OTHER, "Name endet mit Punkt");
    }
}
