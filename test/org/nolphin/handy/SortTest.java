package org.nolphin.handy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Prüft Sortierreihenfolge sowie Favoriten- und Verlaufslisten (ohne Handy). */
public class SortTest {
    static int fails = 0, n = 0;

    static void ok(boolean c, String what) {
        n++;
        if (!c) {
            fails++;
            System.out.println("FEHLER: " + what);
        }
    }

    static final class Item implements SortOrder.Sortable {
        final String name;
        final boolean dir;
        final long size;
        final long modified;

        Item(String name, boolean dir, long size, long modified) {
            this.name = name;
            this.dir = dir;
            this.size = size;
            this.modified = modified;
        }

        @Override
        public String sortName() {
            return name;
        }

        @Override
        public boolean sortIsDir() {
            return dir;
        }

        @Override
        public long sortSize() {
            return size;
        }

        @Override
        public long sortModified() {
            return modified;
        }
    }

    static String order(List<Item> items, SortOrder o) {
        List<Item> l = new ArrayList<>(items);
        Collections.sort(l, o.comparator());
        StringBuilder b = new StringBuilder();
        for (Item i : l) {
            b.append(b.length() == 0 ? "" : ",").append(i.name);
        }
        return b.toString();
    }

    public static void main(String[] a) {
        List<Item> mixed = Arrays.asList(
                new Item("zeta.txt", false, 50, 300),
                new Item("Alpha", true, 0, 100),
                new Item("beta.pdf", false, 9000, 100),
                new Item("Gamma", true, 0, 500),
                new Item("alpha.txt", false, 50, 400),
                new Item("noext", false, 7, 200),
                new Item("archiv.tar.gz", false, 1, 250));

        ok(order(mixed, new SortOrder(SortOrder.NAME, true, true)).equals("Alpha,Gamma,alpha.txt,archiv.tar.gz,beta.pdf,noext,zeta.txt"),
                "Name aufsteigend, Ordner zuerst, Groß-/Kleinschreibung egal: " + order(mixed, new SortOrder(SortOrder.NAME, true, true)));
        ok(order(mixed, new SortOrder(SortOrder.NAME, false, true)).equals("Gamma,Alpha,zeta.txt,noext,beta.pdf,archiv.tar.gz,alpha.txt"),
                "Name absteigend: Ordner bleiben vorn: " + order(mixed, new SortOrder(SortOrder.NAME, false, true)));
        ok(order(mixed, new SortOrder(SortOrder.NAME, true, false)).equals("Alpha,alpha.txt,archiv.tar.gz,beta.pdf,Gamma,noext,zeta.txt"),
                "Name aufsteigend ohne 'Ordner zuerst': Ordner einsortiert: " + order(mixed, new SortOrder(SortOrder.NAME, true, false)));
        ok(order(mixed, new SortOrder(SortOrder.SIZE, false, true)).equals("Alpha,Gamma,beta.pdf,alpha.txt,zeta.txt,noext,archiv.tar.gz"),
                "Größe absteigend, gleiche Größe nach Name: " + order(mixed, new SortOrder(SortOrder.SIZE, false, true)));
        ok(order(mixed, new SortOrder(SortOrder.SIZE, true, true)).equals("Alpha,Gamma,archiv.tar.gz,noext,alpha.txt,zeta.txt,beta.pdf"),
                "Größe aufsteigend: " + order(mixed, new SortOrder(SortOrder.SIZE, true, true)));
        ok(order(mixed, new SortOrder(SortOrder.DATE, false, true)).equals("Gamma,Alpha,alpha.txt,zeta.txt,archiv.tar.gz,noext,beta.pdf"),
                "Datum absteigend (neu zuerst): " + order(mixed, new SortOrder(SortOrder.DATE, false, true)));
        ok(order(mixed, new SortOrder(SortOrder.DATE, true, true)).equals("Alpha,Gamma,beta.pdf,noext,archiv.tar.gz,zeta.txt,alpha.txt"),
                "Datum aufsteigend (alt zuerst), gleiches Datum nach Name: " + order(mixed, new SortOrder(SortOrder.DATE, true, true)));
        ok(order(mixed, new SortOrder(SortOrder.TYPE, true, true)).equals("Alpha,Gamma,noext,archiv.tar.gz,beta.pdf,alpha.txt,zeta.txt"),
                "Dateityp aufsteigend (ohne Endung zuerst), gleicher Typ nach Name: " + order(mixed, new SortOrder(SortOrder.TYPE, true, true)));
        ok(order(mixed, new SortOrder(99, true, true)).equals(order(mixed, new SortOrder(SortOrder.NAME, true, true))), "ungültiger Schlüssel gilt als Name");
        ok(order(new ArrayList<Item>(), SortOrder.standard()).isEmpty(), "leere Liste");

        ok(SortOrder.extension("archiv.tar.gz").equals("gz") && SortOrder.extension(".versteckt").isEmpty() && SortOrder.extension("name.").isEmpty()
                && SortOrder.extension("FOTO.JPG").equals("jpg") && SortOrder.extension("ohne").isEmpty(), "Endung wird richtig erkannt");
        ok(new SortOrder(SortOrder.SIZE, false, true).describe().equals("Größe ↓") && SortOrder.standard().describe().equals("Name ↑"), "Hinweistext");

        // Reihenfolge ändert sich mit der Richtung genau umgekehrt (außer Ordner und Gleichstand)
        List<Item> files = Arrays.asList(new Item("c", false, 3, 3), new Item("a", false, 1, 1), new Item("b", false, 2, 2));
        ok(order(files, new SortOrder(SortOrder.SIZE, true, true)).equals("a,b,c") && order(files, new SortOrder(SortOrder.SIZE, false, true)).equals("c,b,a"),
                "Richtung kehrt die Reihenfolge um");

        // Listen
        List<String> l = PlaceLists.parse("/a\n\n/b\n/a\n  /c  \n");
        ok(l.equals(Arrays.asList("/a", "/b", "/c")), "parse: Leerzeilen und Doppelte entfallen: " + l);
        ok(PlaceLists.parse(null).isEmpty() && PlaceLists.parse("").isEmpty(), "parse: leer");
        ok(PlaceLists.parse(PlaceLists.join(l)).equals(l), "join und parse: gleiche Liste");
        List<String> r = PlaceLists.pushFront(Arrays.asList("/a", "/b", "/c"), "/c", 8);
        ok(r.equals(Arrays.asList("/c", "/a", "/b")), "pushFront: vorhandener Eintrag rückt nach vorn: " + r);
        r = PlaceLists.pushFront(Arrays.asList("/a", "/b", "/c"), "/x", 3);
        ok(r.equals(Arrays.asList("/x", "/a", "/b")), "pushFront: Liste bleibt begrenzt, ältester fällt weg: " + r);
        r = PlaceLists.pushFront(new ArrayList<String>(), "/x", 8);
        ok(r.equals(Arrays.asList("/x")), "pushFront: leere Liste");
        List<String> f = PlaceLists.add(Arrays.asList("/a"), "/b");
        f = PlaceLists.add(f, "/a");
        ok(f.equals(Arrays.asList("/a", "/b")), "add: Reihenfolge bleibt, keine Doppelten: " + f);
        f = PlaceLists.remove(f, "/a");
        ok(f.equals(Arrays.asList("/b")) && PlaceLists.remove(f, "/nix").equals(f), "remove: nur der gewählte Eintrag");
        String joined = PlaceLists.join(Arrays.asList("/mit Leerzeichen/Ordner", "/Größe äöü"));
        ok(PlaceLists.parse(joined).equals(Arrays.asList("/mit Leerzeichen/Ordner", "/Größe äöü")), "Pfade mit Leerzeichen und Umlauten bleiben erhalten");

        System.out.println((n - fails) + " von " + n + " Prüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
