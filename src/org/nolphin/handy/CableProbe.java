package org.nolphin.handy;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Liest die Netzschnittstellen des Handys und ob ein USB-Kabel steckt. Die Regeln dazu stehen in {@link CableNet}. */
final class CableProbe {
    private CableProbe() {}

    /** Alle aktiven Schnittstellen mit IPv4-Adresse. */
    static List<CableNet.Nic> nics() {
        List<CableNet.Nic> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni == null || !ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address) {
                        out.add(new CableNet.Nic(ni.getName(), a.getHostAddress(), ia.getNetworkPrefixLength()));
                    }
                }
            }
        } catch (Exception ignored) {
            // ohne Zugriff auf die Schnittstellen gibt es einfach keine Kabel-Anzeige
        }
        return out;
    }

    /** Steckt ein USB-Kabel (Ladegerät oder PC)? Aus der Ladeanzeige, ohne Berechtigung. */
    static boolean usbPlugged(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            return i != null && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == BatteryManager.BATTERY_PLUGGED_USB;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
