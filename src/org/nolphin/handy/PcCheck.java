package org.nolphin.handy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Prüft, dass unter der Adresse im Link wirklich Nolphin antwortet und der Schlüssel gilt (GET /info). */
final class PcCheck {
    private PcCheck() {}

    /** Ergebnis der Prüfung; die Meldung ist für den Nutzer bestimmt, bei OK leer. */
    static final class Result {
        final boolean ok;
        final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    static Result check(Link link) {
        HttpURLConnection c = null;
        try {
            URL url = new URL("http://" + link.authority() + "/info?t=" + URLEncoder.encode(link.token, "UTF-8"));
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            int code = c.getResponseCode();
            if (code == 403) {
                String body = read(c.getErrorStream());
                return new Result(false, body.contains("abgelaufen")
                        ? "Der Schlüssel ist abgelaufen. Am PC in Nolphin einen neuen QR-Code erzeugen"
                        : "Der PC lehnt den Schlüssel ab. Wahrscheinlich wurde Nolphin neu gestartet oder ein neuer Code erzeugt: den neuen QR-Code scannen");
            }
            if (code == 404) {
                // Ältere Nolphin-Version ohne /info: Schlüssel und Erreichbarkeit über /ls prüfen
                c.disconnect();
                url = new URL("http://" + link.authority() + "/ls?t=" + URLEncoder.encode(link.token, "UTF-8"));
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                code = c.getResponseCode();
                if (code == 200 && read(c.getInputStream()).startsWith("[")) {
                    return new Result(true, "");
                }
                if (code == 403) {
                    return new Result(false, "Der PC lehnt den Schlüssel ab. Den neuen QR-Code scannen");
                }
                return new Result(false, "Unter dieser Adresse läuft kein passendes Nolphin");
            }
            if (code != 200) {
                return new Result(false, "Der PC antwortet mit Fehler " + code);
            }
            String body = read(c.getInputStream());
            if (!body.contains("\"app\":\"nolphin\"")) {
                return new Result(false, "Unter dieser Adresse läuft kein Nolphin");
            }
            if (!body.contains("\"api\":1")) {
                return new Result(false, "Nolphin am PC hat eine andere Version der Schnittstelle. Bitte App und Nolphin aktualisieren");
            }
            return new Result(true, "");
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, "Der PC antwortet nicht. Sind Handy und PC im selben WLAN, und läuft der Austausch in Nolphin?");
        } catch (java.io.IOException e) {
            return new Result(false, "Keine Verbindung zum PC (" + link.authority() + "). Sind Handy und PC im selben WLAN, und läuft der Austausch in Nolphin?");
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0 && total < 65536) {
            b.write(buf, 0, n);
            total += n;
        }
        return b.toString("UTF-8");
    }
}
