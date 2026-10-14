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
    static final String ACTION_FETCH_DIR = "org.nolphin.handy.FETCH_DIR";

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
    /** Läuft der Dienst in diesem Prozess? Das Widget zeigt „verbunden“ nur, solange das stimmt. */
    static volatile boolean running;
    /** Zählt abgeschlossene Übertragungen vom PC; der Dateimanager lädt dann seine Liste neu (eingefügte Dateien erscheinen sofort). */
    static volatile int finished;
    /** Wann sich der Zustand zuletzt geändert hat (für das Widget: veraltete Übertragungen nicht anzeigen). */
    static volatile long stateTime;
    private static final FetchGuard GUARD = new FetchGuard(3000);
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
            running = false;
            touch();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        touch();
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
            final String dir = intent.getStringExtra("dir");
            final String to = intent.getStringExtra("to");   // Zielordner im Handy (Einfügen) oder null
            final String fkey = new RemoteFile(dir == null ? "" : dir, name == null ? "" : name, size).key();
            if (name != null && GUARD.beginFetch(fkey)) {   // dieselbe Datei wird nicht doppelt geladen
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fetchOne(dir == null ? "" : dir, name, size, to);
                        } finally {
                            GUARD.endFetch(fkey);
                        }
                    }
                }, "nolphin-fetch").start();
            }
        } else if (ACTION_FETCH_DIR.equals(action) && intent != null) {
            final String fdir = intent.getStringExtra("dir");
            final String fname = intent.getStringExtra("name");
            final String fto = intent.getStringExtra("to");
            if (fdir != null && FolderFetch.safeName(fname) && GUARD.beginFetch(FolderFetch.dirKey(fdir))) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fetchFolder(fdir, fname, fto);
                        } finally {
                            GUARD.endFetch(FolderFetch.dirKey(fdir));
                        }
                    }
                }, "nolphin-fetch-folder").start();
            }
        } else if (ACTION_UPLOAD.equals(action) && intent != null) {
            final Intent in = intent;
            if (GUARD.beginUpload(uploadKey(in), System.currentTimeMillis())) {   // doppelt ausgelöst: ignorieren
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        upload(jobsOf(in));
                    }
                }, "nolphin-upload").start();
            }
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
        running = false;
        touch();
        super.onDestroy();
    }

    /** Merkt die Änderung und aktualisiert das Widget (ohne Netzwerk, nur aus dem vorhandenen Zustand). */
    private void touch() {
        stateTime = System.currentTimeMillis();
        NolphinWidget.refresh(getApplicationContext());
    }

    /** Kennung eines Uploads (Adressen und Pfade), um doppelt ausgelöste zu erkennen. */
    private static String uploadKey(Intent in) {
        StringBuilder b = new StringBuilder();
        ClipData clip = in.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                b.append(clip.getItemAt(i).getUri()).append('|');
            }
        }
        String[] paths = in.getStringArrayExtra("paths");
        if (paths != null) {
            for (String p : paths) {
                b.append(p).append('|');
            }
        }
        return b.toString();
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
        if (now - lastWidget >= 1000 || done >= total) {   // das Widget höchstens einmal pro Sekunde
            lastWidget = now;
            touch();
        }
    }

    private long lastWidget;

    private void endTransfer() {
        finished++;
        transfer = "";
        transferName = "";
        transferDone = 0;
        transferTotal = 0;
        cancelRequested = false;
        getSystemService(NotificationManager.class).notify(ID_STATE, stateNotification(status));
        touch();
    }

    private void setStatus(String text) {
        if (!text.equals(status)) {
            status = text;
            getSystemService(NotificationManager.class).notify(ID_STATE, stateNotification(text));
            touch();
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
                URL url = new URL("https://" + host + "/wait?t=" + Uri.encode(token) + "&v=" + Uri.encode(version));
                HttpURLConnection c = Secure.connect(url, Conn.pin(this));
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
                if (Secure.isPinMismatch(e)) {
                    // Nicht still weiterversuchen: entweder neuer Code am PC oder jemand gibt sich als der PC aus
                    setStatus("Schlüssel ungültig: Das Zertifikat des PCs passt nicht zum QR-Code. QR-Code am PC neu scannen");
                    sleep(15000);
                    continue;
                }
                if (switchWay()) {
                    continue;   // anderer Weg gefunden (Kabelnetz) oder zurück im WLAN: sofort neu versuchen
                }
                setStatus("PC nicht erreichbar, neuer Versuch …");
                sleep(5000);
            } finally {
                waiting = null;
            }
        }
    }

    private long lastWayTry;

    /**
     * Der PC antwortet nicht: Gibt es ein USB-Kabelnetz, sucht die App den PC dort selbst (gleicher Schlüssel, gleiches
     * Zertifikat, kein neuer QR-Code); ist das Kabel weg, geht sie zur Adresse aus dem QR-Code zurück. Höchstens alle 15 Sekunden.
     */
    private boolean switchWay() {
        long now = System.currentTimeMillis();
        if (now - lastWayTry < 15000) {
            return false;
        }
        lastWayTry = now;
        String cur = Conn.host(this);
        java.util.List<CableNet.Nic> nics = CableProbe.nics();
        switch (HostSwitch.decide(nics, cur, Conn.homeHost(this))) {
            case RESTORE_HOME:
                Conn.restoreHome(this);
                return true;
            case FIND_ON_CABLE:
                setStatus("PC nicht erreichbar, suche im Kabelnetz …");
                String found = CableFinder.find(CableNet.find(nics), HostSwitch.portOf(cur), Conn.token(this), Conn.pin(this));
                if (found != null && !found.equals(cur)) {
                    Conn.useAltHost(this, found);
                    return true;
                }
                return false;
            default:
                return false;
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
        touch();
        int newOffers = 0;
        String firstOffer = null;
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
                    newOffers++;
                    if (firstOffer == null) {
                        firstOffer = name;
                    }
                }
                continue;
            }
            fetch(host, token, name, size, seen);
        }
        if (newOffers > 0) {
            StatusNote.post(StatusNote.fresh(newOffers, firstOffer), false, Host.TAB_FROM_PC, System.currentTimeMillis());
        }
        if (first) {
            Conn.setSeeded(this);
        }
        Conn.saveSeen(this, seen);
    }

    /** Lädt eine Datei und meldet das Ergebnis; merkt sich Erfolg und Abbruch, damit sie nicht erneut geladen wird. */
    private void fetch(String host, String token, String name, long size, Set<String> seen) {
        fetch(host, token, "", name, size, seen, null);
    }

    private void fetch(String host, String token, String dir, String name, long size, Set<String> seen, String to) {
        String key = new RemoteFile(dir, name, size).key();
        cancelRequested = false;
        Uri saved = null;
        try {
            saved = download(host, token, dir, name, null, to);
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
            if (to == null) {   // eingefügte Dateien gehören dem Nutzer im Zielordner, nicht der Liste „Vom PC“
                seen.add(key);
                Conn.saveSeen(this, seen);
                Store.setUri(this, key, saved.toString());
            }
            Store.addHistory(this, true, name, size, true, "");
            notifyFile("Vom PC empfangen", name);
            StatusNote.post(StatusNote.arrived(name, to != null, 1), false, to != null ? Host.TAB_TO_PC : Host.TAB_FROM_PC, System.currentTimeMillis());
        } else {
            Store.addHistory(this, true, name, size, false, "fehlgeschlagen");
            notifyFile("Laden fehlgeschlagen", name);
            StatusNote.post("Laden fehlgeschlagen: " + name, true, Host.TAB_HISTORY, System.currentTimeMillis());
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

    /** Liest einen Ordner am PC samt Unterordnern (über /dir) und sammelt alle Dateien mit ihrem Zielunterordner. */
    private boolean listTree(String host, String token, String pcDir, String rel, int depth, List<FolderFetch.Item> out) {
        if (depth > FolderFetch.MAX_DEPTH || out.size() > FolderFetch.MAX_FILES) {
            return false;
        }
        out.add(new FolderFetch.Item(pcDir, null, -1, rel));   // der Ordner selbst, damit auch leere Ordner entstehen
        try {
            URL url = new URL("https://" + host + "/dir?t=" + Uri.encode(token) + RemoteFile.params(pcDir));
            HttpURLConnection h = Secure.connect(url, Conn.pin(this));
            h.setConnectTimeout(8000);
            h.setReadTimeout(15000);
            String body;
            try {
                if (h.getResponseCode() != 200) {
                    return false;
                }
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                try (InputStream in = h.getInputStream()) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        buf.write(b, 0, n);
                    }
                }
                body = buf.toString("UTF-8");
            } finally {
                h.disconnect();
            }
            JSONObject o = new JSONObject(body);
            JSONArray files = o.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                String n = f.getString("n");
                if (FolderFetch.safeName(n)) {
                    out.add(new FolderFetch.Item(pcDir, n, f.getLong("s"), rel));
                }
            }
            JSONArray dirs = o.getJSONArray("dirs");
            boolean ok = true;
            for (int i = 0; i < dirs.length() && !cancelRequested; i++) {
                String n = dirs.getJSONObject(i).getString("n");
                if (FolderFetch.safeName(n)) {
                    ok &= listTree(host, token, FolderFetch.child(pcDir, n), rel.isEmpty() ? n : rel + "/" + n, depth + 1, out);
                }
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lädt einen ganzen Ordner vom PC in {@code Download/Nolphin/<Ordner>} (oder den gewählten Zielordner), mit Unterordnern. */
    private void fetchFolder(String pcDir, String name, String to) {
        String host = Conn.host(this);
        String token = Conn.token(this);
        if (host == null || token == null) {
            return;
        }
        cancelRequested = false;
        List<FolderFetch.Item> items = new ArrayList<>();
        boolean complete = listTree(host, token, pcDir, name, 0, items);
        int ok = 0, failed = 0;
        long bytes = 0;
        boolean cancelled = false;
        String firstFailed = null;
        for (FolderFetch.Item it : items) {
            if (cancelRequested) {
                cancelled = true;
                break;
            }
            if (it.name == null) {   // ein Ordner: beim Einfügen in einen Handy-Ordner auch leer anlegen
                if (to != null && FolderFetch.safeRelative(it.rel)) {
                    java.io.File d = new java.io.File(to, it.rel);
                    if (!d.isDirectory() && !d.mkdirs()) {
                        failed++;
                    }
                }
                continue;
            }
            Uri saved = download(host, token, it.pcDir, it.name, it.rel, to);
            if (saved != null) {
                ok++;
                bytes += it.size;
            } else if (cancelRequested) {
                cancelled = true;
                break;
            } else {
                failed++;
                if (firstFailed == null) {
                    firstFailed = it.name;
                }
            }
        }
        if (!complete && !cancelled) {
            failed++;   // der Ordner ließ sich nicht ganz lesen (Fehler, zu tief oder zu viele Dateien)
        }
        endTransfer();
        String sum = FolderFetch.summary(ok, failed, cancelled) + (firstFailed != null ? " (zuerst: " + firstFailed + ")" : "");
        boolean good = failed == 0 && !cancelled;
        Store.addHistory(this, true, name + "/" + (good ? " (" + sum + ")" : ""), bytes, good, good ? "" : sum);
        notifyFile(good ? "Ordner vom PC empfangen" : "Ordner nicht ganz geladen", name + ": " + sum);
        if (!cancelled) {
            StatusNote.post(good ? StatusNote.arrived(name, to != null, ok) : name + " nicht ganz geladen: " + sum, !good,
                    good ? (to != null ? Host.TAB_TO_PC : Host.TAB_FROM_PC) : Host.TAB_HISTORY, System.currentTimeMillis());
        }
    }

    private void fetchOne(String dir, String name, long size, String to) {
        String host = Conn.host(this);
        String token = Conn.token(this);
        if (name == null || host == null || token == null) {
            return;
        }
        fetch(host, token, dir, name, size, Conn.seen(this), to);
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


    /** Findet einen Unterordner im gewählten Zielordner oder legt ihn an. */
    private Uri dirUnder(Uri treeUri, Uri parentDoc, String name) throws Exception {
        Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                android.provider.DocumentsContract.getDocumentId(parentDoc));
        try (android.database.Cursor cur = getContentResolver().query(children, new String[] {
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE }, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                if (name.equals(cur.getString(1)) && android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(cur.getString(2))) {
                    return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, cur.getString(0));
                }
            }
        }
        return android.provider.DocumentsContract.createDocument(getContentResolver(), parentDoc,
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR, name);
    }

    /**
     * Lädt eine Datei vom PC. {@code sub} ist ein Unterordner unterhalb des Zielordners ("Ordner/Unter") oder null.
     * Die Namen in {@code sub} müssen vorher mit {@link FolderFetch#safeRelative} geprüft sein.
     */
    private Uri download(String host, String token, String dir, String name, String sub, String to) {
        if (sub != null && !FolderFetch.safeRelative(sub)) {
            return null;
        }
        if (to != null) {
            return downloadInto(host, token, dir, name, sub, new java.io.File(to));
        }
        Uri target = null;
        try {
            URL url = new URL("https://" + host + "/dl?t=" + Uri.encode(token) + "&n=" + Uri.encode(name)
                    + RemoteFile.params(dir));
            HttpURLConnection c = Secure.connect(url, Conn.pin(this));
            c.setConnectTimeout(8000);
            c.setReadTimeout(30000);
            if (c.getResponseCode() != 200) {
                c.disconnect();
                return null;
            }
            ContentValues cv = new ContentValues();
            // Gewählter Zielordner (Handy oder SD-Karte); ohne Auswahl oder wenn er nicht mehr geht: Downloads
            boolean viaTree = false;
            String tree = Conn.targetTree(this);
            if (tree != null) {
                try {
                    Uri treeUri = Uri.parse(tree);
                    Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            android.provider.DocumentsContract.getTreeDocumentId(treeUri));
                    if (sub != null && !sub.isEmpty()) {
                        for (String part : sub.split("/")) {
                            parent = dirUnder(treeUri, parent, part);
                        }
                    }
                    target = android.provider.DocumentsContract.createDocument(getContentResolver(), parent,
                            mimeOf(name), name);
                    viaTree = target != null;
                } catch (Exception e) {
                    target = null;
                }
            }
            if (!viaTree) {
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mimeOf(name));
                cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/" + Conn.folder(this) + (sub == null || sub.isEmpty() ? "" : "/" + sub));
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                target = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            }
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
            if (!viaTree) {
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(target, cv, null, null);
            }
            return target;
        } catch (Exception e) {
            if (target != null) {
                getContentResolver().delete(target, null, null);
            }
            return null;
        }
    }

    /** Lädt eine Datei vom PC direkt in einen Ordner des Handys (Einfügen). Vorhandene Dateien werden nie überschrieben. */
    private Uri downloadInto(String host, String token, String dir, String name, String sub, java.io.File base) {
        java.io.File out = null;
        try {
            java.io.File folder = sub == null || sub.isEmpty() ? base : new java.io.File(base, sub);
            if (!folder.isDirectory() && !folder.mkdirs()) {
                return null;
            }
            URL url = new URL("https://" + host + "/dl?t=" + Uri.encode(token) + "&n=" + Uri.encode(name)
                    + RemoteFile.params(dir));
            HttpURLConnection c = Secure.connect(url, Conn.pin(this));
            c.setConnectTimeout(8000);
            c.setReadTimeout(30000);
            if (c.getResponseCode() != 200) {
                c.disconnect();
                return null;
            }
            out = FileOps.uniqueIn(folder, name);
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream(); OutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                progress("Lade", name, 0, total);
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    done += n;
                    progress("Lade", name, done, total);
                }
                os.flush();
            }
            c.disconnect();
            return Uri.fromFile(out);
        } catch (Exception e) {
            if (out != null) {
                out.delete();
            }
            return null;
        }
    }

    // ---- Handy -> PC -----------------------------------------------------------------------

    /** Eine Datei, die an den PC geht, bei Ordnern mit Unterordner (relativ) und dem Namen des obersten Ordners. */
    private static final class Job {
        final Uri uri;
        final String name;    // null: Name aus dem Anbieter lesen
        final String dir;     // Unterordner am PC ("Ordner1/Unter") oder null
        final String group;   // oberster Ordner (für die Sammelmeldung) oder null
        final boolean emptyDir;   // ein leerer Ordner: am PC nur anlegen, nichts hochladen

        Job(Uri uri, String name, String dir, String group) {
            this(uri, name, dir, group, false);
        }

        Job(Uri uri, String name, String dir, String group, boolean emptyDir) {
            this.uri = uri;
            this.name = name;
            this.dir = dir;
            this.group = group;
            this.emptyDir = emptyDir;
        }
    }

    private List<Job> jobsOf(Intent intent) {
        List<Job> list = new ArrayList<>();
        ClipData clip = intent.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) {
                    list.add(new Job(u, null, null, null));
                }
            }
        }
        String[] paths = intent.getStringArrayExtra("paths");
        if (paths != null) {
            Boolean folders = null;
            for (String path : paths) {
                java.io.File f = new java.io.File(path);
                if (!f.isDirectory()) {
                    list.add(new Job(Uri.fromFile(f), f.getName(), null, null));
                    continue;
                }
                if (folders == null) {
                    folders = pcAcceptsFolders();
                }
                if (folders) {
                    addFolder(list, f, f.getName(), f.getName());
                } else {
                    // Ältere Nolphin-Version am PC: der Ordner geht als ZIP-Datei
                    try {
                        java.io.File zip = FileOps.zip(f, getCacheDir());
                        list.add(new Job(Uri.fromFile(zip), zip.getName(), null, null));
                    } catch (IOException e) {
                        notifyFile("Packen nicht möglich", f.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        return list;
    }

    private void addFolder(List<Job> list, java.io.File dir, String rel, String group) {
        java.io.File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        if (fs.length == 0) {
            list.add(new Job(null, dir.getName(), rel, group, true));   // leerer Ordner
            return;
        }
        java.util.Arrays.sort(fs);
        for (java.io.File f : fs) {
            if (f.isDirectory()) {
                addFolder(list, f, rel + "/" + f.getName(), group);
            } else if (f.isFile()) {
                list.add(new Job(Uri.fromFile(f), f.getName(), rel, group));
            }
        }
    }

    /** Fragt den PC, ob er Ordner mit Unterordnern annimmt ("folders" in /info). */
    private boolean pcAcceptsFolders() {
        String host = Conn.host(this);
        String token = Conn.token(this);
        if (host == null || token == null) {
            return false;
        }
        HttpURLConnection c = null;
        try {
            c = Secure.connect(new URL("https://" + host + "/info?t=" + Uri.encode(token)), Conn.pin(this));
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            return c.getResponseCode() == 200 && readAll(c.getInputStream()).contains("\"folders\":true");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private void upload(List<Job> jobs) {
        String host = Conn.host(this);
        String token = Conn.token(this);
        if (host == null || token == null) {
            notifyFile("Senden nicht möglich", "Erst den QR-Code vom PC scannen");
            return;
        }
        // Sammelmeldung je Ordner: Anzahl, Größe, Fehler
        java.util.Map<String, long[]> groups = new java.util.LinkedHashMap<>();
        for (Job job : jobs) {
            Uri u = job.uri;
            String name = job.name != null ? job.name : displayName(u);
            long[] grp = job.group != null ? groups.get(job.group) : null;
            if (job.group != null && grp == null) {
                grp = new long[4];
                groups.put(job.group, grp);
            }
            if (grp != null && grp[2] > 0) {
                continue;   // nach einem Fehler den Rest dieses Ordners nicht weiter senden
            }
            if (job.emptyDir) {
                if (makeRemoteFolder(host, token, job.dir)) {
                    grp[3]++;
                } else {
                    grp[2]++;
                    notifyFile("Senden fehlgeschlagen", "Ordner " + job.dir + " konnte am PC nicht angelegt werden");
                }
                continue;
            }
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
                URL url = new URL("https://" + host + "/up?t=" + Uri.encode(token) + "&n=" + Uri.encode(name)
                        + (job.dir != null ? "&d=" + Uri.encode(job.dir) : ""));
                HttpURLConnection c = Secure.connect(url, Conn.pin(this));
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
                    if (grp != null) {
                        grp[0]++;
                        grp[1] += sentSize;
                    } else {
                        Store.addHistory(this, false, name, sentSize, true, "");
                        notifyFile("An den PC gesendet", name);
                    }
                } else if (code == 403) {
                    Store.addHistory(this, false, name, sentSize, false, "Schlüssel abgelaufen");
                    notifyFile("Senden fehlgeschlagen", name + ": Schlüssel abgelaufen, QR-Code neu scannen");
                    if (grp != null) {
                        grp[2]++;
                    }
                } else {
                    Store.addHistory(this, false, name, sentSize, false, "PC antwortet mit Fehler " + code);
                    notifyFile("Senden fehlgeschlagen", name + ": PC antwortet mit Fehler " + code);
                    if (grp != null) {
                        grp[2]++;
                    }
                }
            } catch (Exception e) {
                if (grp != null) {
                    grp[2]++;
                }
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
        for (java.util.Map.Entry<String, long[]> g : groups.entrySet()) {
            long[] v = g.getValue();
            if ((v[0] > 0 || v[3] > 0) && v[2] == 0) {
                String text = v[0] + (v[0] == 1 ? " Datei" : " Dateien")
                        + (v[3] > 0 ? " und " + v[3] + (v[3] == 1 ? " leerer Ordner" : " leere Ordner") : "");
                Store.addHistory(this, false, g.getKey() + "/ (" + text + ")", v[1], true, "");
                notifyFile("Ordner an den PC gesendet", g.getKey() + ": " + text);
            }
        }
    }

    /** Legt am PC einen (leeren) Ordner im Eingangsordner an ("Ordner/Unter"). */
    private boolean makeRemoteFolder(String host, String token, String rel) {
        HttpURLConnection c = null;
        try {
            c = Secure.connect(new URL("https://" + host + "/mkdir?t=" + Uri.encode(token) + "&d=" + Uri.encode(rel)), Conn.pin(this));
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            c.getOutputStream().close();
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
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
