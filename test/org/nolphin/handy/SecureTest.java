package org.nolphin.handy;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Prüft die Verbindung mit festem Zertifikat (Secure): richtiger Fingerabdruck wird angenommen, falscher,
 * fremde Zertifikate und unverschlüsselte Adressen werden abgelehnt. Die Zertifikate stellt test.sh mit keytool her.
 * Aufruf: SecureTest <Ordner mit eins.p12 und zwei.p12>
 */
public class SecureTest {
    static int fails = 0, runs = 0;

    static void check(boolean ok, String what) {
        runs++;
        if (!ok) {
            fails++;
            System.out.println("FAIL: " + what);
        }
    }

    static class Srv {
        final HttpsServer server;
        final String pin;
        final int port;

        Srv(File p12) throws Exception {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(p12)) {
                ks.load(in, "geheim".toCharArray());
            }
            String alias = ks.aliases().nextElement();
            pin = Secure.fingerprintOf(((X509Certificate) ks.getCertificate(alias)).getEncoded());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "geheim".toCharArray());
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(ctx));
            server.createContext("/info", ex -> {
                byte[] b = "{\"app\":\"nolphin\"}".getBytes("UTF-8");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            server.createContext("/weiter", ex -> {
                ex.getResponseHeaders().add("Location", "https://127.0.0.1:1/");
                ex.sendResponseHeaders(302, -1);
                ex.close();
            });
            server.start();
            port = server.getAddress().getPort();
        }
    }

    static String get(int port, String path, String pin) throws IOException {
        HttpURLConnection c = Secure.connect(new URL("https://127.0.0.1:" + port + path), pin);
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        int code = c.getResponseCode();
        if (code != 200) {
            return "HTTP " + code;
        }
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), "UTF-8");
        }
    }

    public static void main(String[] a) throws Exception {
        Srv eins = new Srv(new File(a[0], "eins.p12"));
        Srv zwei = new Srv(new File(a[0], "zwei.p12"));
        try {
            check(eins.pin.length() == 22 && eins.pin.matches("[A-Za-z0-9_-]{22}"), "Fingerabdruck hat 22 Zeichen aus base64url");
            check(!eins.pin.equals(zwei.pin), "zwei Zertifikate haben verschiedene Fingerabdrücke");

            check(get(eins.port, "/info", eins.pin).contains("\"app\":\"nolphin\""), "richtiger Fingerabdruck: Verbindung klappt");
            check(get(zwei.port, "/info", zwei.pin).contains("\"app\":\"nolphin\""), "richtiger Fingerabdruck beim zweiten Server");

            try {
                get(eins.port, "/info", zwei.pin);
                check(false, "fremdes Zertifikat (Fingerabdruck eines anderen) muss abgelehnt werden");
            } catch (IOException e) {
                check(Secure.isPinMismatch(e), "fremdes Zertifikat: abgelehnt mit Meldung 'passt nicht zum QR-Code' (" + e + ")");
            }
            try {
                get(eins.port, "/info", "AAAAAAAAAAAAAAAAAAAAAA");
                check(false, "falscher Fingerabdruck muss abgelehnt werden");
            } catch (IOException e) {
                check(Secure.isPinMismatch(e), "falscher Fingerabdruck: abgelehnt");
            }
            // Man-in-the-Middle: ein Dritter mit eigenem, gültig aussehendem Zertifikat auf dem Port des PCs
            try {
                get(zwei.port, "/info", eins.pin);
                check(false, "Dritter mit eigenem Zertifikat darf nicht durchkommen");
            } catch (IOException e) {
                check(Secure.isPinMismatch(e), "Dritter mit eigenem Zertifikat: abgelehnt");
            }

            for (String bad : new String[] { null, "", "zu-kurz", "QULDtffIw8Ed6DmETVJ7zAA" }) {
                try {
                    get(eins.port, "/info", bad);
                    check(false, "ungültiger Fingerabdruck '" + bad + "' darf nicht verbinden");
                } catch (IOException e) {
                    check(!Secure.isPinMismatch(e), "ungültiger Fingerabdruck '" + bad + "': abgelehnt, ohne zu verbinden");
                }
            }
            try {
                Secure.connect(new URL("http://127.0.0.1:" + eins.port + "/info"), eins.pin);
                check(false, "http:// muss abgelehnt werden");
            } catch (IOException e) {
                check(e.getMessage().contains("Unverschlüsselt"), "http:// wird abgelehnt");
            }
            try {
                Secure.connect(new URL("http://127.0.0.1:1/"), null);
                check(false, "http:// ohne Fingerabdruck muss abgelehnt werden");
            } catch (IOException e) {
                check(true, "http:// ohne Fingerabdruck wird abgelehnt");
            }

            // Klartext-Server auf einer https-Adresse: kein Verbinden, keine Daten
            com.sun.net.httpserver.HttpServer plain = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            plain.createContext("/info", ex -> {
                byte[] b = "KLARTEXT".getBytes("UTF-8");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            plain.start();
            try {
                String got = get(plain.getAddress().getPort(), "/info", eins.pin);
                check(false, "Klartext-Server hinter https-Adresse: es dürfen keine Daten kommen, kam: " + got);
            } catch (IOException e) {
                check(true, "Klartext-Server hinter https-Adresse: Verbindung scheitert");
            }
            plain.stop(0);

            // keine Umleitung auf eine andere Adresse
            HttpURLConnection c = Secure.connect(new URL("https://127.0.0.1:" + eins.port + "/weiter"), eins.pin);
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            check(c.getResponseCode() == 302 && !c.getInstanceFollowRedirects(), "Umleitungen werden nicht verfolgt");

            // Verbindung wiederholt benutzen (Zwischenspeicher der Fabrik) bleibt gültig
            for (int i = 0; i < 5; i++) {
                if (!get(eins.port, "/info", eins.pin).contains("nolphin")) {
                    check(false, "wiederholte Verbindung " + i);
                }
            }
            check(true, "fünf Verbindungen hintereinander");
        } finally {
            eins.server.stop(0);
            zwei.server.stop(0);
        }
        System.out.println((runs - fails) + " von " + runs + " Prüfungen zur verschlüsselten Verbindung bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
