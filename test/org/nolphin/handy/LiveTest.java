package org.nolphin.handy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Prüft die App-Seite gegen den ECHTEN Nolphin-Server (C, GnuTLS): Der Fingerabdruck, den der Server meldet,
 * muss zu dem passen, den die App aus dem Zertifikat berechnet. Aufruf: LiveTest <Programm phone-drv>
 */
public class LiveTest {
    static int fails = 0, runs = 0;

    static void check(boolean ok, String what) {
        runs++;
        if (!ok) {
            fails++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] a) throws Exception {
        Process p = new ProcessBuilder(a[0]).redirectErrorStream(false).start();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            check(line != null, "Server liefert Port, Schlüssel und Fingerabdruck");
            String[] f = line.trim().split(" ");
            int port = Integer.parseInt(f[0]);
            String token = f[1], fp = f[2];

            // Link genau so, wie Nolphin ihn im QR-Code zeigt (src/nolphin-phone.c, phone_refresh_url)
            String link = "nolphin://127.0.0.1:" + port + "/?t=" + token + "&v=2&f=" + fp;
            Link l = Link.parse(link, System.currentTimeMillis() / 1000);
            check(l.fingerprint.equals(fp) && l.token.equals(token), "Link des Servers wird von der App gelesen");

            PcCheck.Result ok = PcCheck.check(l);
            check(ok.ok, "App prüft den echten Server und verbindet (" + ok.message + ")");

            String wrong = "nolphin://127.0.0.1:" + port + "/?t=" + token + "&v=2&f=AAAAAAAAAAAAAAAAAAAAAA";
            PcCheck.Result bad = PcCheck.check(Link.parse(wrong, System.currentTimeMillis() / 1000));
            check(!bad.ok && bad.message.contains("passt nicht zum QR-Code"), "falscher Fingerabdruck: App lehnt ab (" + bad.message + ")");

            String wrongToken = "nolphin://127.0.0.1:" + port + "/?t=" + "0".repeat(32) + "&v=2&f=" + fp;
            PcCheck.Result badToken = PcCheck.check(Link.parse(wrongToken, System.currentTimeMillis() / 1000));
            check(!badToken.ok && badToken.message.contains("Schlüssel"), "falscher Schlüssel: App meldet ihn (" + badToken.message + ")");

            // Der Server hat keine Browser-Seite mehr
            HttpURLConnection c = Secure.connect(new URL("https://127.0.0.1:" + port + "/?t=" + token), fp);
            check(c.getResponseCode() == 404, "keine Browser-Seite: 404");

            // Klartext auf dem Port: keine Antwort
            try {
                HttpURLConnection plain = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/info?t=" + token).openConnection();
                plain.setConnectTimeout(3000);
                plain.setReadTimeout(3000);
                int code = plain.getResponseCode();
                check(false, "Klartext-Anfrage bekam Antwort " + code);
            } catch (java.io.IOException e) {
                check(true, "Klartext-Anfrage bekommt keine Antwort");
            }
        } finally {
            p.getOutputStream().close();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            p.destroy();
        }
        System.out.println((runs - fails) + " von " + runs + " Prüfungen gegen den echten Nolphin-Server bestanden");
        System.exit(fails == 0 ? 0 : 1);
    }
}
