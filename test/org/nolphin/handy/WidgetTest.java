package org.nolphin.handy;

import java.util.List;

/** Prüft die Anzeigeregeln des Widgets und den Schutz vor Doppelstarts (ohne Handy). */
public class WidgetTest {
    static int fails = 0, n = 0;

    static void ok(boolean c, String what) {
        n++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    static WidgetModel.Input in(boolean saved, boolean running, String status) {
        WidgetModel.Input i = new WidgetModel.Input();
        i.saved = saved;
        i.running = running;
        i.status = status;
        i.pcName = "Mein-Laptop";
        i.now = 100_000;
        i.stateTime = 100_000;
        return i;
    }

    public static void main(String[] a) {
        // Zustand: gespeicherte Daten allein reichen nicht für "verbunden"
        ok(WidgetModel.mode(in(false, false, "Nicht verbunden")) == WidgetModel.Mode.NOT_CONNECTED, "ohne Daten: nicht verbunden");
        ok(WidgetModel.mode(in(false, true, "Verbunden")) == WidgetModel.Mode.NOT_CONNECTED, "Dienst läuft, aber keine Daten: nicht verbunden");
        ok(WidgetModel.mode(in(true, false, "Verbunden")) == WidgetModel.Mode.STOPPED, "Daten, aber Dienst läuft nicht (alter Status 'Verbunden'): gestoppt, nicht verbunden");
        ok(WidgetModel.mode(in(true, true, "Verbunden mit PC")) == WidgetModel.Mode.CONNECTED, "Daten und Dienst: verbunden");
        ok(WidgetModel.mode(in(true, true, "PC nicht erreichbar: Zeitüberschreitung")) == WidgetModel.Mode.ERROR, "PC nicht erreichbar: Fehler");
        ok(WidgetModel.mode(in(true, true, "Schlüssel ungültig")) == WidgetModel.Mode.ERROR, "Schlüssel ungültig: Fehler");
        ok(WidgetModel.mode(in(true, false, "PC nicht erreichbar")) == WidgetModel.Mode.STOPPED, "läuft nicht: nie Fehler- oder Verbunden-Anzeige");

        // Texte
        ok(WidgetModel.title(in(true, true, "ok")).equals("Verbunden") && WidgetModel.subtitle(in(true, true, "ok")).equals("PC: Mein-Laptop"), "Titel und PC-Name");
        ok(WidgetModel.subtitle(in(true, true, "PC nicht erreichbar: x")).equals("PC nicht erreichbar: x"), "Fehlertext wird gezeigt");
        WidgetModel.Input noName = in(true, true, "ok");
        noName.pcName = "";
        ok(WidgetModel.subtitle(noName).equals("PC: PC"), "ohne PC-Namen kein Absturz");
        ok(WidgetModel.subtitle(in(true, false, "")).contains("Dienst gestoppt"), "gestoppt erklärt");
        ok(WidgetModel.title(in(false, false, "")).equals("Nicht verbunden"), "Titel ohne Verbindung");

        // Übertragung: nur wenn Dienst läuft und Zustand frisch
        WidgetModel.Input t = in(true, true, "ok");
        t.transferName = "Plan.pdf";
        t.transferDone = 512 * 1024;
        t.transferTotal = 1024 * 1024;
        ok(WidgetModel.transferActive(t), "aktive Übertragung wird gezeigt");
        t.stateTime = t.now - WidgetModel.TRANSFER_FRESH_MS - 1;
        ok(!WidgetModel.transferActive(t), "veraltete Übertragung wird nicht gezeigt");
        t.stateTime = t.now;
        t.running = false;
        ok(!WidgetModel.transferActive(t), "ohne laufenden Dienst keine Übertragung (nach Prozessende)");
        t.running = true;
        t.transferName = "";
        ok(!WidgetModel.transferActive(t), "ohne Dateinamen keine Übertragung");

        // Prozent und Mengen
        ok(WidgetModel.percent(512, 1024) == 50 && WidgetModel.percent(0, 1024) == 0 && WidgetModel.percent(1024, 1024) == 100, "Prozent");
        ok(WidgetModel.percent(5, 0) == -1 && WidgetModel.percent(5, -1) == -1, "unbekannte Gesamtgröße: kein Prozentwert");
        ok(WidgetModel.percent(2000, 1000) == 100 && WidgetModel.percent(-5, 1000) == 0, "Prozent bleibt zwischen 0 und 100");
        ok(WidgetModel.amount(512 * 1024, 1024 * 1024).equals("512 KB von 1,0 MB"), "Menge mit Gesamtgröße: " + WidgetModel.amount(512 * 1024, 1024 * 1024));
        ok(WidgetModel.amount(2048, 0).equals("2 KB"), "Menge ohne Gesamtgröße");
        ok(WidgetModel.size(0).equals("0 B") && WidgetModel.size(1023).equals("1023 B") && WidgetModel.size(1024).equals("1 KB"), "Größen");

        // Größen und Dateizeilen
        ok(!WidgetModel.large(110, 250) && !WidgetModel.large(179, 300) && !WidgetModel.large(300, 179), "kompakt bei kleiner Höhe oder Breite");
        ok(WidgetModel.large(180, 180) && WidgetModel.large(400, 400), "erweitert ab 180 mal 180 dp");
        ok(WidgetModel.fileRows(110) == 0 && WidgetModel.fileRows(179) == 0 && WidgetModel.fileRows(180) == 2 && WidgetModel.fileRows(249) == 2 && WidgetModel.fileRows(250) == 3 && WidgetModel.fileRows(900) == 3, "Dateizeilen nach Höhe");
        WidgetModel.Input f = in(true, true, "ok");
        for (int i = 0; i < 5; i++) {
            f.files.add(new RemoteFile("Datei" + i + ".txt", 100 + i));
        }
        List<RemoteFile> shown = WidgetModel.shownFiles(f, 3);
        ok(shown.size() == 3 && shown.get(0).name.equals("Datei0.txt") && WidgetModel.hiddenFiles(f, 3) == 2, "höchstens drei Dateien, zwei weitere in der App");
        ok(WidgetModel.shownFiles(f, 0).isEmpty() && WidgetModel.hiddenFiles(f, 0) == 5, "kompakt: keine Dateien, alle in der App");
        f.running = false;
        ok(WidgetModel.shownFiles(f, 3).isEmpty() && WidgetModel.hiddenFiles(f, 3) == 0, "nicht verbunden: keine Dateiliste (kein Laden einer alten Liste)");
        WidgetModel.Input none = in(true, true, "ok");
        ok(WidgetModel.shownFiles(none, 3).isEmpty() && WidgetModel.hiddenFiles(none, 3) == 0, "keine Dateien");

        // Schutz vor Doppelstarts
        FetchGuard g = new FetchGuard(3000);
        ok(g.beginFetch("a.pdf|10"), "erster Download darf starten");
        ok(!g.beginFetch("a.pdf|10"), "dieselbe Datei nicht doppelt laden");
        ok(g.beginFetch("b.pdf|10"), "andere Datei darf parallel");
        g.endFetch("a.pdf|10");
        ok(g.beginFetch("a.pdf|10"), "nach dem Ende darf erneut geladen werden");
        ok(g.beginUpload("u1", 1000), "erster Upload");
        ok(!g.beginUpload("u1", 2000), "derselbe Upload kurz danach wird ignoriert");
        ok(g.beginUpload("u2", 2100), "ein anderer Upload ist erlaubt");
        ok(g.beginUpload("u1", 2100 + 3000), "nach Ablauf der Sperrzeit erlaubt");
        ok(g.beginUpload("", 9000) && !g.beginUpload("", 9001), "leerer Schlüssel verhält sich genauso");

        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden");
        if (fails > 0) {
            System.exit(1);
        }
    }
}
