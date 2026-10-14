package org.nolphin.handy;

import java.util.ArrayList;
import java.util.List;

/**
 * Regeln für den Wechsel zwischen WLAN und USB-Kabelnetz (ohne Android, damit sie sich testen lassen). Die App merkt sich den
 * PC mit Schlüssel und Fingerabdruck aus dem QR-Code. Geht der gespeicherte Weg nicht mehr und gibt es ein Kabelnetz, sucht
 * sie den PC dort selbst; ist das Kabel weg, kehrt sie zur Adresse aus dem QR-Code (WLAN) zurück. Ein neuer QR-Code ist dafür
 * nicht nötig, weil Nolphin am PC auf allen privaten Netzen mit derselben Sitzung hört.
 */
final class HostSwitch {
    private HostSwitch() {}

    enum Action { NONE, FIND_ON_CABLE, RESTORE_HOME }

    /** Was tun, wenn der PC unter {@code current} nicht erreichbar ist? {@code home}: Adresse aus dem QR-Code, falls gerade eine andere gilt. */
    static Action decide(List<CableNet.Nic> nics, String current, String home) {
        boolean alt = home != null && !home.isEmpty() && !home.equals(current);
        CableNet.Nic cable = CableNet.find(nics);
        if (cable == null) {
            return alt ? Action.RESTORE_HOME : Action.NONE;
        }
        return Action.FIND_ON_CABLE;
    }

    /** Port aus "ip:port" oder -1. */
    static int portOf(String host) {
        if (host == null) {
            return -1;
        }
        int colon = host.lastIndexOf(':');
        if (colon < 0 || colon == host.length() - 1) {
            return -1;
        }
        try {
            int p = Integer.parseInt(host.substring(colon + 1));
            return p >= 1 && p <= 65535 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Die Adressen, auf denen der PC im Kabelnetz liegen kann: das Netz des Handys, höchstens ein /24 (254 Adressen), ohne
     * die eigene Adresse, Netz- und Sendeadresse.
     */
    static List<String> candidates(CableNet.Nic nic) {
        List<String> out = new ArrayList<>();
        int[] ip = nic == null ? null : CableNet.ipv4(nic.ip);
        if (ip == null || !CableNet.isPrivate(ip) || nic.prefix < 8 || nic.prefix > 30) {
            return out;
        }
        long own = ((long) ip[0] << 24) | (ip[1] << 16) | (ip[2] << 8) | ip[3];
        int prefix = Math.max(nic.prefix, 24);   // größere Netze nur im eigenen /24 absuchen
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long net = own & mask;
        long bcast = net | (~mask & 0xFFFFFFFFL);
        for (long a = net + 1; a < bcast; a++) {
            if (a == own) {
                continue;
            }
            out.add(((a >> 24) & 255) + "." + ((a >> 16) & 255) + "." + ((a >> 8) & 255) + "." + (a & 255));
        }
        return out;
    }
}
