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
        String sig = Conn.connected(c) + "|" + Conn.host(c) + "|" + Conn.targetTree(c);
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
        Ui.tintSwitch(c, auto);
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
        String tree = Conn.targetTree(c);
        TextView where = Ui.label(c, "Speicherort", 13, Ui.dim(c), false);
        files.addView(where);
        TextView whereNow = Ui.label(c, tree == null ? "Downloads (Ordner unten)" : readable(tree), 15, Ui.text(c), false);
        whereNow.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 6));
        files.addView(whereNow);
        files.addView(Ui.button(c, "Zielordner wählen (Handy oder SD-Karte)", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.pickTargetFolder();
            }
        }));
        if (tree != null) {
            files.addView(Ui.button(c, "Zurück zu „Downloads“", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Conn.setTargetTree(c, null);
                    signature = "";
                    refresh();
                }
            }));
        }
        TextView fl = Ui.label(c, "Ordner in „Downloads“", 13, Ui.dim(c), false);
        fl.setPadding(0, Ui.dp(c, 12), 0, 0);
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

        // Dateimanager
        LinearLayout manager = Ui.card(c);
        manager.addView(Ui.label(c, "Dateimanager", 16, Ui.text(c), true));
        Switch dual = new Switch(c);
        dual.setText("Zwei-Fenster-Modus anbieten");
        dual.setTextSize(15);
        dual.setTextColor(Ui.text(c));
        dual.setChecked(Conn.dualAvailable(c));
        dual.setPadding(0, Ui.dp(c, 10), 0, 0);
        Ui.tintSwitch(c, dual);
        dual.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Conn.setDualAvailable(c, checked);
            }
        });
        manager.addView(dual);
        TextView dualHint = Ui.label(c, "An: In „Zum PC“ erscheint oben ein Knopf für zwei Fenster, in denen du unabhängig durch "
                + "Ordner gehst und Dateien zwischen beiden kopierst oder verschiebst. Aus: es gibt nur ein Fenster.", 12,
                Ui.dim(c), false);
        dualHint.setPadding(0, Ui.dp(c, 4), 0, 0);
        manager.addView(dualHint);
        col.addView(manager);

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
        hist.addView(Ui.button(c, "Zuletzt geöffnete Ordner löschen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Conn.clearRecents(c);
                Toast.makeText(c, "Liste der zuletzt geöffneten Ordner gelöscht", Toast.LENGTH_SHORT).show();
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

    /** "primary:Dokumente/Nolphin" oder "1A2B-3C4D:Nolphin" aus der Baum-Adresse, als lesbarer Pfad. */
    private static String readable(String tree) {
        try {
            String id = android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(tree));
            int colon = id.indexOf(':');
            String volume = colon > 0 ? id.substring(0, colon) : id;
            String path = colon >= 0 ? id.substring(colon + 1) : "";
            return ("primary".equals(volume) ? "Handy" : "SD-Karte") + "/" + path;
        } catch (Exception e) {
            return tree;
        }
    }

    private String version() {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
