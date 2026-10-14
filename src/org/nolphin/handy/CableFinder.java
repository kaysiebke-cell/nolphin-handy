package org.nolphin.handy;

import android.net.Uri;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Sucht den PC im USB-Kabelnetz (USB-Tethering). Das Handy kennt die Adresse des PCs dort nicht; die App probiert deshalb die
 * Adressen des Netzes auf dem gemerkten Port durch und fragt jede erreichbare Adresse mit dem gemerkten Schlüssel und dem
 * Fingerabdruck. Nur ein PC, der beides erfüllt, gilt als gefunden: ein fremdes Gerät im Kabelnetz kommt nicht durch.
 */
final class CableFinder {
    private CableFinder() {}

    private static final int CONNECT_MS = 350;
    private static final int VERIFY_MS = 3000;
    private static final long TOTAL_MS = 9000;

    /** @return "ip:port" des PCs im Kabelnetz oder null */
    static String find(CableNet.Nic nic, final int port, final String token, final String pin) {
        List<String> candidates = HostSwitch.candidates(nic);
        if (candidates.isEmpty() || port < 1 || token == null || pin == null) {
            return null;
        }
        ExecutorService pool = Executors.newFixedThreadPool(48);
        ExecutorCompletionService<String> done = new ExecutorCompletionService<>(pool);
        try {
            for (final String ip : candidates) {
                done.submit(new Callable<String>() {
                    @Override
                    public String call() {
                        return probe(ip, port, token, pin);
                    }
                });
            }
            long end = System.currentTimeMillis() + TOTAL_MS;
            for (int i = 0; i < candidates.size(); i++) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    break;
                }
                Future<String> f = done.poll(left, TimeUnit.MILLISECONDS);
                if (f == null) {
                    break;
                }
                try {
                    String hit = f.get();
                    if (hit != null) {
                        return hit;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return null;
    }

    private static String probe(String ip, int port, String token, String pin) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), CONNECT_MS);
        } catch (Exception e) {
            return null;
        }
        String host = ip + ":" + port;
        try {
            URL url = new URL("https://" + host + "/info?t=" + Uri.encode(token));
            HttpURLConnection c = Secure.connect(url, pin);
            c.setConnectTimeout(VERIFY_MS);
            c.setReadTimeout(VERIFY_MS);
            try {
                return c.getResponseCode() == 200 ? host : null;
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;   // falscher Fingerabdruck, kein TLS oder kein Nolphin: kein Treffer
        }
    }
}
