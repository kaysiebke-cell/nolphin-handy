package org.nolphin.handy;

import java.util.List;

/**
 * Erkennt das USB-Kabelnetz (USB-Tethering) ohne Android-Klassen, damit sich die Regeln testen lassen. Beim USB-Tethering
 * legt das Handy eine eigene Netzschnittstelle zum PC an (Namen wie rndis0, usb0, ncm0). Die App benutzt das Kabel nicht
 * selbst; sie erkennt nur, dass es ein Kabelnetz gibt, und zeigt es an. Die Verbindung läuft wie sonst über https mit dem
 * Schlüssel und dem Fingerabdruck aus dem QR-Code.
 */
final class CableNet {
    private CableNet() {}

    /** Eine Netzschnittstelle des Handys: Name, IPv4-Adresse (Text) und Länge des Netzanteils (zum Beispiel 24). */
    static final class Nic {
        final String name;
        final String ip;
        final int prefix;

        Nic(String name, String ip, int prefix) {
            this.name = name;
            this.ip = ip;
            this.prefix = prefix;
        }
    }

    /** Ist das ein USB-Kabelnetz (und nicht WLAN, Mobilfunk, Hotspot oder Loopback)? */
    static boolean isCableName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.startsWith("rndis") || n.startsWith("usb") || n.startsWith("ncm") || n.startsWith("enx");
    }

    static int[] ipv4(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split("\\.", -1);
        if (p.length != 4) {
            return null;
        }
        int[] r = new int[4];
        for (int i = 0; i < 4; i++) {
            if (p[i].isEmpty() || p[i].length() > 3) {
                return null;
            }
            for (int k = 0; k < p[i].length(); k++) {
                if (p[i].charAt(k) < '0' || p[i].charAt(k) > '9') {
                    return null;
                }
            }
            r[i] = Integer.parseInt(p[i]);
            if (r[i] > 255) {
                return null;
            }
        }
        return r;
    }

    static boolean isPrivate(int[] ip) {
        return ip != null && (ip[0] == 10 || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) || (ip[0] == 192 && ip[1] == 168));
    }

    /** Das erste Kabelnetz in der Liste (nur mit privater IPv4-Adresse) oder null. */
    static Nic find(List<Nic> all) {
        if (all == null) {
            return null;
        }
        for (Nic n : all) {
            if (n != null && isCableName(n.name) && isPrivate(ipv4(n.ip))) {
                return n;
            }
        }
        return null;
    }

    /** Liegt {@code host} (Text "ip" oder "ip:port") im Netz der Schnittstelle? */
    static boolean inNet(Nic nic, String host) {
        if (nic == null || host == null) {
            return false;
        }
        String h = host;
        int colon = h.lastIndexOf(':');
        if (colon > 0) {
            h = h.substring(0, colon);
        }
        int[] a = ipv4(nic.ip);
        int[] b = ipv4(h);
        if (a == null || b == null || nic.prefix < 8 || nic.prefix > 32) {
            return false;
        }
        long x = ((long) a[0] << 24) | (a[1] << 16) | (a[2] << 8) | a[3];
        long y = ((long) b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3];
        long mask = nic.prefix == 32 ? 0xFFFFFFFFL : (0xFFFFFFFFL << (32 - nic.prefix)) & 0xFFFFFFFFL;
        return (x & mask) == (y & mask);
    }

    /** Läuft die Verbindung zum PC über das Kabel? */
    static boolean viaCable(List<Nic> all, String host) {
        Nic n = find(all);
        return n != null && inNet(n, host);
    }

    /** Welche Karte zeigt die Startseite? */
    enum State { NONE, CABLE_READY, CONNECTED_VIA_CABLE, CABLE_PLUGGED_NO_NET }

    static State state(List<Nic> all, boolean usbPlugged, boolean connected, String host) {
        Nic n = find(all);
        if (connected && n != null && inNet(n, host)) {
            return State.CONNECTED_VIA_CABLE;
        }
        if (connected) {
            return State.NONE;
        }
        if (n != null) {
            return State.CABLE_READY;
        }
        return usbPlugged ? State.CABLE_PLUGGED_NO_NET : State.NONE;
    }
}
