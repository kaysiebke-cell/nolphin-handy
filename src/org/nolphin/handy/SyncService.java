package org.nolphin.handy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hält die Verbindung zu Nolphin am PC: wartet, bis der PC eine Datei in
 * "Handy" legt, lädt sie nach Downloads/Nolphin und sendet auf Wunsch
 * Dateien vom Handy an den PC.
 */
public class SyncService extends Service {
    static final String ACTION_STOP = "org.nolphin.handy.STOP";
    static final String ACTION_UPLOAD = "org.nolphin.handy.UPLOAD";
    static final String ACTION_ALL = "org.nolphin.handy.ALL";
    static final String ACTION_CANCEL = "org.nolphin.handy.CANCEL";
    static final String ACTION_FETCH = "org.nolphin.handy.FETCH";

    private static final String CH_STATE = "state";
    private static final String CH_FILES = "files";
    private static final int ID_STATE = 1;

    /** Zustand für die Anzeige in MainActivity. */
    static volatile String status = "Nicht verbunden";
    /** Laufende Übertragung für die Anzeige, zum Beispiel "Lade Bild.jpg: 45 %"; leer, wenn keine läuft. */
    static volatile String transfer = "";
    /** Fortschritt der laufenden Übertragung für die Anzeige (Name, erledigt, gesamt in Bytes; gesamt 0 = unbekannt). */
    static volatile String transferName = "";
    static volatile boolean transferDown;
    static volatile long transferDone, transferTotal;
    /** Dateien im Ordner „Handy“ am PC (zuletzt von /wait gemeldet). */
    static volatile RemoteFile[] files = new RemoteFile[0];
    /** Wird gesetzt, wenn der Nutzer die laufende Übertragung abbricht. */
    private static volatile boolean cancelRequested;
    private static final ArrayDeque<String> events = new ArrayDeque<>();

    static synchronized void event(String text) {
        events.addFirst(text);
        while (events.size() > 30) {
            events.removeLast();
        }
    }

    static synchronized String eventsText() {
        StringBuilder b = new StringBuilder();
        for (String e : events) {
            b.append(e).append('\n');
        }
        return b.toString();
    }

    private volatile boolean stop;
    private volatile boolean forceAll;
    private volatile boolean reconnect;                  // neuer Schlüssel: die laufende Wartezeit abbrechen
    private volatile HttpURLConnection waiting;         // die gerade laufende Anfrage an /wait
    private long lastProgress;
    private final java.util.Set<String> offered = new java.util.HashSet<>();   // schon angebotene Dateien (Rückfrage)
    private Thread worker;
    private int nextNotification = 100;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannels();
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stop = true;
            status = "Nicht verbunden";
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(ID_STATE, stateNotification(status), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (action == null && worker != null && worker.isAlive()) {
            // Die App wurde (neu) verbunden: die alte Wartezeit mit dem alten Schlüssel sofort beenden
            reconnect = true;
            HttpURLConnection w = waiting;
            if (w != null) {
                w.disconnect();
            }
        }
        if (ACTION_CANCEL.equals(action)) {
            cancelRequested = true;
        } else if (ACTION_ALL.equals(action)) {
            forceAll = true;
        } else if (ACTION_FETCH.equals(action) && intent != null) {
            final String name = intent.getStringExtra("name");
            final long size = intent.getLongExtra("size", -1);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    fetchOne(name, size);
                }
            }, "nolphin-fetch").start();
        } else if (ACTION_UPLOAD.equals(action) && intent != null) {
            final List<Uri> uris = urisOf(intent);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    upload(uris);
                }
            }, "nolphin-upload").start();
        }
        if (worker == null || !worker.isAlive()) {
            stop = false;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "nolphin-sync");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stop = true;
        super.onDestroy();
    }

    // ---- Benachrichtigungen -------------------------------------------------------------

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_STATE, "Verbindung", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_FILES, "Dateien", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private Notification stateNotification(String text) {
        Notification.Builder b = new Notification.Builder(this, CH_STATE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Nolphin Handy")
                .setContentText(transfer.isEmpty() ? text : transfer)
                .setOngoing(true);
        if (!transfer.isEmpty()) {
            Intent cancel = new Intent(this, SyncService.class).setAction(ACTION_CANCEL);
            PendingIntent pi = PendingIntent.getForegroundService(this, 1, cancel,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Abbrechen", pi).build());
        }
        return b.build();
    }

    /** Fortschritt anzeigen (höchstens alle 400 ms) und auf Abbruch prüfen. */
    private void progress(String verb, String name, long done, long total) throws IOException {
        if (cancelRequested) {
            throw new IOException("abgebrochen");
        }
        long now = System.currentTimeMillis();
        if (now - lastProgress < 400 && done < total) {
            return;
        }
        lastProgress = now;
        transferName = name;
        transferDown = "Lade".equals(verb);
        transferDone = done;
        transferTotal = total;
        transfer = verb + " " + name + (total > 0 ? ": " + (done * 100 / total) + " %" : "");
        getSystemService(NotificationManager.class).notify(ID_STATE, stateNotification(status));
    }

    private void endTransfer() {
        transfer = "";
        transferName = "";
        transferDone = 0;
        transferTotal = 0;
        cancelRequested = false;
        getSystemService(NotificationManager.class).notify(ID_STATE, stateNotification(status));
    }

    private void setStatus(String text) {
        if (!text.equals(status)) {
            status = text;
            getSystemService(NotificationManager.class).notify(ID_STATE, stateNotification(text));
        }
    }

    private void notifyFile(String title, String text) {
        Notification n = new Notification.Builder(this, CH_FILES)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(nextNotification++, n);
        event(title + ": " + text);
    }

    // ---- PC -> Handy -----------------------------------------------------------------------

    private void loop() {
        String version = "";
        while (!stop) {
            String host = Conn.host(this);
            String token = Conn.token(this);
            if (host == null || token == null) {
                setStatus("Nicht verbunden");
                return;
            }
            try {
                URL url = new URL("http://" + host + "/wait?t=" + Uri.encode(token) + "&v=" + Uri.encode(version));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(40000);
                waiting = c;
                int code = c.getResponseCode();
                if (code == 403) {
                    c.disconnect();
                    setStatus("Schlüssel abgelaufen: QR-Code am PC neu scannen");
                    sleep(5000);
                    continue;
                }
                if (code != 200) {
                    c.disconnect();
                    setStatus("PC antwortet mit Fehler " + code);
                    sleep(5000);
                    continue;
                }
                JSONObject o = new JSONObject(readAll(c.getInputStream()));
                c.disconnect();
                version = o.getString("v");
                setStatus("Verbunden mit " + host);
                handleFiles(host, token, o.getJSONArray("files"));
            } catch (Exception e) {
                version = "";
                if (reconnect) {
                    reconnect = false;   // absichtlich beendet: sofort mit den neuen Daten weitermachen
                    continue;
                }
                setStatus("PC nicht erreichbar, neuer Versuch …");
                sleep(5000);
            } finally {
                waiting = null;
            }
        }
    }

    private void handleFiles(String host, String token, JSONArray files) throws Exception {
        Set<String> seen = Conn.seen(this);
        boolean first = !Conn.seeded(this);
        boolean all = forceAll;
        forceAll = false;
        RemoteFile[] list = new RemoteFile[files.length()];
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            list[i] = new RemoteFile(f.getString("n"), f.getLong("s"));
        }
        SyncService.files = list;
        for (int i = 0; i < files.length() && !stop; i++) {
            JSONObject f = files.getJSONObject(i);
            String name = f.getString("n");
            long size = f.getLong("s");
            String key = name + "|" + size;
            if (first) {
                seen.add(key);   // beim ersten Verbinden nichts Altes laden
                continue;
            }
            if (!all && seen.contains(key)) {
                continue;
            }
            if (!all && !Conn.autoLoad(this)) {
                if (offered.add(key)) {
                    offer(name, size);
                }
                continue;
            }
            fetch(host, token, name, size, seen);
        }
        if (first) {
            Conn.setSeeded(this);
        }
        Conn.saveSeen(this, seen);
    }

    /** Lädt eine Datei und meldet das Ergebnis; merkt sich Erfolg und Abbruch, damit sie nicht erneut geladen wird. */
    private void fetch(String host, String token, String name, long size, Set<String> seen) {
        String key = name + "|" + size;
        cancelRequested = false;
        Uri saved = null;
        try {
            saved = download(host, token, name);
        } finally {
            boolean wasCancelled = cancelRequested;
            endTransfer();
            if (wasCancelled) {
                seen.add(key);   // abgebrochen: nicht von selbst erneut laden
                Conn.saveSeen(this, seen);
                Store.addHistory(this, true, name, size, false, "abgebrochen");
                notifyFile("Laden abgebrochen", name);
                return;
            }
        }
        if (saved != null) {
            seen.add(key);
            Conn.saveSeen(this, seen);
            Store.setUri(this, key, saved.toString());
            Store.addHistory(this, true, name, size, true, "");
            notifyFile("Vom PC empfangen", name);
        } else {
            Store.addHistory(this, true, name, size, false, "fehlgeschlagen");
            notifyFile("Laden fehlgeschlagen", name);
        }
    }

    /** Rückfrage statt sofortigem Laden: Benachrichtigung mit der Aktion "Laden". */
    private void offer(String name, long size) {
        Intent i = new Intent(this, SyncService.class).setAction(ACTION_FETCH)
                .putExtra("name", name).putExtra("size", size);
        PendingIntent pi = PendingIntent.getForegroundService(this, nextNotification, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_FILES)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Datei vom PC bereit")
                .setContentText(name + " (" + humanSize(size) + ")")
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.stat_sys_download), "Laden", pi).build())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(nextNotification++, n);
        event("Bereit zum Laden: " + name);
    }

    private void fetchOne(String name, long size) {
        String host = Conn.host(this);
        String token = Conn.token(this);
        if (name == null || host == null || token == null) {
            return;
        }
        fetch(host, token, name, size, Conn.seen(this));
    }

    private static String humanSize(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024 * 1024) {
            return (b / 1024) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
    }

    private Uri download(String host, String token, String name) {
        Uri target = null;
        try {
            URL url = new URL("http://" + host + "/dl?t=" + Uri.encode(token) + "&n=" + Uri.encode(name));
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(30000);
            if (c.getResponseCode() != 200) {
                c.disconnect();
                return null;
            }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, mimeOf(name));
            cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/" + Conn.folder(this));
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            target = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (target == null) {
                c.disconnect();
                return null;
            }
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream(); OutputStream out = getContentResolver().openOutputStream(target)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                progress("Lade", name, 0, total);
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    progress("Lade", name, done, total);
                }
                out.flush();
            }
            c.disconnect();
            cv.clear();
            cv.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(target, cv, null, null);
            return target;
        } catch (Exception e) {
            if (target != null) {
                getContentResolver().delete(target, null, null);
            }
            return null;
        }
    }

    // ---- Handy -> PC -----------------------------------------------------------------------

    private List<Uri> urisOf(Intent intent) {
        List<Uri> list = new ArrayList<>();
        ClipData clip = intent.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) {
                    list.add(u);
                }
            }
        }
        return list;
    }

    private void upload(List<Uri> uris) {
        String host = Conn.host(this);
        String token = Conn.token(this);
        if (host == null || token == null) {
            notifyFile("Senden nicht möglich", "Erst den QR-Code vom PC scannen");
            return;
        }
        for (Uri u : uris) {
            String name = displayName(u);
            java.io.File temp = null;
            long sentSize = 0;
            try {
                // Immer erst zwischenspeichern: Anbieter nennen die Größe oft falsch oder gar nicht
                // (zum Beispiel die Galerie von MIUI), der Server braucht aber die genaue Länge.
                temp = java.io.File.createTempFile("senden", ".tmp", getCacheDir());
                try (InputStream src = getContentResolver().openInputStream(u); OutputStream dst = new java.io.FileOutputStream(temp)) {
                    if (src == null) {
                        throw new IOException("Datei nicht lesbar");
                    }
                    copy(src, dst);
                }
                long size = temp.length();
                sentSize = size;
                InputStream in = new java.io.FileInputStream(temp);
                URL url = new URL("http://" + host + "/up?t=" + Uri.encode(token) + "&n=" + Uri.encode(name));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(120000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(size);
                long sent = 0;
                cancelRequested = false;
                progress("Sende", name, 0, size);
                try (InputStream src = in; OutputStream out = c.getOutputStream()) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = src.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        sent += n;
                        progress("Sende", name, sent, size);
                    }
                }
                if (sent != size) {
                    throw new IOException("Größe stimmt nicht (" + sent + " statt " + size + " Bytes)");
                }
                int code = c.getResponseCode();
                c.disconnect();
                if (code == 200) {
                    Store.addHistory(this, false, name, sentSize, true, "");
                    notifyFile("An den PC gesendet", name);
                } else if (code == 403) {
                    Store.addHistory(this, false, name, sentSize, false, "Schlüssel abgelaufen");
                    notifyFile("Senden fehlgeschlagen", name + ": Schlüssel abgelaufen, QR-Code neu scannen");
                } else {
                    Store.addHistory(this, false, name, sentSize, false, "PC antwortet mit Fehler " + code);
                    notifyFile("Senden fehlgeschlagen", name + ": PC antwortet mit Fehler " + code);
                }
            } catch (Exception e) {
                if (cancelRequested) {
                    Store.addHistory(this, false, name, sentSize, false, "abgebrochen");
                    notifyFile("Senden abgebrochen", name);
                } else {
                    // Den Grund nennen, damit sich der Fehler eingrenzen lässt
                    Store.addHistory(this, false, name, sentSize, false, e.getClass().getSimpleName());
                    notifyFile("Senden fehlgeschlagen", name + ": " + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
                }
            } finally {
                endTransfer();
                if (temp != null) {
                    temp.delete();
                }
            }
        }
    }

    private String displayName(Uri u) {
        String name = null;
        try (Cursor cur = getContentResolver().query(u, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                name = cur.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            name = u.getLastPathSegment();
        }
        return name != null ? name : "Datei";
    }

    // ---- Hilfen ----------------------------------------------------------------------------

    private static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (m != null) {
                return m;
            }
        }
        return "application/octet-stream";
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        copy(in, b);
        return b.toString("UTF-8");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
