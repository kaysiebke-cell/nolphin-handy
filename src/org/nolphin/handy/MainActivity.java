package org.nolphin.handy;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Hauptseite: Kopfleiste, Inhalt und die Reiter Start, Vom PC, Zum PC, Verlauf (Einstellungen oben rechts). */
public class MainActivity extends Activity implements Host {
    private static final int PICK_FILES = 7;
    private static final int REQ_MEDIA = 31;
    private static final int PICK_FOLDER = 8;
    private static final int SAVE_AS = 9;

    private Uri saveSource;

    private static final int[] TAB_ICONS = { R.drawable.ic_home, R.drawable.ic_download, R.drawable.ic_upload,
            R.drawable.ic_history };
    private static final String[] TAB_NAMES = { "Start", "Vom PC", "Zum PC", "Verlauf" };

    private FrameLayout content;
    private TextView subtitle;
    private final View[] navItems = new View[4];
    private View navBar;
    private final Screen[] screens = new Screen[5];
    private int current = -1;
    private String error;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (current >= 0) {
                screens[current].refresh();
            }
            updateSubtitle();
            handler.postDelayed(this, 1000);
        }
    };

    /**
     * Skaliert die App einheitlich auf die Bezugsbreite von 360 dp (siehe {@link UiScale}): Auf breiteren Handys wirken
     * sonst Schrift, Symbole und Abstände kleiner als im Entwurf.
     */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        int dpi = UiScale.densityDpi(c.densityDpi, c.smallestScreenWidthDp);
        if (dpi != c.densityDpi) {
            c.densityDpi = dpi;
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screens[TAB_HOME] = new HomeScreen(this, this);
        screens[TAB_FROM_PC] = new FromPcScreen(this, this);
        screens[TAB_TO_PC] = new ToPcTabs(this, this);
        screens[TAB_HISTORY] = new HistoryScreen(this, this);
        screens[TAB_SETTINGS] = new SettingsScreen(this, this);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
        show(TAB_HOME);
        updateSubtitle();
        handleIntent(getIntent());
    }

    private void updateSubtitle() {
        if (subtitle == null) {
            return;
        }
        if (!Conn.connected(this)) {
            subtitle.setText("Nicht verbunden");
        } else {
            String pc = Conn.pcName(this);
            subtitle.setText("Verbunden mit " + (pc.isEmpty() ? Conn.host(this) : pc));
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));

        // Kopfleiste: App-Symbol, Name und Verbindungsstatus
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable barBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { Ui.bar(this), Ui.barEnd(this) });
        // gerade Unterkante: klare Trennung zwischen Kopfleiste und Inhalt
        top.setBackground(barBg);
        top.setElevation(Ui.dp(this, 3));
        top.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_phone);   // rahmenloses Handy für die schmale Leiste; das Launcher-Symbol bleibt das grüne Quadrat
        top.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 36)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 10), 0, 0, 0);
        titles.addView(Ui.label(this, "Nolphin Handy", 16, Color.WHITE, true));
        subtitle = Ui.label(this, "", 11, 0xDDFFFFFF, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // zentrales Menü: Einträge des aktuellen Bildschirms (zum Beispiel Suchen, Neuer Ordner, Ansicht und Sortierung) und Einstellungen
        ImageView menu = Ui.icon(this, R.drawable.ic_menu, Color.WHITE, 24);
        menu.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        menu.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        menu.setBackground(Ui.pressable(this));
        menu.setContentDescription("Menü");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainMenu(v);
            }
        });
        top.addView(menu);
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Reiter unten
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Ui.surface(this));
        nav.setElevation(Ui.dp(this, 8));
        for (int i = 0; i < 4; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            item.addView(Ui.icon(this, TAB_ICONS[i], Ui.dim(this), 24));
            TextView t = Ui.label(this, TAB_NAMES[i], 11, Ui.dim(this), false);
            t.setGravity(Gravity.CENTER);
            item.addView(t);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    show(tab);
                }
            });
            navItems[i] = item;
            nav.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        navBar = nav;
        root.addView(nav);
        setContentView(root);
        int flags = 0;
        if (!Ui.dark(this)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void showMainMenu(View anchor) {
        final String[] own = screens[current].menuLabels();
        String[] all = new String[own.length + 1];
        System.arraycopy(own, 0, all, 0, own.length);
        all[own.length] = "Einstellungen";
        Ui.popupMenu(this, anchor, all, new Ui.MenuPick() {
            @Override
            public void on(int index) {
                if (index == own.length) {
                    show(TAB_SETTINGS);
                } else {
                    screens[current].menuPicked(index);
                }
            }
        });
    }

    // ---- Host --------------------------------------------------------------------------------

    @Override
    public Activity activity() {
        return this;
    }

    @Override
    public void setNavVisible(boolean visible) {
        if (navBar != null) {
            navBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void show(int tab) {
        setNavVisible(true);   // der gewählte Bildschirm blendet sie bei Bedarf wieder aus
        current = tab;
        content.removeAllViews();
        content.addView(screens[tab].view());
        screens[tab].onShow();
        for (int i = 0; i < 4; i++) {
            boolean on = i == tab;
            int color = on ? Ui.primary(this) : Ui.dim(this);
            LinearLayout item = (LinearLayout) navItems[i];
            ((ImageView) item.getChildAt(0)).setColorFilter(color);
            ((TextView) item.getChildAt(1)).setTextColor(color);
            ((TextView) item.getChildAt(1)).setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
        }
    }

    /**
     * Öffnet den Scanner der App (eigene Kamera und eigener QR-Leser): Er verbindet sofort, ohne Umweg. Der Scanner des
     * Herstellers (Xiaomi) wird nicht mehr benutzt: Er gibt das Ergebnis nicht an die App zurück, sondern zeigt Text
     * an oder öffnet den Link im Browser.
     */
    @Override
    public void scan() {
        startActivity(new Intent(this, ScanActivity.class));
    }

    @Override
    public void pasteLink() {
        android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
        CharSequence text = null;
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        }
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Die Zwischenablage ist leer. Kopiere am PC in Nolphin den Link (Knopf neben dem Link) und sende ihn dir aufs Handy.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        error = null;
        Toast.makeText(this, "Verbinde …", Toast.LENGTH_SHORT).show();
        Connector.connect(this, text.toString().trim(), connectListener);
    }

    @Override
    public void disconnect() {
        Conn.clear(this);
        startSync(SyncService.ACTION_STOP, null);
        error = null;
        screens[current].onShow();
    }

    @Override
    public void cancelTransfer() {
        startSync(SyncService.ACTION_CANCEL, null);
    }

    @Override
    public void fetchFolder(String dir, String name) {
        Intent i = new Intent(this, SyncService.class).setAction(SyncService.ACTION_FETCH_DIR)
                .putExtra("dir", dir).putExtra("name", name);
        startForegroundService(i);
    }

    @Override
    public String error() {
        return error;
    }

    @Override
    public void fetch(RemoteFile file) {
        Intent i = new Intent(this, SyncService.class).setAction(SyncService.ACTION_FETCH)
                .putExtra("name", file.name).putExtra("size", file.size).putExtra("dir", file.dir);
        startForegroundService(i);
    }

    @Override
    public void send(List<Uri> uris) {
        if (uris.isEmpty()) {
            return;
        }
        ClipData clip = ClipData.newUri(getContentResolver(), "Dateien", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        Intent svc = new Intent(this, SyncService.class).setAction(SyncService.ACTION_UPLOAD);
        svc.setClipData(clip);
        svc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startForegroundService(svc);
        Toast.makeText(this, uris.size() == 1 ? "Wird an den PC gesendet" : uris.size() + " Dateien werden gesendet",
                Toast.LENGTH_SHORT).show();
        show(TAB_HOME);
    }

    @Override
    public void sendPaths(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        Intent svc = new Intent(this, SyncService.class).setAction(SyncService.ACTION_UPLOAD);
        svc.putExtra("paths", paths.toArray(new String[0]));
        startForegroundService(svc);
        Toast.makeText(this, paths.size() == 1 ? "Wird an den PC gesendet" : paths.size() + " Dateien werden gesendet",
                Toast.LENGTH_SHORT).show();
        show(TAB_HOME);
    }

    @Override
    public boolean hasFilesAccess() {
        return Build.VERSION.SDK_INT >= 30 ? android.os.Environment.isExternalStorageManager() : hasMediaAccess();
    }

    @Override
    public void requestFilesAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            requestMediaAccess();
            return;
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    @Override
    public void pickFiles() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(pick, PICK_FILES);
    }

    @Override
    public void pickPhotos() {
        Intent pick;
        if (Build.VERSION.SDK_INT >= 33) {
            // Fotoauswahl des Systems: zeigt auch die Cloud-Bilder aus Google Fotos
            pick = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            pick.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, android.provider.MediaStore.getPickImagesMaxLimit());
        } else {
            pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "image/*", "video/*" });
            pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        startActivityForResult(pick, PICK_FILES);
    }

    @Override
    public void pickTargetFolder() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(pick, PICK_FOLDER);
    }

    @Override
    public void saveAs(String name, String mime, Uri source) {
        saveSource = source;
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(mime);
        create.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(create, SAVE_AS);
    }

    private void copyTo(Uri from, Uri to) {
        try (java.io.InputStream in = getContentResolver().openInputStream(from);
             java.io.OutputStream out = getContentResolver().openOutputStream(to)) {
            if (in == null || out == null) {
                throw new java.io.IOException("nicht lesbar");
            }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Speichern fehlgeschlagen: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private String mediaPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public boolean hasMediaAccess() {
        return checkSelfPermission(mediaPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void requestMediaAccess() {
        requestPermissions(new String[] { mediaPermission() }, REQ_MEDIA);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        if (request == REQ_MEDIA && current >= 0) {
            screens[current].onShow();
        }
    }

    // ---- Verbinden und Dienst ------------------------------------------------------------------

    private final Connector.Listener connectListener = new Connector.Listener() {
        @Override
        public void onSuccess(Link link) {
            error = null;
            Toast.makeText(MainActivity.this, "Mit dem PC verbunden", Toast.LENGTH_SHORT).show();
            startSync(null, null);
            screens[current].onShow();
        }

        @Override
        public void onFailure(String message) {
            error = message;
            // Auch sichtbar machen, wenn die App schon verbunden ist (zum Beispiel abgelehntes Zertifikat)
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            screens[current].onShow();
        }
    };

    /**
     * Ein Link von außen (Kamera-App, Browser, andere App) verbindet erst nach Bestätigung: Sonst könnte eine fremde
     * Seite die App mit einem untergeschobenen Rechner im Netz verbinden. Der eigene QR-Scanner der App fragt nicht,
     * dort hat der Nutzer den Code gerade selbst gewählt.
     */
    /** Aktion der Verknüpfung am App-Symbol: gleich den Scanner öffnen. */
    static final String ACTION_SCAN = "org.nolphin.handy.action.SCAN";

    private void handleIntent(Intent intent) {
        String act = intent != null ? intent.getAction() : null;
        if (NolphinWidget.ACTION_PICK.equals(act) || NolphinWidget.ACTION_FROM_PC.equals(act)) {
            intent.setAction(Intent.ACTION_MAIN);   // vom Widget: nicht bei jedem Zurückkehren erneut öffnen
            if (!Conn.connected(this)) {
                Toast.makeText(this, "Zuerst mit dem PC verbinden", Toast.LENGTH_LONG).show();
                show(TAB_HOME);
            } else if (NolphinWidget.ACTION_PICK.equals(act)) {
                // „Senden“ im Widget öffnet den Dateimanager „Zum PC“: Dort lassen sich Dateien und auch Ordner auswählen
                // (die Dateiauswahl von Android zeigt keine Ordner)
                show(TAB_TO_PC);
            } else {
                show(TAB_FROM_PC);
            }
            return;
        }
        if (intent != null && ACTION_SCAN.equals(intent.getAction())) {
            intent.setAction(Intent.ACTION_MAIN);   // nicht bei jedem Zurückkehren erneut scannen
            scan();
            return;
        }
        Uri data = intent != null ? intent.getData() : null;
        if (data == null || !"nolphin".equals(data.getScheme())) {
            return;
        }
        final String text = data.toString();
        final Link link;
        try {
            link = Link.parse(text, System.currentTimeMillis() / 1000);
        } catch (Link.Invalid e) {
            error = e.getMessage();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            if (current >= 0) {
                screens[current].onShow();
            }
            return;
        }
        confirmConnect(link, text);
    }

    private boolean confirmShowing;

    /** Fragt nach, bevor sich die App mit einem Link verbindet, der nicht aus dem eigenen QR-Scanner kommt. */
    private void confirmConnect(Link link, final String text) {
        if (confirmShowing) {
            return;
        }
        confirmShowing = true;
        new android.app.AlertDialog.Builder(this, R.style.NolphinDialog)
                .setTitle("Mit diesem PC verbinden?")
                .setMessage("PC-Adresse: " + link.authority() + "\n\nNur bestätigen, wenn du diesen Code gerade selbst "
                        + "an deinem PC in Nolphin angezeigt hast. Ein fremder Link kann dich mit einem fremden Rechner verbinden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Verbinden", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        error = null;
                        Connector.connect(MainActivity.this, text, connectListener);
                    }
                })
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(android.content.DialogInterface d) {
                        confirmShowing = false;
                    }
                }).show();
    }

    private static long lastClipStamp = -1;

    /**
     * Liegt gerade ein Nolphin-Link in der Zwischenablage (zum Beispiel nach „Text kopieren“ im Scanner des Handys),
     * bietet die App an, sich damit zu verbinden. Gelesen wird nur, wenn sich die Zwischenablage seit dem letzten Mal
     * geändert hat; so bleibt die Meldung „App hat Zwischenablage eingefügt“ des Systems selten.
     */
    private void offerClipboardLink() {
        try {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            if (cm == null || !cm.hasPrimaryClip()) {
                return;
            }
            android.content.ClipDescription desc = cm.getPrimaryClipDescription();
            if (desc == null || !desc.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                return;
            }
            long stamp = desc.getTimestamp();
            if (stamp == lastClipStamp) {
                return;
            }
            lastClipStamp = stamp;
            if (System.currentTimeMillis() - stamp > 2 * 60 * 1000) {
                return;   // alter Inhalt der Zwischenablage: nicht bei jedem Start erneut anbieten
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return;
            }
            String text = clip.getItemAt(0).coerceToText(this).toString().trim();
            if (!text.startsWith("nolphin://") && !text.startsWith("https://")) {
                return;
            }
            Link link = Link.parse(text, System.currentTimeMillis() / 1000);
            if (link.authority().equals(Conn.host(this)) && link.token.equals(Conn.token(this))
                    && link.fingerprint.equals(Conn.pin(this))) {
                return;   // schon so verbunden
            }
            confirmConnect(link, text);
        } catch (Link.Invalid | RuntimeException ignored) {
            // kein brauchbarer Link: nichts anbieten
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            offerClipboardLink();   // erst mit Fokus darf die App die Zwischenablage lesen
        }
    }

    private void startSync(String action, String unused) {
        Intent i = new Intent(this, SyncService.class);
        if (action != null) {
            i.setAction(action);
        }
        startForegroundService(i);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Conn.connected(this)) {
            startSync(null, null);   // nach einem Neustart des Handys wieder aufnehmen
            Updater.check(getApplicationContext());
        }
        if (current >= 0) {
            screens[current].onShow();
        }
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (current >= 0 && screens[current].onBack()) {
            return;
        }
        if (current != TAB_HOME) {
            show(TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FOLDER && result == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Conn.setTargetTree(this, tree.toString());
            Toast.makeText(this, "Zielordner gespeichert", Toast.LENGTH_SHORT).show();
            show(TAB_SETTINGS);
            return;
        }
        if (request == SAVE_AS && result == RESULT_OK && data != null && data.getData() != null && saveSource != null) {
            copyTo(saveSource, data.getData());
            return;
        }
        if (request != PICK_FILES || result != RESULT_OK || data == null) {
            return;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                uris.add(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        send(uris);
    }
}
