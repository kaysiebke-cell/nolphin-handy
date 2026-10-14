package org.nolphin.handy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Prüft Ansichtsregeln (Liste, Raster, Kompakt, Vorschaugröße, Sortiermenü) und die Regeln für externe Apps (ohne Handy). */
public class ViewTest {
    static int fails = 0, n = 0;

    static void ok(boolean c, String what) {
        n++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    public static void main(String[] a) {
        // Darstellung wird aus den Einstellungen gelesen; fehlt sie, gilt die alte Einstellung
        ok(ViewModes.fromPrefs(0, true) == ViewModes.LIST, "gespeicherte Liste");
        ok(ViewModes.fromPrefs(1, false) == ViewModes.GRID, "gespeichertes Raster");
        ok(ViewModes.fromPrefs(2, true) == ViewModes.COMPACT, "gespeicherte Kompaktansicht");
        ok(ViewModes.fromPrefs(-1, true) == ViewModes.GRID && ViewModes.fromPrefs(-1, false) == ViewModes.LIST, "alte Einstellung Kacheln/Liste");
        ok(ViewModes.fromPrefs(7, true) == ViewModes.GRID, "ungültiger Wert fällt auf die alte Einstellung zurück");

        // Raster passt sich der Breite an und bleibt dicht
        ok(ViewModes.columns(360, false) == 4, "360 dp breit: 4 Spalten: " + ViewModes.columns(360, false));
        ok(ViewModes.columns(412, false) == 4 && ViewModes.columns(480, false) == 5, "breiter: mehr Spalten");
        ok(ViewModes.columns(360, true) == 2 && ViewModes.columns(600, true) == 4, "große Vorschau: größere Kacheln, weniger Spalten");
        ok(ViewModes.columns(100, true) == 1 && ViewModes.columns(0, false) == 1, "nie weniger als eine Spalte");
        for (int w = 280; w <= 900; w += 10) {
            ok(ViewModes.columns(w, true) <= ViewModes.columns(w, false), "große Vorschau hat nie mehr Spalten als die Standard-Vorschau bei " + w);
            int cols = ViewModes.columns(w, false);
            int tile = (w - 12 - (cols - 1) * 4) / cols;
            ok(tile >= ViewModes.tileWidthDp(false), "Kachel mindestens so breit wie vorgesehen bei " + w + ": " + tile);
        }

        // Vorschaugröße und Darstellung sind unabhängig
        for (int mode = 0; mode <= 2; mode++) {
            ok(ViewModes.rowIconDp(mode, true) > ViewModes.rowIconDp(mode, false), "große Vorschau macht Symbole größer, Modus " + mode);
            ok(ViewModes.rowHeightDp(mode, true) > ViewModes.rowHeightDp(mode, false), "und Zeilen höher, Modus " + mode);
        }
        ok(ViewModes.rowHeightDp(ViewModes.COMPACT, false) < ViewModes.rowHeightDp(ViewModes.LIST, false), "Kompakt ist niedriger als Liste");
        ok(ViewModes.rowIconDp(ViewModes.COMPACT, false) <= 28, "kompakte Symbole klein");
        ok(ViewModes.rowHeightDp(ViewModes.COMPACT, false) >= 48 && ViewModes.rowHeightDp(ViewModes.LIST, false) >= 56, "zwei Textzeilen passen in jede Zeile, Touch-Ziele nicht zu klein");

        // Sortierauswahl: neun Einträge, jeder rechnet hin und zurück
        boolean[] seen = new boolean[ViewModes.SORT_CHOICES];
        for (int i = 0; i < ViewModes.SORT_CHOICES; i++) {
            SortOrder o = ViewModes.sortFromIndex(i, true);
            ok(ViewModes.sortIndex(o) == i, "Eintrag " + i + " hin und zurück");
            ok(o.foldersFirst, "Ordner zuerst bleibt erhalten, Eintrag " + i);
            seen[ViewModes.sortIndex(o)] = true;
        }
        boolean all = true;
        for (boolean b : seen) {
            all &= b;
        }
        ok(all && ViewModes.SORT_CHOICES == 9, "neun verschiedene Einträge");
        ok(ViewModes.sortFromIndex(0, false).key == SortOrder.NONE && !ViewModes.sortFromIndex(0, false).foldersFirst, "Eintrag 0 ist Keine Sortierung");
        ok(ViewModes.sortFromIndex(99, true).key == SortOrder.NONE && ViewModes.sortFromIndex(-3, true).key == SortOrder.NONE, "ungültiger Eintrag gilt als Keine");
        for (int k = SortOrder.NAME; k <= SortOrder.TYPE; k++) {
            ok(ViewModes.sortIndex(new SortOrder(k, true, true)) == 1 + k * 2 && ViewModes.sortIndex(new SortOrder(k, false, true)) == 2 + k * 2,
                    "Schlüssel " + k + ": aufsteigend vor absteigend");
        }
        SortOrder std = SortOrder.standard();
        ok(ViewModes.sortIndex(std) == 2 + SortOrder.DATE * 2, "Standard im Menü: Datum absteigend");

        // Darstellungsgröße: schmale Handys bleiben, breite werden auf die Bezugsbreite 360 dp gebracht
        ok(UiScale.factor(320) == 1f && UiScale.factor(360) == 1f, "bis 360 dp unverändert");
        ok(Math.abs(UiScale.factor(393) - 393 / 360f) < 1e-6, "393 dp: Faktor 393/360");
        ok(Math.abs(393 / UiScale.factor(393) - 360) < 0.01, "danach ist die Breite 360 dp");
        ok(UiScale.factor(800) == UiScale.MAX && UiScale.factor(10000) == UiScale.MAX, "Obergrenze");
        ok(UiScale.densityDpi(440, 393) == 480 && UiScale.densityDpi(420, 360) == 420, "Dichte wird mitgerechnet: " + UiScale.densityDpi(440, 393));
        for (int w = 200; w <= 1200; w += 7) {
            float f = UiScale.factor(w);
            ok(f >= 1f && f <= UiScale.MAX, "Faktor im erlaubten Bereich bei " + w);
            ok(w <= 360 || w / f <= 360 + 0.01 || f == UiScale.MAX, "Breite nie über der Bezugsbreite, außer an der Obergrenze: " + w);
        }

        // Auswahl-Modus
        SelectionMode sm = new SelectionMode();
        ok(!sm.active(0) && !sm.taps(0), "ohne Auswahl und ohne Modus: Antippen öffnet");
        ok(sm.active(2) && sm.taps(1), "mit Auswahl: Antippen wählt");
        sm.begin();
        ok(sm.active(0) && sm.taps(0), "Modus über das Menü: schon ohne Auswahl aktiv");
        sm.update(0);
        ok(sm.active(0), "Modus bleibt, solange noch nie etwas gewählt wurde");
        sm.update(1);
        ok(sm.active(1), "nach der ersten Auswahl aktiv");
        sm.update(0);
        ok(!sm.active(0), "Modus endet von selbst, wenn nach einer Auswahl nichts mehr gewählt ist");
        sm.begin();
        sm.update(3);
        sm.end();
        ok(!sm.active(0) && sm.active(3), "✕: Modus aus; eine bestehende Auswahl bleibt eine Auswahl");
        sm.begin();
        sm.update(2);
        sm.update(0);
        sm.begin();
        ok(sm.active(0), "erneutes Starten funktioniert");

        // Ordner laden vom PC
        ok(FolderFetch.safeName("Bilder") && FolderFetch.safeName("Ärger mit Umlauten.txt") && FolderFetch.safeName(".versteckt"), "normale Namen sind sicher");
        ok(!FolderFetch.safeName("") && !FolderFetch.safeName(".") && !FolderFetch.safeName("..") && !FolderFetch.safeName(null), "leer, Punkt, Doppelpunkt und null sind unsicher");
        ok(!FolderFetch.safeName("a/b") && !FolderFetch.safeName("../x") && !FolderFetch.safeName("a\\b") && !FolderFetch.safeName("a\u0000b"), "Pfadtrenner und Nullzeichen sind unsicher");
        ok(FolderFetch.safeRelative("") && FolderFetch.safeRelative("A") && FolderFetch.safeRelative("A/B/C"), "relative Pfade aus sicheren Teilen sind sicher");
        ok(!FolderFetch.safeRelative("A/../B") && !FolderFetch.safeRelative("/A") && !FolderFetch.safeRelative("A//B") && !FolderFetch.safeRelative("../A") && !FolderFetch.safeRelative(null), "Ausbruch aus dem Zielordner ist ausgeschlossen");
        ok(FolderFetch.child("", "A").equals("A") && FolderFetch.child("A", "B").equals("A/B") && FolderFetch.child("~", "B").equals("~/B"), "Pfad am PC zusammensetzen");
        ok(FolderFetch.dirKey("A/B").equals("d:A/B") && FolderFetch.isDirKey("d:A/B") && !FolderFetch.isDirKey("A/B|10") && FolderFetch.pathOf("d:A/B").equals("A/B"), "Ordner-Schlüssel");
        ok(FolderFetch.countDirs(java.util.Arrays.asList("d:A", "x|1", "d:B")) == 2, "Ordner in der Auswahl zählen");
        ok(FolderFetch.targetPath("Download/Nolphin", "Bilder", "").equals("Download/Nolphin/Bilder")
                && FolderFetch.targetPath("Download/Nolphin", "Bilder", "Urlaub/2026").equals("Download/Nolphin/Bilder/Urlaub/2026"), "Zielpfad");
        ok(FolderFetch.targetPath("Download/Nolphin", "..", "") == null && FolderFetch.targetPath("Download/Nolphin", "Bilder", "../etc") == null, "unsicherer Zielpfad wird abgelehnt");
        ok(FolderFetch.summary(5, 0, false).equals("5 Dateien") && FolderFetch.summary(1, 0, false).equals("1 Datei")
                && FolderFetch.summary(5, 2, false).equals("5 geladen, 2 fehlgeschlagen") && FolderFetch.summary(3, 0, true).equals("abgebrochen nach 3 Dateien"), "Zusammenfassung");

        // Auswahl nach dem Neuladen: Ordner bleiben, wenn es sie noch gibt
        java.util.List<String> fk = java.util.Arrays.asList("a.txt|1", "b.txt|2");
        java.util.List<String> dk = java.util.Arrays.asList("d:Ordner1", "d:Ordner2");
        java.util.Set<String> kept = FolderFetch.keep(java.util.Arrays.asList("a.txt|1", "d:Ordner1", "weg.txt|9", "d:Weg"), fk, dk);
        ok(kept.size() == 2 && kept.contains("a.txt|1") && kept.contains("d:Ordner1"), "Neuladen: Datei und Ordner bleiben gewählt, Verschwundenes fällt heraus: " + kept);
        ok(FolderFetch.keep(java.util.Arrays.asList("d:Ordner2"), fk, dk).size() == 1, "Neuladen: ein angehakter Ordner verschwindet nicht mehr sofort (Fehler gefunden am Handy)");
        ok(FolderFetch.keep(new java.util.ArrayList<String>(), fk, dk).isEmpty(), "Neuladen: leere Auswahl bleibt leer");

        // Kabelnetz (USB-Tethering)
        ok(CableNet.isCableName("rndis0") && CableNet.isCableName("usb0") && CableNet.isCableName("ncm0") && CableNet.isCableName("RNDIS1") && CableNet.isCableName("enx1234"), "Kabel-Namen erkannt");
        ok(!CableNet.isCableName("wlan0") && !CableNet.isCableName("rmnet_data0") && !CableNet.isCableName("lo") && !CableNet.isCableName("ap0") && !CableNet.isCableName("") && !CableNet.isCableName(null), "WLAN, Mobilfunk, Hotspot und Loopback sind kein Kabel");
        ok(CableNet.ipv4("10.20.30.31") != null && CableNet.ipv4("1.2.3") == null && CableNet.ipv4("1.2.3.256") == null && CableNet.ipv4("a.b.c.d") == null && CableNet.ipv4("") == null && CableNet.ipv4(null) == null && CableNet.ipv4("1..2.3") == null, "IPv4-Adressen werden streng geprüft");
        ok(CableNet.isPrivate(CableNet.ipv4("10.20.30.31")) && CableNet.isPrivate(CableNet.ipv4("192.168.42.129")) && CableNet.isPrivate(CableNet.ipv4("172.20.1.1")) && !CableNet.isPrivate(CableNet.ipv4("172.32.0.1")) && !CableNet.isPrivate(CableNet.ipv4("8.8.8.8")), "private Adressen");
        java.util.List<CableNet.Nic> nics = java.util.Arrays.asList(new CableNet.Nic("wlan0", "192.168.100.50", 24), new CableNet.Nic("rndis0", "10.20.30.31", 24), new CableNet.Nic("lo", "127.0.0.1", 8));
        ok(CableNet.find(nics) != null && CableNet.find(nics).name.equals("rndis0"), "Kabelnetz gefunden");
        ok(CableNet.find(java.util.Arrays.asList(new CableNet.Nic("wlan0", "192.168.100.50", 24))) == null && CableNet.find(new java.util.ArrayList<CableNet.Nic>()) == null && CableNet.find(null) == null, "ohne Kabel: nichts gefunden");
        ok(CableNet.find(java.util.Arrays.asList(new CableNet.Nic("rndis0", "8.8.8.8", 24))) == null, "ein Kabelname mit öffentlicher Adresse zählt nicht");
        CableNet.Nic rn = new CableNet.Nic("rndis0", "10.20.30.31", 24);
        ok(CableNet.inNet(rn, "10.20.30.124") && CableNet.inNet(rn, "10.20.30.124:41000") && !CableNet.inNet(rn, "10.20.40.124") && !CableNet.inNet(rn, "192.168.100.20:40000") && !CableNet.inNet(rn, "x") && !CableNet.inNet(null, "10.20.30.1"), "gleiches Netz");
        ok(CableNet.inNet(new CableNet.Nic("usb0", "192.168.42.129", 16), "192.168.99.1") && !CableNet.inNet(new CableNet.Nic("usb0", "192.168.42.129", 24), "192.168.99.1"), "Netzanteil beachtet");
        ok(!CableNet.inNet(new CableNet.Nic("usb0", "10.0.0.1", 0), "10.0.0.2") && !CableNet.inNet(new CableNet.Nic("usb0", "10.0.0.1", 5), "10.0.0.2"), "unsinnige Netzlänge wird abgelehnt");
        ok(CableNet.viaCable(nics, "10.20.30.124:41000") && !CableNet.viaCable(nics, "192.168.100.20:40000"), "Verbindung über Kabel erkannt, WLAN nicht");
        ok(CableNet.state(nics, true, true, "10.20.30.124:41000") == CableNet.State.CONNECTED_VIA_CABLE, "Zustand: verbunden über Kabel");
        ok(CableNet.state(nics, true, true, "192.168.100.20:40000") == CableNet.State.NONE, "Zustand: verbunden über WLAN zeigt nichts");
        ok(CableNet.state(nics, true, false, null) == CableNet.State.CABLE_READY, "Zustand: Kabel erkannt, nicht verbunden");
        ok(CableNet.state(java.util.Arrays.asList(new CableNet.Nic("wlan0", "192.168.100.50", 24)), true, false, null) == CableNet.State.CABLE_PLUGGED_NO_NET, "Zustand: Kabel steckt, kein Kabelnetz");
        ok(CableNet.state(java.util.Arrays.asList(new CableNet.Nic("wlan0", "192.168.100.50", 24)), false, false, null) == CableNet.State.NONE, "Zustand: kein Kabel, nichts anzeigen");
        ok(CableNet.state(new java.util.ArrayList<CableNet.Nic>(), true, true, "10.20.30.124") == CableNet.State.NONE, "Zustand: verbunden, aber kein Kabelnetz mehr: nichts");

        // Verbindungskarte
        ConnCard.View v1 = ConnCard.of(CableNet.State.NONE, false, false, "");
        ok(v1.way == ConnCard.Way.WIFI && !v1.connected && v1.main == ConnCard.Main.SCAN && v1.linkLine && v1.steps && v1.subtitle.equals("Mit dem PC verbinden"), "Karte: nicht verbunden, WLAN, Scannen, Link-Zeile, Anleitung");
        ConnCard.View v2 = ConnCard.of(CableNet.State.CABLE_READY, false, false, "");
        ok(v2.way == ConnCard.Way.CABLE && v2.main == ConnCard.Main.SCAN && v2.subtitle.equals("USB-Netz zum PC steht bereit"), "Karte: Kabel erkannt: Umschalter springt auf Kabel");
        ConnCard.View v3 = ConnCard.of(CableNet.State.CABLE_PLUGGED_NO_NET, false, false, "");
        ok(v3.way == ConnCard.Way.CABLE && v3.main == ConnCard.Main.TETHERING && v3.linkLine && v3.subtitle.contains("Tethering"), "Karte: Kabel steckt, kein Netz: Hauptknopf Tethering");
        ConnCard.View v4 = ConnCard.of(CableNet.State.NONE, true, false, "Mein-Laptop");
        ok(v4.way == ConnCard.Way.WIFI && v4.connected && v4.main == ConnCard.Main.DISCONNECT && !v4.linkLine && !v4.steps && v4.subtitle.equals("PC: Mein-Laptop"), "Karte: verbunden im WLAN");
        ConnCard.View v5 = ConnCard.of(CableNet.State.CONNECTED_VIA_CABLE, true, false, "Mein-Laptop");
        ok(v5.way == ConnCard.Way.CABLE && v5.connected && v5.main == ConnCard.Main.DISCONNECT, "Karte: verbunden über Kabel");
        ConnCard.View v6 = ConnCard.of(CableNet.State.NONE, true, true, "Mein-Laptop");
        ok(v6.problem && v6.subtitle.equals("PC nicht erreichbar") && v6.way == ConnCard.Way.WIFI, "Karte: gestört, Weg WLAN");
        ok(ConnCard.of(CableNet.State.NONE, true, false, "").subtitle.equals("PC: PC"), "Karte: ohne PC-Namen kein Absturz");
        ok(ConnCard.of(CableNet.State.CABLE_READY, true, false, "x").way == ConnCard.Way.WIFI, "Karte: verbunden über WLAN, Kabel nur bereit: Weg bleibt WLAN");
        ok(ConnCard.mainLabel(ConnCard.Main.SCAN).equals("QR-Code scannen") && ConnCard.mainLabel(ConnCard.Main.TETHERING).equals("USB-Tethering einschalten") && ConnCard.mainLabel(ConnCard.Main.DISCONNECT).equals("Verbindung trennen"), "Karte: Beschriftungen");
        for (CableNet.State cs : CableNet.State.values()) {
            for (int k = 0; k < 4; k++) {
                ConnCard.View vv = ConnCard.of(cs, (k & 1) != 0, (k & 2) != 0, "");
                ok(vv.main != null && vv.way != null && vv.subtitle != null && !vv.subtitle.isEmpty(), "Karte: jede Kombination ergibt eine vollständige Anzeige: " + cs + "/" + k);
            }
        }

        // Wechsel zwischen WLAN und Kabelnetz
        java.util.List<CableNet.Nic> wl = java.util.Arrays.asList(new CableNet.Nic("wlan0", "192.168.100.50", 24));
        java.util.List<CableNet.Nic> cb = java.util.Arrays.asList(new CableNet.Nic("wlan0", "192.168.100.50", 24), new CableNet.Nic("rndis0", "10.20.30.31", 24));
        ok(HostSwitch.decide(wl, "192.168.100.20:40000", null) == HostSwitch.Action.NONE, "kein Kabelnetz, normale Verbindung: nichts tun");
        ok(HostSwitch.decide(cb, "192.168.100.20:40000", null) == HostSwitch.Action.FIND_ON_CABLE, "Kabelnetz da, PC im WLAN nicht erreichbar: im Kabelnetz suchen");
        ok(HostSwitch.decide(cb, "10.20.30.124:40000", "192.168.100.20:40000") == HostSwitch.Action.FIND_ON_CABLE, "auf der Kabeladresse und gestört: im Kabelnetz neu suchen");
        ok(HostSwitch.decide(wl, "10.20.30.124:40000", "192.168.100.20:40000") == HostSwitch.Action.RESTORE_HOME, "Kabel weg: zurück zur Adresse aus dem QR-Code");
        ok(HostSwitch.decide(wl, "192.168.100.20:40000", "192.168.100.20:40000") == HostSwitch.Action.NONE, "Heimadresse gleich der aktuellen: nichts zurückzusetzen");
        ok(HostSwitch.decide(null, "192.168.100.20:40000", null) == HostSwitch.Action.NONE && HostSwitch.decide(new java.util.ArrayList<CableNet.Nic>(), "x", "") == HostSwitch.Action.NONE, "ohne Netzschnittstellen: nichts tun");
        ok(HostSwitch.portOf("10.20.30.124:40000") == 40000 && HostSwitch.portOf("10.20.30.124") == -1 && HostSwitch.portOf("10.20.30.124:") == -1 && HostSwitch.portOf("a:b") == -1 && HostSwitch.portOf("1.2.3.4:70000") == -1 && HostSwitch.portOf(null) == -1, "Port aus der Adresse");
        java.util.List<String> cand = HostSwitch.candidates(new CableNet.Nic("rndis0", "10.20.30.31", 24));
        ok(cand.size() == 253 && cand.contains("10.20.30.1") && cand.contains("10.20.30.254") && !cand.contains("10.20.30.31") && !cand.contains("10.20.30.0") && !cand.contains("10.20.30.255"), "Kandidaten im /24: ohne eigene Adresse, Netz- und Sendeadresse: " + cand.size());
        ok(HostSwitch.candidates(new CableNet.Nic("rndis0", "10.20.30.31", 16)).size() == 253, "größeres Netz: nur der eigene /24");
        ok(HostSwitch.candidates(new CableNet.Nic("rndis0", "10.20.30.34", 28)).size() == 13 && HostSwitch.candidates(new CableNet.Nic("rndis0", "10.20.30.34", 28)).contains("10.20.30.33"), "kleineres Netz: nur dessen Adressen");
        ok(HostSwitch.candidates(new CableNet.Nic("rndis0", "8.8.8.8", 24)).isEmpty() && HostSwitch.candidates(new CableNet.Nic("rndis0", "x", 24)).isEmpty() && HostSwitch.candidates(null).isEmpty() && HostSwitch.candidates(new CableNet.Nic("rndis0", "10.0.0.1", 31)).isEmpty(), "ungültige oder öffentliche Netze: keine Kandidaten");

        // Externe Apps: nur Dateien unterhalb der Speicher, nie private App-Ordner
        List<String> roots = Collections.singletonList("/storage");
        ok(FileUris.isAllowed("/storage/emulated/0/DCIM/foto.jpg", roots), "Foto im Speicher erlaubt");
        ok(FileUris.isAllowed("/storage/1234-ABCD/Musik/lied.mp3", roots), "Datei auf der SD-Karte erlaubt");
        ok(!FileUris.isAllowed("/data/data/org.nolphin.handy/shared_prefs/store.xml", roots), "private Daten der App nicht erlaubt");
        ok(!FileUris.isAllowed("/storage/emulated/0/Android/data/andere.app/datei.db", roots), "private Ordner anderer Apps nicht erlaubt");
        ok(!FileUris.isAllowed("/storage/emulated/0/Android/obb/x/y.obb", roots), "obb nicht erlaubt");
        ok(!FileUris.isAllowed("/storagefremd/x", roots) && !FileUris.isAllowed("/storage", roots), "nur echte Unterpfade");
        ok(!FileUris.isAllowed("", roots) && !FileUris.isAllowed(null, roots) && !FileUris.isAllowed("storage/x/y", roots), "leer und relativ nicht erlaubt");
        ok(!FileUris.isAllowed("/etc/passwd", roots), "Systemdateien nicht erlaubt");

        // Meldung nach der Rückkehr
        ok(FileUris.afterReturn(true, true, 10, 100, 12, 200).equals("Datei geändert."), "geändert erkannt");
        ok(FileUris.afterReturn(true, true, 10, 100, 10, 200).equals("Datei geändert."), "nur Zeitstempel geändert");
        ok(FileUris.afterReturn(true, true, 10, 100, 10, 100).startsWith("Original unverändert"), "Bearbeiten ohne Änderung: ehrlicher Hinweis");
        for (int i = 0; i < 3; i++) {
            String m = i == 0 ? FileUris.afterReturn(true, true, 1, 1, 2, 2) : i == 1 ? FileUris.afterReturn(true, true, 1, 1, 1, 1) : FileUris.afterReturn(true, false, 1, 1, 0, 0);
            ok(m.length() <= 60, "Meldung passt in einen Toast (höchstens 60 Zeichen): " + m);
        }
        ok(FileUris.afterReturn(false, true, 10, 100, 10, 100) == null, "nur Ansehen ohne Änderung: keine Meldung");
        ok(FileUris.afterReturn(false, false, 10, 100, 0, 0).contains("nicht mehr"), "Datei weg");
        ok(FileUris.afterReturn(true, false, 10, 100, 0, 0).contains("nicht mehr"), "Datei weg beim Bearbeiten");

        // Meldungszeile unten
        StatusNote.dismiss();
        ok(StatusNote.current(0) == null, "Zeile am Anfang leer");
        StatusNote.post("2 neu am PC", false, 1, 1000);
        ok(StatusNote.current(1000 + 7999) != null && StatusNote.current(1000 + 7999).tab == 1, "Info bleibt knapp 8 Sekunden");
        ok(StatusNote.current(1000 + 8000) == null, "Info verschwindet nach 8 Sekunden");
        StatusNote.post("x.py fehlgeschlagen", true, 3, 2000);
        ok(StatusNote.current(2000 + 600000) != null && StatusNote.current(2000 + 600000).error, "Fehler bleibt, bis man ihn schließt");
        StatusNote.post("Macros eingefügt", false, 2, 3000);
        ok(StatusNote.current(3001).text.equals("Macros eingefügt"), "die neueste Meldung ersetzt die vorige");
        StatusNote.dismiss();
        ok(StatusNote.current(3002) == null, "Wegwischen schließt die Meldung");
        StatusNote.post("", false, 0, 1);
        ok(StatusNote.current(2) == null, "leere Meldung wird nicht gezeigt");
        ok(StatusNote.arrived("Macros", true, 269).equals("Macros eingefügt, 269 Dateien") && StatusNote.arrived("a.txt", false, 1).equals("a.txt geladen"), "Text für Angekommenes");
        ok(StatusNote.fresh(2, "a").equals("2 neu am PC") && StatusNote.fresh(1, "a.txt").equals("a.txt neu am PC"), "Text für Neues am PC");
        ok(StatusNote.progressLine("") == null && StatusNote.progressLine("Lade a: 40 %").equals("Lade a: 40 %"), "Fortschritt nur während der Übertragung");

        // Zwischenablage "Vom PC kopieren, im Handy einfügen"
        PcClip.clear();
        ok(PcClip.isEmpty() && PcClip.count() == 0, "Zwischenablage am Anfang leer");
        PcClip.set(Collections.singletonList(new RemoteFile("~/Bilder", "strand.jpg", 100)),
                java.util.Arrays.asList(new PcClip.Dir("~/Bilder/Urlaub", "Urlaub"), new PcClip.Dir("~/x", "..")));
        ok(PcClip.count() == 2 && PcClip.files().size() == 1 && PcClip.dirs().size() == 1, "Datei und Ordner gemerkt, unsicherer Ordnername verworfen");
        ok(PcClip.label().equals("2 Einträge vom PC einfügen"), "Beschriftung der Einfügeleiste");
        PcClip.set(Collections.singletonList(new RemoteFile("a.txt", 1)), Collections.<PcClip.Dir>emptyList());
        ok(PcClip.label().equals("1 Eintrag vom PC einfügen") && PcClip.dirs().isEmpty(), "ein Eintrag, alte Auswahl ersetzt");
        PcClip.clear();
        ok(PcClip.isEmpty(), "nach dem Einfügen leer");

        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden");
        if (fails > 0) {
            System.exit(1);
        }
    }
}
