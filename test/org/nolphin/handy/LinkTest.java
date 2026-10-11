package org.nolphin.handy;

/** Prüft den Verbindungslink (Link.parse): gültige Formen und Fälle, die abgelehnt werden müssen. */
public class LinkTest {
    static int fails = 0, runs = 0;
    static final String T = "4d6d1633ec8ca225aaeb5ddfbc5a1b6d";
    static final long NOW = 1_800_000_000L;

    static void ok(String what, String link) {
        runs++;
        try {
            Link l = Link.parse(link, NOW);
            if (l.token == null) {
                throw new AssertionError();
            }
        } catch (Exception e) {
            fails++;
            System.out.println("FAIL (sollte gelten): " + what + " -> " + e.getMessage());
        }
    }

    static void bad(String what, String link, String expectInMessage) {
        runs++;
        try {
            Link.parse(link, NOW);
            fails++;
            System.out.println("FAIL (sollte abgelehnt werden): " + what);
        } catch (Link.Invalid e) {
            if (expectInMessage != null && !e.getMessage().contains(expectInMessage)) {
                fails++;
                System.out.println("FAIL (falsche Meldung): " + what + " -> " + e.getMessage());
            }
        } catch (Exception e) {
            fails++;
            System.out.println("FAIL (unerwartete Ausnahme): " + what + " -> " + e);
        }
    }

    public static void main(String[] args) {
        ok("Link aus dem QR-Code (Version 1)", "http://192.168.178.20:43913/?t=" + T + "&v=1&e=" + (NOW + 3600));
        ok("Link ohne Version und Ablauf (ältere Nolphin-Version)", "http://192.168.178.20:43913/?t=" + T);
        ok("nolphin-Link von der Seite", "nolphin://connect?host=192.168.178.20:43913&t=" + T);
        ok("Netz 10.x", "http://10.0.2.2:8080/?t=" + T);
        ok("Netz 172.16 bis 172.31", "http://172.31.255.254:65535/?t=" + T);
        ok("Leerzeichen und Zeilenumbruch drumherum", "  http://192.168.1.5:1024/?t=" + T + "\n");
        ok("knapp abgelaufen, innerhalb der Uhrentoleranz", "http://192.168.1.5:1024/?t=" + T + "&e=" + (NOW - 300));

        bad("abgelaufen", "http://192.168.1.5:1024/?t=" + T + "&e=" + (NOW - 7200), "abgelaufen");
        bad("unbekannte Version", "http://192.168.1.5:1024/?t=" + T + "&v=2", "Version 2");
        bad("öffentliche Adresse", "http://8.8.8.8:1024/?t=" + T, "privaten Netz");
        bad("172.32 liegt außerhalb des privaten Bereichs", "http://172.32.0.1:1024/?t=" + T, "privaten Netz");
        bad("Hostname statt Adresse", "http://example.com:1024/?t=" + T, "IPv4");
        bad("IPv6", "http://[::1]:1024/?t=" + T, null);
        bad("Oktett über 255", "http://192.168.1.256:1024/?t=" + T, "IPv4");
        bad("zu wenige Oktette", "http://192.168.1:1024/?t=" + T, "IPv4");
        bad("Port fehlt", "http://192.168.1.5/?t=" + T, "Port");
        bad("Port 0", "http://192.168.1.5:0/?t=" + T, "Port");
        bad("Port zu groß", "http://192.168.1.5:70000/?t=" + T, "Port");
        bad("Port keine Zahl", "http://192.168.1.5:abc/?t=" + T, "Port");
        bad("Schlüssel fehlt", "http://192.168.1.5:1024/", "Schlüssel");
        bad("Schlüssel zu kurz", "http://192.168.1.5:1024/?t=abcd", "Schlüssel");
        bad("Schlüssel mit Großbuchstaben", "http://192.168.1.5:1024/?t=" + T.toUpperCase(), "Schlüssel");
        bad("Schlüssel mit Sonderzeichen", "http://192.168.1.5:1024/?t=" + T.substring(0, 30) + "%2F%2F", "Schlüssel");
        bad("anderes Schema", "https://192.168.1.5:1024/?t=" + T, "kein Nolphin-Link");
        bad("javascript", "javascript:alert(1)", "kein Nolphin-Link");
        bad("file", "file:///etc/passwd", "kein Nolphin-Link");
        bad("nolphin mit falschem Host", "nolphin://other?host=192.168.1.5:1024&t=" + T, "kein Nolphin-Link");
        bad("nolphin ohne Adresse", "nolphin://connect?t=" + T, "Adresse");
        bad("Ablauf keine Zahl", "http://192.168.1.5:1024/?t=" + T + "&e=morgen", "Ablaufzeit");
        bad("leer", "", null);
        bad("nur Leerzeichen", "   ", null);
        bad("null", null, null);
        bad("zu lang", "http://192.168.1.5:1024/?t=" + T + "&x=" + "a".repeat(400), null);
        bad("Text statt Link", "Hallo Welt", null);

        System.out.println((runs - fails) + " von " + runs + " Linkprüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
