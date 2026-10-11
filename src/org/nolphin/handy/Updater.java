package org.nolphin.handy;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aktualisiert die App selbst: Nolphin am PC liefert seine Handy-App unter /apk. Ist sie neuer als die
 * installierte, bietet die Startseite „Aktualisieren“ an; die Installation läuft über den
 * Paketinstaller von Android (gleiche Signatur: Daten und Verbindung bleiben erhalten).
 */
final class Updater {
    private Updater() {}

    private static final long RECHECK_MS = 30 * 60 * 1000L;

    /** Name der neuen Version (z. B. "2.1") oder null, wenn keine neuere Version bereitliegt. */
    static volatile String availableName;
    static volatile String message = "";
    private static volatile boolean busy;
    private static long lastCheck;

    private static File apkFile(Context c) {
        return new File(c.getCacheDir(), "update.apk");
    }

    /** Fragt im Hintergrund am PC nach einer neueren Version. */
    static void check(final Context app) {
        if (busy || !Conn.connected(app) || System.currentTimeMillis() - lastCheck < RECHECK_MS) {
            return;
        }
        busy = true;
        lastCheck = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    download(app);
                } catch (Exception e) {
                    lastCheck = 0;   // beim nächsten Mal erneut versuchen
                } finally {
                    busy = false;
                }
            }
        }, "update-check").start();
    }

    private static void download(Context app) throws Exception {
        String host = Conn.host(app);
        String token = Conn.token(app);
        if (host == null || token == null) {
            return;
        }
        HttpURLConnection h = (HttpURLConnection) new URL("http://" + host + "/apk?t=" + token).openConnection();
        h.setConnectTimeout(5000);
        h.setReadTimeout(15000);
        try {
            if (h.getResponseCode() != 200) {
                availableName = null;
                return;
            }
            File tmp = new File(app.getCacheDir(), "update.part");
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 50L * 1024 * 1024) {
                        throw new Exception("zu groß");
                    }
                    out.write(buf, 0, n);
                }
            }
            PackageInfo offered = app.getPackageManager().getPackageArchiveInfo(tmp.getPath(), 0);
            PackageInfo mine = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            if (offered != null && app.getPackageName().equals(offered.packageName)
                    && offered.getLongVersionCode() > mine.getLongVersionCode()) {
                File dest = apkFile(app);
                dest.delete();
                if (tmp.renameTo(dest)) {
                    availableName = offered.versionName;
                    return;
                }
            }
            tmp.delete();
            availableName = null;
        } finally {
            h.disconnect();
        }
    }

    /** Startet die Installation; fragt vorher einmal nach der Erlaubnis „Apps installieren“. */
    static void install(Activity a) {
        File apk = apkFile(a);
        if (availableName == null || !apk.isFile()) {
            return;
        }
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            message = "Erlaube Nolphin Handy bitte, Apps zu installieren, und tippe danach erneut auf Aktualisieren.";
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }
        try {
            PackageInstaller pi = a.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(params);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = s.openWrite("nolphin-handy", 0, apk.length())) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    s.fsync(out);
                }
                Intent result = new Intent(a, Result.class);
                PendingIntent pending = PendingIntent.getBroadcast(a, id, result,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                s.commit(pending.getIntentSender());
            }
            message = "Installation läuft …";
        } catch (Exception e) {
            message = "Aktualisieren fehlgeschlagen: " + e.getClass().getSimpleName();
        }
    }

    /** Antwort des Paketinstallers: Rückfrage anzeigen oder Ergebnis merken. */
    public static final class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String text = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                message = "Aktualisieren fehlgeschlagen" + (text == null ? "" : ": " + text);
            }
        }
    }
}
