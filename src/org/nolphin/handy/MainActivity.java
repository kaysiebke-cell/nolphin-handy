package org.nolphin.handy;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Verbindungsstatus und die wenigen Bedienelemente der App. */
public class MainActivity extends Activity {
    private static final int PICK_FILES = 7;

    private TextView statusView;
    private TextView logView;
    private Button scanButton, sendButton, disconnectButton, cancelButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
        handleIntent(getIntent());
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
            startSync(null);   // nach einem Neustart des Handys wieder aufnehmen
        }
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void handleIntent(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "nolphin".equals(data.getScheme())) {
            connectWith(data.toString());
        }
    }

    private String error;

    private final Connector.Listener connectListener = new Connector.Listener() {
        @Override
        public void onSuccess(Link link) {
            error = null;
            Toast.makeText(MainActivity.this, "Mit dem PC verbunden", Toast.LENGTH_SHORT).show();
            startSync(null);
            update();
        }

        @Override
        public void onFailure(String message) {
            error = message;
            update();
        }
    };

    /** Link lesen, am PC prüfen und erst dann speichern. */
    private void connectWith(String text) {
        error = null;
        statusView.setText("Prüfe die Verbindung zum PC …");
        Connector.connect(this, text, connectListener);
    }

    private void startSync(String action) {
        Intent i = new Intent(this, SyncService.class);
        if (action != null) {
            i.setAction(action);
        }
        startForegroundService(i);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("Nolphin Handy");
        title.setTextSize(24);
        box.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(0, dp(12), 0, dp(12));
        box.addView(statusView);

        // Nicht verbunden: nur dieser Knopf
        scanButton = button("Mit dem PC verbinden (QR-Code scannen)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
            }
        });
        box.addView(scanButton);

        // Verbunden: Dateien senden, Verbindung trennen, laufende Übertragung abbrechen
        sendButton = button("Dateien an den PC senden …", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(pick, PICK_FILES);
            }
        });
        box.addView(sendButton);
        cancelButton = button("Übertragung abbrechen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSync(SyncService.ACTION_CANCEL);
            }
        });
        box.addView(cancelButton);
        disconnectButton = button("Verbindung trennen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Conn.clear(MainActivity.this);
                startSync(SyncService.ACTION_STOP);
                update();
            }
        });
        box.addView(disconnectButton);

        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setPadding(0, dp(16), 0, 0);
        box.addView(logView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setContentView(scroll);
    }

    /** Zeigt nur, was im jeweiligen Zustand Sinn ergibt. */
    private void update() {
        boolean connected = Conn.connected(this);
        if (connected) {
            String st = SyncService.status;
            // "Nicht verbunden" gilt kurz nach dem Verbinden, bevor der Dienst läuft
            if (st.startsWith("Nicht verbunden")) {
                st = "Verbinde mit dem PC …";
            }
            String line = st.contains(Conn.host(this)) ? st : st + "\nPC: " + Conn.host(this);
            if (!SyncService.transfer.isEmpty()) {
                line += "\n" + SyncService.transfer;
            }
            statusView.setText(line);
        } else {
            statusView.setText("Nicht mit einem PC verbunden.\n"
                    + "Öffne am PC in Nolphin die Seitenleiste „Handy“ und scanne den QR-Code.");
        }
        if (error != null) {
            statusView.setText(statusView.getText() + "\n\n" + error);
        }
        scanButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        sendButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        logView.setVisibility(connected ? View.VISIBLE : View.GONE);
        cancelButton.setVisibility(connected && !SyncService.transfer.isEmpty() ? View.VISIBLE : View.GONE);
        logView.setText(SyncService.eventsText());
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILES || result != RESULT_OK || data == null) {
            return;
        }
        ClipData clip = data.getClipData();
        ClipData out = null;
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (out == null) {
                    out = ClipData.newUri(getContentResolver(), "Dateien", u);
                } else {
                    out.addItem(new ClipData.Item(u));
                }
            }
        } else if (data.getData() != null) {
            out = ClipData.newUri(getContentResolver(), "Datei", data.getData());
        }
        if (out == null) {
            return;
        }
        Intent i = new Intent(this, SyncService.class);
        i.setAction(SyncService.ACTION_UPLOAD);
        i.setClipData(out);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startForegroundService(i);
        Toast.makeText(this, "Wird an den PC gesendet", Toast.LENGTH_SHORT).show();
    }
}
