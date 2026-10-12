package org.nolphin.handy;

/** Prüft den Verbindungslink (Link.parse): gültige Formen und Fälle, die abgelehnt werden müssen. */
public class LinkTest {
    static int fails = 0, runs = 0;
    static final String T = "0123456789abcdef0123456789abcdef";   // erfunden
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

    static final String F = "QULDtffIw8Ed6DmETVJ7zA";   // erfundener Fingerabdruck (22 Zeichen)

    public static void main(String[] args) {
        String q = "?t=" + T + "&v=2&f=" + F;
        ok("Link aus dem QR-Code (Version 2)", "https://192.168.1.10:40000/" + q);
        ok("Link mit Ablauf (wird noch gelesen)", "https://192.168.1.10:40000/" + q + "&e=" + (NOW + 3600));
        ok("Link aus dem QR-Code (nolphin://, Version 2)", "nolphin://192.168.1.10:40000/" + q);
        ok("nolphin:// mit längster Adresse", "nolphin://192.168.178.255:65535/" + q);
        ok("nolphin-Link, ältere Form mit connect", "nolphin://connect?host=192.168.1.10:40000&t=" + T + "&v=2&f=" + F);
        ok("Netz 10.x", "https://10.0.2.2:8080/" + q);
        ok("Netz 172.16 bis 172.31", "https://172.31.255.254:65535/" + q);
        ok("Leerzeichen und Zeilenumbruch drumherum", "  https://192.168.1.5:1024/" + q + "\n");
        ok("knapp abgelaufen, innerhalb der Uhrentoleranz", "https://192.168.1.5:1024/" + q + "&e=" + (NOW - 300));
        ok("Fingerabdruck mit - und _", "https://192.168.1.5:1024/?t=" + T + "&v=2&f=ab-_cdEFgh0123456789AB");
        runs++;
        try {
            Link l = Link.parse("https://192.168.1.5:1024/" + q, NOW);
            if (!F.equals(l.fingerprint) || !"192.168.1.5:1024".equals(l.authority()) || !T.equals(l.token)) {
                throw new AssertionError("Felder falsch");
            }
        } catch (Throwable e) {
            fails++;
            System.out.println("FAIL (Felder des Links): " + e);
        }

        // alte, unverschlüsselte Links (Version 1) dürfen nie mehr gelten
        bad("alter Link (Version 1, http)", "http://192.168.1.10:40000/?t=" + T + "&v=1&e=" + (NOW + 3600), "unverschlüsselt");
        bad("alter Link ohne Version", "http://192.168.1.10:40000/?t=" + T, "unverschlüsselt");
        bad("http auch mit Fingerabdruck", "http://192.168.1.10:40000/" + q, "unverschlüsselt");
        bad("https ohne Version", "https://192.168.1.5:1024/?t=" + T + "&f=" + F, "Version");
        bad("https mit Version 1", "https://192.168.1.5:1024/?t=" + T + "&v=1&f=" + F, "Version 1");
        bad("unbekannte Version 3", "https://192.168.1.5:1024/?t=" + T + "&v=3&f=" + F, "Version 3");
        bad("nolphin:// ohne Fingerabdruck", "nolphin://192.168.1.10:40000/?t=" + T + "&v=2", "Fingerabdruck");
        bad("nolphin:// ohne Port", "nolphin://192.168.1.10/" + q, "Port");
        bad("nolphin:// mit öffentlicher Adresse", "nolphin://8.8.8.8:40000/" + q, "privaten Netz");
        bad("nolphin:// mit Hostname", "nolphin://example.com:40000/" + q, "IPv4");
        bad("nolphin:// Version 1", "nolphin://192.168.1.10:40000/?t=" + T + "&v=1&f=" + F, "Version 1");
        bad("Fingerabdruck fehlt", "https://192.168.1.5:1024/?t=" + T + "&v=2", "Fingerabdruck");
        bad("Fingerabdruck zu kurz", "https://192.168.1.5:1024/?t=" + T + "&v=2&f=QULDtffIw8Ed6DmETVJ7z", "Fingerabdruck");
        bad("Fingerabdruck zu lang", "https://192.168.1.5:1024/?t=" + T + "&v=2&f=QULDtffIw8Ed6DmETVJ7zAA", "Fingerabdruck");
        bad("Fingerabdruck mit Sonderzeichen", "https://192.168.1.5:1024/?t=" + T + "&v=2&f=QULDtffIw8Ed6DmETVJ7%2F%2F", "Fingerabdruck");
        bad("Fingerabdruck leer", "https://192.168.1.5:1024/?t=" + T + "&v=2&f=", null);
        bad("nolphin-Link ohne Fingerabdruck", "nolphin://connect?host=192.168.1.5:1024&t=" + T + "&v=2", "Fingerabdruck");

        bad("abgelaufen", "https://192.168.1.5:1024/" + q + "&e=" + (NOW - 7200), "abgelaufen");
        bad("öffentliche Adresse", "https://8.8.8.8:1024/" + q, "privaten Netz");
        bad("172.32 liegt außerhalb des privaten Bereichs", "https://172.32.0.1:1024/" + q, "privaten Netz");
        bad("Hostname statt Adresse", "https://example.com:1024/" + q, "IPv4");
        bad("IPv6", "https://[::1]:1024/" + q, null);
        bad("Oktett über 255", "https://192.168.1.256:1024/" + q, "IPv4");
        bad("zu wenige Oktette", "https://192.168.1:1024/" + q, "IPv4");
        bad("Port fehlt", "https://192.168.1.5/" + q, "Port");
        bad("Port 0", "https://192.168.1.5:0/" + q, "Port");
        bad("Port zu groß", "https://192.168.1.5:70000/" + q, "Port");
        bad("Port keine Zahl", "https://192.168.1.5:abc/" + q, "Port");
        bad("Schlüssel fehlt", "https://192.168.1.5:1024/?v=2&f=" + F, "Schlüssel");
        bad("Schlüssel zu kurz", "https://192.168.1.5:1024/?t=abcd&v=2&f=" + F, "Schlüssel");
        bad("Schlüssel mit Großbuchstaben", "https://192.168.1.5:1024/?t=" + T.toUpperCase() + "&v=2&f=" + F, "Schlüssel");
        bad("Schlüssel mit Sonderzeichen", "https://192.168.1.5:1024/?t=" + T.substring(0, 30) + "%2F%2F&v=2&f=" + F, "Schlüssel");
        bad("anderes Schema ftp", "ftp://192.168.1.5:1024/" + q, "kein Nolphin-Link");
        bad("javascript", "javascript:alert(1)", "kein Nolphin-Link");
        bad("file", "file:///etc/passwd", "kein Nolphin-Link");
        bad("nolphin mit falschem Host (abgelehnt, ohne Port)", "nolphin://other?host=192.168.1.5:1024&t=" + T + "&v=2&f=" + F, "Port");
        bad("nolphin mit Hostname und Port", "nolphin://other:1024/" + q, "IPv4");
        bad("nolphin ohne Adresse", "nolphin://connect?t=" + T + "&v=2&f=" + F, "Adresse");
        bad("Ablauf keine Zahl", "https://192.168.1.5:1024/" + q + "&e=morgen", "Ablaufzeit");
        bad("leer", "", null);
        bad("nur Leerzeichen", "   ", null);
        bad("null", null, null);
        bad("zu lang", "https://192.168.1.5:1024/" + q + "&x=" + "a".repeat(400), null);
        bad("Text statt Link", "Hallo Welt", null);

        System.out.println((runs - fails) + " von " + runs + " Linkprüfungen bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
