package org.nolphin.handy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Der einzige Weg, wie die App mit dem PC spricht: nur https, und nur mit dem Zertifikat, dessen
 * Fingerabdruck im QR-Code stand ("Pinning"). Alles andere (http, fremde Zertifikate, auch von
 * sonst vertrauenswürdigen Stellen) wird abgelehnt. Reines Java, ohne Android-Klassen.
 */
final class Secure {
    private Secure() {}

    /** Fingerabdruck wie in Nolphin: die ersten 16 Bytes von SHA-256 über das Zertifikat (DER), base64url ohne "=". */
    static String fingerprintOf(byte[] der) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(der);
            byte[] first = java.util.Arrays.copyOf(hash, 16);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(first);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Wie token_equal in Nolphin: kein früher Abbruch beim Vergleich. */
    private static boolean same(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static final ConcurrentMap<String, SSLSocketFactory> FACTORIES = new ConcurrentHashMap<>();

    private static SSLSocketFactory factory(final String pin) throws IOException {
        SSLSocketFactory f = FACTORIES.get(pin);
        if (f != null) {
            return f;
        }
        TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Die App nimmt keine Clientzertifikate an");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain == null || chain.length == 0) {
                    throw new CertificateException("Der PC hat kein Zertifikat geschickt");
                }
                // Das Blattzertifikat muss genau das aus dem QR-Code sein; eine Kette bis zu einer Zertifizierungsstelle zählt nicht.
                String got = fingerprintOf(chain[0].getEncoded());
                if (!same(got, pin)) {
                    throw new CertificateException("Das Zertifikat des PCs passt nicht zum QR-Code");
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { tm }, null);
            f = ctx.getSocketFactory();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("TLS nicht verfügbar", e);
        }
        FACTORIES.put(pin, f);
        return f;
    }

    private static final HostnameVerifier PINNED = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
            return true;   // geprüft wird der Fingerabdruck des Zertifikats, nicht der Name (der PC hat nur eine IP-Adresse)
        }
    };

    /** Öffnet eine Verbindung; sie verbindet sich erst beim ersten Lesen oder Schreiben. */
    static HttpURLConnection connect(URL url, String pin) throws IOException {
        if (url == null || !"https".equals(url.getProtocol())) {
            throw new IOException("Unverschlüsselte Verbindungen sind nicht erlaubt");
        }
        if (pin == null || pin.length() != 22) {
            throw new IOException("Kein Fingerabdruck des PCs bekannt: den QR-Code neu scannen");
        }
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setSSLSocketFactory(factory(pin));
        c.setHostnameVerifier(PINNED);
        c.setInstanceFollowRedirects(false);   // nie auf eine andere Adresse umleiten lassen
        return c;
    }

    /** Ob der Fehler daher kommt, dass das Zertifikat des PCs nicht zum QR-Code passt. */
    static boolean isPinMismatch(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof CertificateException && t.getMessage() != null && t.getMessage().contains("passt nicht zum QR-Code")) {
                return true;
            }
        }
        return false;
    }
}
