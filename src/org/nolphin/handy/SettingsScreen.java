package org.nolphin.handy;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Einstellungen: Verbindung, automatisches Laden, Zielordner, Verlauf. */
final class SettingsScreen implements Screen {
    private final Context c;
    private final Host host;
    private final ScrollView root;
    private final LinearLayout col;
    private String signature = "";

    SettingsScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        root = new ScrollView(c);
        root.setBackgroundColor(Ui.bg(c));
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(c, 16);
        col.setPadding(p, p, p, p);
        root.addView(col);
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        signature = "";
        refresh();
    }

    @Override
    public void refresh() {
        String sig = Conn.connected(c) + "|" + Conn.host(c);
        if (!sig.equals(signature)) {
            signature = sig;
            build();
        }
    }

    private void build() {
        col.removeAllViews();
        TextView head = Ui.label(c, "Einstellungen", 22, Ui.text(c), true);
        head.setPadding(0, 0, 0, Ui.dp(c, 12));
        col.addView(head);

        // Verbindung
        LinearLayout conn = Ui.card(c);
        conn.addView(Ui.label(c, "Verbindung", 16, Ui.text(c), true));
        boolean connected = Conn.connected(c);
        String name = Conn.pcName(c);
        TextView st = Ui.label(c, connected ? "Verbunden mit " + (name.isEmpty() ? Conn.host(c) : name + " (" + Conn.host(c) + ")")
                : "Nicht verbunden", 14, Ui.dim(c), false);
        st.setPadding(0, Ui.dp(c, 6), 0, 0);
        conn.addView(st);
        if (connected) {
            conn.addView(Ui.button(c, "Verbindung trennen", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.disconnect();
                }
            }));
        } else {
            conn.addView(Ui.button(c, "Mit dem PC verbinden", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.scan();
                }
            }));
        }
        col.addView(conn);

        // Dateien vom PC
        LinearLayout files = Ui.card(c);
        files.addView(Ui.label(c, "Dateien vom PC", 16, Ui.text(c), true));
        Switch auto = new Switch(c);
        auto.setText("Neue Dateien automatisch laden");
        auto.setTextSize(15);
        auto.setTextColor(Ui.text(c));
        auto.setChecked(Conn.autoLoad(c));
        auto.setPadding(0, Ui.dp(c, 10), 0, 0);
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Conn.setAutoLoad(c, checked);
            }
        });
        files.addView(auto);
        TextView hint = Ui.label(c, "Aus: Dateien erscheinen unter „Vom PC“ und werden erst geladen, wenn du sie antippst.", 12,
                Ui.dim(c), false);
        hint.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12));
        files.addView(hint);
        TextView fl = Ui.label(c, "Ordner in „Downloads“", 13, Ui.dim(c), false);
        files.addView(fl);
        final EditText folder = new EditText(c);
        folder.setText(Conn.folder(c));
        folder.setSingleLine(true);
        folder.setInputType(InputType.TYPE_CLASS_TEXT);
        folder.setImeOptions(EditorInfo.IME_ACTION_DONE);
        folder.setTextColor(Ui.text(c));
        files.addView(folder);
        Runnable save = new Runnable() {
            @Override
            public void run() {
                Conn.setFolder(c, folder.getText().toString());
                folder.setText(Conn.folder(c));
            }
        };
        final Runnable saver = save;
        folder.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    saver.run();
                }
            }
        });
        folder.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int action, android.view.KeyEvent e) {
                saver.run();
                folder.clearFocus();
                Toast.makeText(c, "Gespeichert", Toast.LENGTH_SHORT).show();
                return false;
            }
        });
        col.addView(files);

        // Verlauf
        LinearLayout hist = Ui.card(c);
        hist.addView(Ui.label(c, "Verlauf", 16, Ui.text(c), true));
        hist.addView(Ui.button(c, "Verlauf löschen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.clearHistory(c);
                Toast.makeText(c, "Verlauf gelöscht", Toast.LENGTH_SHORT).show();
            }
        }));
        col.addView(hist);

        // Über
        LinearLayout about = Ui.card(c);
        about.addView(Ui.label(c, "Über", 16, Ui.text(c), true));
        TextView t = Ui.label(c, "Nolphin Handy " + version() + "\nEigene App ohne Bibliotheken, Teil von Nolphin.\n"
                + "Dateien gehen im eigenen WLAN direkt zwischen Handy und PC, ohne Cloud.", 13, Ui.dim(c), false);
        t.setPadding(0, Ui.dp(c, 6), 0, 0);
        about.addView(t);
        col.addView(about);
    }

    private String version() {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
