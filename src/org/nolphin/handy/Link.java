package org.nolphin.handy;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Verbindungslink von Nolphin, Format Version 1:
 *   http://HOST:PORT/?t=SCHLÜSSEL&v=1&e=ABLAUF
 *   nolphin://connect?host=HOST:PORT&t=SCHLÜSSEL&v=1&e=ABLAUF
 * HOST ist eine IPv4-Adresse aus einem privaten Netz, SCHLÜSSEL besteht aus 32 Hex-Zeichen,
 * ABLAUF (optional) ist die Unix-Zeit in Sekunden. Reines Java, ohne Android-Klassen.
 */
final class Link {
    static final int VERSION = 1;
    /** Toleranz für Uhren, die nicht genau gehen (Sekunden). */
    static final long CLOCK_SKEW = 600;

    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");

    final String host;     // nur die Adresse
    final int port;
    final String token;
    final long expires;    // 0 = nicht angegeben

    private Link(String host, int port, String token, long expires) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.expires = expires;
    }

    /** "host:port" wie in der Adresse. */
    String authority() {
        return host + ":" + port;
    }

    /** Ungültiger Link; die Meldung ist für den Nutzer bestimmt. */
    static final class Invalid extends Exception {
        private static final long serialVersionUID = 1L;

        Invalid(String message) {
            super(message);
        }
    }

    static Link parse(String text, long nowSeconds) throws Invalid {
        if (text == null) {
            throw new Invalid("Kein Link");
        }
        String s = text.trim();
        if (s.isEmpty() || s.length() > 300) {
            throw new Invalid("Das ist kein Nolphin-Link");
        }
        URI uri;
        try {
            uri = new URI(s);
        } catch (Exception e) {
            throw new Invalid("Das ist kein Nolphin-Link");
        }
        String scheme = uri.getScheme();
        Map<String, String> q = query(uri.getRawQuery());
        String authority;
        if ("http".equals(scheme)) {
            authority = uri.getRawAuthority();
        } else if ("nolphin".equals(scheme) && "connect".equals(uri.getHost())) {
            authority = q.get("host");
        } else {
            throw new Invalid("Das ist kein Nolphin-Link");
        }
        if (authority == null) {
            throw new Invalid("Im Link fehlt die Adresse des PCs");
        }
        int colon = authority.lastIndexOf(':');
        if (colon <= 0 || colon == authority.length() - 1) {
            throw new Invalid("Im Link fehlt der Port");
        }
        String host = authority.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(authority.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new Invalid("Der Port im Link ist ungültig");
        }
        if (port < 1 || port > 65535) {
            throw new Invalid("Der Port im Link ist ungültig");
        }
        int[] ip = ipv4(host);
        if (ip == null) {
            throw new Invalid("Die Adresse im Link ist keine IPv4-Adresse");
        }
        if (!privateAddress(ip)) {
            throw new Invalid("Die Adresse gehört zu keinem privaten Netz (nur WLAN oder LAN zu Hause sind erlaubt)");
        }
        String token = q.get("t");
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new Invalid("Der Schlüssel im Link ist ungültig");
        }
        String v = q.get("v");
        if (v != null && !v.equals(String.valueOf(VERSION))) {
            throw new Invalid("Der Code hat Version " + v + ", die App kann nur Version " + VERSION
                    + ". Bitte App und Nolphin auf den gleichen Stand bringen");
        }
        long expires = 0;
        String e = q.get("e");
        if (e != null) {
            try {
                expires = Long.parseLong(e);
            } catch (NumberFormatException ex) {
                throw new Invalid("Die Ablaufzeit im Link ist ungültig");
            }
            if (expires > 0 && nowSeconds > expires + CLOCK_SKEW) {
                throw new Invalid("Der QR-Code ist abgelaufen. Am PC in Nolphin einen neuen Code erzeugen");
            }
        }
        return new Link(host, port, token, expires);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) {
            return m;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                m.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    private static int[] ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] ip = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) {
                return null;
            }
            for (char c : parts[i].toCharArray()) {
                if (c < '0' || c > '9') {
                    return null;
                }
            }
            ip[i] = Integer.parseInt(parts[i]);
            if (ip[i] > 255) {
                return null;
            }
        }
        return ip;
    }

    /** 10/8, 172.16/12, 192.168/16, Link-Local 169.254/16 und Loopback 127/8. */
    private static boolean privateAddress(int[] ip) {
        return ip[0] == 10
                || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31)
                || (ip[0] == 192 && ip[1] == 168)
                || (ip[0] == 169 && ip[1] == 254)
                || ip[0] == 127;
    }
}
