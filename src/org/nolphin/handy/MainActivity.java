package org.nolphin.handy;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

    private static final int[] TAB_ICONS = { R.drawable.ic_home, R.drawable.ic_download, R.drawable.ic_upload,
            R.drawable.ic_history };
    private static final String[] TAB_NAMES = { "Start", "Vom PC", "Zum PC", "Verlauf" };

    private FrameLayout content;
    private final View[] navItems = new View[4];
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
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screens[TAB_HOME] = new HomeScreen(this, this);
        screens[TAB_FROM_PC] = new FromPcScreen(this, this);
        screens[TAB_TO_PC] = new ToPcScreen(this, this);
        screens[TAB_HISTORY] = new HistoryScreen(this, this);
        screens[TAB_SETTINGS] = new SettingsScreen(this, this);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
        show(TAB_HOME);
        handleIntent(getIntent());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));

        // Kopfleiste
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xFF0B2C47);
        top.setElevation(Ui.dp(this, 4));
        top.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        top.addView(Ui.icon(this, R.drawable.ic_phone, 0xFF7FE0D2, 26));
        TextView title = Ui.label(this, "Nolphin Handy", 19, Color.WHITE, true);
        title.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView settings = Ui.icon(this, R.drawable.ic_tune, Color.WHITE, 26);
        settings.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        settings.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        settings.setContentDescription("Einstellungen");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(TAB_SETTINGS);
            }
        });
        top.addView(settings);
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

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
        root.addView(nav);
        setContentView(root);
        int flags = 0;
        if (!Ui.dark(this)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ---- Host --------------------------------------------------------------------------------

    @Override
    public Activity activity() {
        return this;
    }

    @Override
    public void show(int tab) {
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

    @Override
    public void scan() {
        startActivity(new Intent(this, ScanActivity.class));
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
    public String error() {
        return error;
    }

    @Override
    public void fetch(RemoteFile file) {
        Intent i = new Intent(this, SyncService.class).setAction(SyncService.ACTION_FETCH)
                .putExtra("name", file.name).putExtra("size", file.size);
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
    public void pickFiles() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(pick, PICK_FILES);
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
            screens[current].onShow();
        }
    };

    private void handleIntent(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "nolphin".equals(data.getScheme())) {
            error = null;
            Connector.connect(this, data.toString(), connectListener);
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
        if (current != TAB_HOME) {
            show(TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
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
