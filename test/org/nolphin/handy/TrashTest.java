package org.nolphin.handy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Prüft den Papierkorb (TrashOps) am echten Dateisystem. */
public final class TrashTest {
    static int fails, checks;

    static void ok(boolean c, String what) {
        checks++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    static File make(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes("UTF-8"));
        return f;
    }

    static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), "UTF-8");
    }

    static void rmrf(File f) {
        if (!Files.isSymbolicLink(f.toPath())) {
            f.setReadable(true);
            f.setWritable(true);
            f.setExecutable(true);
            File[] l = f.listFiles();
            if (l != null) {
                for (File c : l) {
                    rmrf(c);
                }
            }
        }
        f.delete();
    }

    static List<File> L(File... f) {
        return new ArrayList<>(Arrays.asList(f));
    }

    static List<TrashOps.Entry> E(TrashOps.Entry... e) {
        return new ArrayList<>(Arrays.asList(e));
    }

    public static void main(String[] a) throws Exception {
        File w = Files.createTempDirectory("trash").toFile();
        try {
            run(w);
        } finally {
            rmrf(w);
        }
        System.out.println(checks - fails + " von " + checks + " Prüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void run(File w) throws Exception {
        File intern = new File(w, "intern");
        File sd = new File(w, "sd");
        intern.mkdirs();
        sd.mkdirs();
        List<File> roots = L(intern, sd);

        // Datei, Ordner mit Inhalt, Verknüpfung, Name mit Umlauten/Leerzeichen
        File datei = make(new File(intern, "Doku/Rechnung ä ö.pdf"), "PDF-Inhalt");
        File ordner = new File(intern, "Fotos");
        make(new File(ordner, "a.jpg"), "AAA");
        make(new File(ordner, "Unter/b.jpg"), "BBBB");
        File ziel = make(new File(intern, "ziel.txt"), "Z");
        File link = new File(intern, "link");
        Files.createSymbolicLink(link.toPath(), ziel.toPath());
        File sdDatei = make(new File(sd, "Musik/lied.mp3"), "MP3");

        FileOps.Result r = TrashOps.moveToTrash(L(datei, ordner, link, sdDatei), roots, null);
        ok(r.ok() && r.done.size() == 4, "4 Elemente in den Papierkorb: " + r.done + r.failed);
        ok(!datei.exists() && !ordner.exists() && !Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) && !sdDatei.exists(), "Originale sind weg");
        ok(ziel.exists() && read(ziel).equals("Z"), "Ziel der Verknüpfung blieb unberührt");
        ok(new File(TrashOps.trashDir(intern), ".nomedia").exists(), ".nomedia im Papierkorb");
        ok(TrashOps.trashDir(sd).isDirectory() && !new File(TrashOps.trashDir(intern), "lied.mp3").exists(), "jeder Speicher hat seinen eigenen Papierkorb");

        List<TrashOps.Entry> list = TrashOps.list(roots);
        ok(list.size() == 4, "Liste: 4 Einträge (" + list.size() + ")");
        TrashOps.Entry eDatei = null, eOrdner = null, eLink = null, eSd = null;
        for (TrashOps.Entry e : list) {
            if (e.name.equals("Rechnung ä ö.pdf")) eDatei = e;
            if (e.name.equals("Fotos")) eOrdner = e;
            if (e.name.equals("link")) eLink = e;
            if (e.name.equals("lied.mp3")) eSd = e;
        }
        ok(eDatei != null && eDatei.origPath.equals(datei.getPath()), "alter Pfad gemerkt (mit Umlauten und Leerzeichen)");
        ok(eOrdner != null && eOrdner.dir && eOrdner.origPath.equals(ordner.getPath()), "Ordner erkannt");
        ok(eLink != null && Files.isSymbolicLink(eLink.stored.toPath()), "Verknüpfung blieb Verknüpfung");
        ok(eSd != null && eSd.stored.getPath().startsWith(TrashOps.trashDir(sd).getPath()), "SD-Eintrag liegt im Papierkorb der SD-Karte");
        ok(read(eDatei.stored).equals("PDF-Inhalt") && read(new File(eOrdner.stored, "Unter/b.jpg")).equals("BBBB"), "Inhalt unverändert");
        long[] s = TrashOps.summary(roots);
        ok(s[0] == 4 && s[1] == 10 + 3 + 4 + 3, "Summe: " + s[0] + " Elemente, " + s[1] + " Bytes");

        // Zurückholen: alter Ort; bei Namensgleichheit (2); nie überschreiben
        FileOps.Result rr = TrashOps.restore(E(eDatei), null);
        ok(rr.ok() && datei.exists() && read(datei).equals("PDF-Inhalt"), "Datei zurückgeholt an den alten Ort");
        make(new File(intern, "Fotos/neu.txt"), "NEU");
        rr = TrashOps.restore(E(eOrdner), null);
        ok(rr.ok() && new File(intern, "Fotos (2)/a.jpg").exists() && read(new File(intern, "Fotos/neu.txt")).equals("NEU"), "Namensgleichheit: zurückgeholter Ordner heißt (2), nichts überschrieben");
        ok(TrashOps.list(roots).size() == 2, "zurückgeholte Elemente sind aus der Liste");
        ok(!eDatei.box.exists() && !eDatei.info.exists(), "Fach und Infodatei aufgeräumt");

        // alter Ordner gelöscht: wird neu angelegt
        File tiefer = make(new File(intern, "a/b/c.txt"), "C");
        TrashOps.moveToTrash(L(tiefer), roots, null);
        rmrf(new File(intern, "a"));
        TrashOps.Entry eT = null;
        for (TrashOps.Entry e : TrashOps.list(roots)) if (e.name.equals("c.txt")) eT = e;
        rr = TrashOps.restore(E(eT), null);
        ok(rr.ok() && read(tiefer).equals("C"), "fehlender alter Ordner wird neu angelegt");

        // Verknüpfung zurückholen
        TrashOps.Entry eL = null;
        for (TrashOps.Entry e : TrashOps.list(roots)) if (e.name.equals("link")) eL = e;
        rr = TrashOps.restore(E(eL), null);
        ok(rr.ok() && Files.isSymbolicLink(link.toPath()) && read(link).equals("Z"), "Verknüpfung zurückgeholt");

        // Endgültig löschen
        TrashOps.Entry eS = null;
        for (TrashOps.Entry e : TrashOps.list(roots)) if (e.name.equals("lied.mp3")) eS = e;
        File storedSd = eS.stored;
        FileOps.Result df = TrashOps.deleteForever(E(eS), null);
        ok(df.ok() && !storedSd.exists() && !eS.box.exists() && !eS.info.exists(), "endgültig gelöscht, Fach und Info weg");
        ok(TrashOps.list(roots).isEmpty(), "Papierkorb leer");

        // Ablehnungen
        File wurzel = intern;
        FileOps.Result bad = TrashOps.moveToTrash(L(wurzel), roots, null);
        ok(!bad.ok() && wurzel.isDirectory(), "Wurzel eines Speichers wird nie in den Papierkorb gelegt");
        File fremd = make(new File(w, "fremd/x.txt"), "X");
        bad = TrashOps.moveToTrash(L(fremd), roots, null);
        ok(!bad.ok() && fremd.exists(), "Datei außerhalb der Speicher: abgelehnt, Original bleibt");
        File d2 = make(new File(intern, "d2.txt"), "D2");
        TrashOps.moveToTrash(L(d2), roots, null);
        TrashOps.Entry in = TrashOps.list(roots).get(0);
        ok(TrashOps.isInTrash(in.stored, roots) && TrashOps.isInTrash(TrashOps.trashDir(intern), roots), "isInTrash erkennt Inhalt und Papierkorb");
        ok(!TrashOps.canTrashAll(L(in.stored), roots) && !TrashOps.canTrashAll(L(TrashOps.trashDir(intern)), roots), "aus dem Papierkorb heraus wird nicht erneut in den Papierkorb gelegt");
        ok(!TrashOps.canTrashAll(L(fremd), roots) && !TrashOps.canTrashAll(L(wurzel), roots) && !TrashOps.canTrashAll(new ArrayList<File>(), roots), "canTrashAll: außerhalb, Wurzel, leer = nein");
        ok(TrashOps.canTrashAll(L(link), roots), "canTrashAll: normale Datei = ja");
        File linkAufPapierkorb = new File(intern, "zumkorb");
        Files.createSymbolicLink(linkAufPapierkorb.toPath(), TrashOps.trashDir(intern).toPath());
        ok(TrashOps.canTrashAll(L(linkAufPapierkorb), roots), "Verknüpfung auf den Papierkorb darf selbst in den Papierkorb");
        TrashOps.restore(E(in), null);

        // Fehlschlag lässt das Original unberührt (kein Papierkorb anlegbar)
        File gesperrt = new File(w, "gesperrt");
        gesperrt.mkdirs();
        File gd = make(new File(gesperrt, "wichtig.txt"), "WICHTIG");
        gesperrt.setWritable(false);
        gesperrt.setExecutable(true);
        boolean cannot = !gesperrt.canWrite();
        if (cannot) {
            FileOps.Result fail = TrashOps.moveToTrash(L(gd), L(gesperrt), null);
            ok(!fail.ok() && gd.exists() && read(gd).equals("WICHTIG") && fail.retry.size() == 1, "kein Papierkorb anlegbar: Original bleibt, Fehler genannt, Wiederholen möglich");
            gesperrt.setWritable(true);
        }

        // Abbruch
        File c1 = make(new File(intern, "c1.txt"), "1");
        File c2 = make(new File(intern, "c2.txt"), "2");
        FileOps.Result cr = TrashOps.moveToTrash(L(c1, c2), roots, new FileOps.Progress() {
            @Override public boolean cancelled() { return true; }
            @Override public void update(String name, long done, long total, int fi, int fc) {}
        });
        ok(cr.cancelled && c1.exists() && c2.exists(), "Abbruch: nichts angefasst");

        // Alterung
        TrashOps.moveToTrash(L(c1, c2), roots, null);
        List<TrashOps.Entry> two = TrashOps.list(roots);
        // einen Eintrag um 40 Tage älter machen
        TrashOps.Entry old = two.get(0);
        java.util.Properties p = new java.util.Properties();
        p.setProperty("path", old.origPath);
        p.setProperty("when", Long.toString(System.currentTimeMillis() - 40L * 24 * 3600 * 1000));
        try (java.io.OutputStream o = new java.io.FileOutputStream(old.info)) {
            p.store(o, null);
        }
        int purged = TrashOps.purge(roots, TrashOps.MAX_AGE_MS, System.currentTimeMillis());
        ok(purged == 1 && TrashOps.list(roots).size() == 1, "nach 30 Tagen automatisch gelöscht, jüngeres bleibt: " + purged);

        // Absturz zwischen Verschieben und Infodatei: Fach ohne Info wird trotzdem gelistet und lässt sich zurückholen
        File vor = make(new File(intern, "weg.txt"), "WEG");
        TrashOps.moveToTrash(L(vor), roots, null);
        TrashOps.Entry weg = null;
        for (TrashOps.Entry e : TrashOps.list(roots)) if (e.name.equals("weg.txt")) weg = e;
        weg.info.delete();
        TrashOps.Entry ohneInfo = null;
        for (TrashOps.Entry e : TrashOps.list(roots)) if (e.name.equals("weg.txt")) ohneInfo = e;
        ok(ohneInfo != null && ohneInfo.origPath.equals(new File(intern, "weg.txt").getPath()), "ohne Infodatei: Rückkehr an die Wurzel des Speichers");
        rr = TrashOps.restore(E(ohneInfo), null);
        ok(rr.ok() && read(new File(intern, "weg.txt")).equals("WEG"), "Fach ohne Info lässt sich zurückholen");

        // kaputte Infodatei
        File k = make(new File(intern, "kaputt.txt"), "K");
        TrashOps.moveToTrash(L(k), roots, null);
        TrashOps.Entry ke = null;
        for (TrashOps.Entry e : TrashOps.list(roots)) if (e.name.equals("kaputt.txt")) ke = e;
        Files.write(ke.info.toPath(), new byte[] { 0x00, 'x', '\\', 'u', 'Z' });
        ok(TrashOps.list(roots).size() >= 1, "kaputte Infodatei bringt die Liste nicht zum Absturz");

        // Alter als Text
        long now = 1_000_000_000_000L;
        ok(TrashOps.age(now, now).equals("gerade eben"), "age: gerade eben");
        ok(TrashOps.age(now - 60_000, now).equals("vor 1 Minute"), "age: 1 Minute");
        ok(TrashOps.age(now - 5 * 3600_000L, now).equals("vor 5 Stunden"), "age: Stunden");
        ok(TrashOps.age(now - 24 * 3600_000L, now).equals("vor 1 Tag"), "age: 1 Tag");
        ok(TrashOps.age(now - 9 * 24 * 3600_000L, now).equals("vor 9 Tagen"), "age: Tage");

        // Fach mit zwei Dingen oder leeres Fach stört nicht
        File leerBox = new File(TrashOps.trashDir(intern), "leer-1");
        leerBox.mkdirs();
        TrashOps.list(roots);
        ok(!leerBox.exists(), "leeres Fach wird beim Auflisten aufgeräumt");
    }
}
