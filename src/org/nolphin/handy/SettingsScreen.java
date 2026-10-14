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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        String sig = Conn.connected(c) + "|" + Conn.host(c) + "|" + Conn.targetTree(c) + "|" + Conn.catFolders(c, 0) + Conn.catFolders(c, 1)
                + Conn.catFolders(c, 2) + Conn.catFolders(c, 3) + Conn.catExclude(c, 0) + Conn.catExclude(c, 1) + Conn.catExclude(c, 2)
                + Conn.catExclude(c, 3);
        if (!sig.equals(signature)) {
            signature = sig;
            build();
        }
    }

    private static final String[] KIND_NAMES = { "Bilder", "Musik", "Videos", "Dokumente" };
    /** Reihenfolge der Zeilen wie im Entwurf: Bilder, Dokumente, Musik, Videos (Zahlen wie in ToPcScreen.CATS: 0 Bilder, 1 Audio, 2 Videos, 3 Dokumente). */
    private static final int[] KIND_ORDER = { 0, 3, 1, 2 };

    /** Eine einzeilige Zeile „Bilder … DCIM, Pictures ›“; Tippen öffnet die Ordnerauswahl. */
    private View kindRow(final int kind) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        row.setBackground(Ui.pressable(c));
        row.addView(Ui.label(c, KIND_NAMES[kind], 15, Ui.text(c), false));
        TextView value = Ui.label(c, CatFilter.summary(Conn.catFolders(c, kind), Conn.catExclude(c, kind)), 13, Ui.dim(c), false);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        vp.leftMargin = Ui.dp(c, 12);
        row.addView(value, vp);
        row.addView(Ui.icon(c, R.drawable.ic_chevron, Ui.dim(c), 18));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseFolders(kind);
            }
        });
        // dünne Trennlinie über jeder Zeile, wie im Entwurf
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(c);
        line.setBackgroundColor(Ui.line(c));
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        wrap.addView(row);
        return wrap;
    }

    /** Zählt je obersten Ordner die Dateien der Art und lässt die Ordner ankreuzen; nichts angekreuzt = alle Ordner. */
    private void chooseFolders(final int kind) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
                List<String> roots = StorageRoots.paths(c);
                try (android.database.Cursor cur = c.getContentResolver().query(android.provider.MediaStore.Files.getContentUri("external"),
                        new String[] { android.provider.MediaStore.Files.FileColumns.DATA }, ToPcScreen.categoryBase(kind), null, null)) {
                    while (cur != null && cur.moveToNext()) {
                        String top = CatFilter.top(roots, cur.getString(0));
                        if (top != null) {
                            counts.put(top, counts.containsKey(top) ? counts.get(top) + 1 : 1);
                        }
                    }
                } catch (Exception ignored) {
                }
                final Set<String> chosen = Conn.catFolders(c, kind);
                for (String s : chosen) {
                    if (!counts.containsKey(s)) {
                        counts.put(s, 0);
                    }
                }
                final List<String> names = new ArrayList<>(counts.keySet());
                java.util.Collections.sort(names, new java.util.Comparator<String>() {
                    @Override
                    public int compare(String a, String b) {
                        int d = counts.get(b) - counts.get(a);
                        return d != 0 ? d : a.compareToIgnoreCase(b);
                    }
                });
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        showFolderDialog(kind, names, counts, chosen);
                    }
                });
            }
        }, "nolphin-folders").start();
    }

    /**
     * Auswahl wie im Entwurf (grüne Kopfzeile, Kästchen mit Zahl rechts, grüner Balken „Übernehmen“) und dazu: ein Umschalter
     * „Nur aus | Nicht aus“ und ein Feld, in dem man einen Ordnernamen selbst einträgt (auch tiefer: „Pictures/Bearbeitet“).
     */
    private void showFolderDialog(final int kind, final List<String> counted, final java.util.Map<String, Integer> counts, Set<String> chosen) {
        final android.app.Dialog dialog = new android.app.Dialog(host.activity(), android.R.style.Theme_DeviceDefault_NoActionBar);
        final Set<String> inc = new HashSet<>(chosen);
        final Set<String> exc = Conn.catExclude(c, kind);
        final List<String> all = new ArrayList<>(counted);
        for (String s : inc) {
            if (!all.contains(s)) {
                all.add(s);
            }
        }
        for (String s : exc) {
            if (!all.contains(s)) {
                all.add(s);
            }
        }
        final int[] mode = { exc.isEmpty() || !inc.isEmpty() ? 0 : 1 };   // 0 = nur aus, 1 = nicht aus
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.bg(c));
        TextView head = Ui.label(c, KIND_NAMES[kind], 16, android.graphics.Color.WHITE, true);
        head.setBackgroundColor(Ui.bar(c));
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        box.addView(head);

        LinearLayout tabs = new LinearLayout(c);
        tabs.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 4));
        final TextView tOnly = Ui.label(c, "Nur aus", 14, Ui.text(c), true);
        final TextView tNot = Ui.label(c, "Nicht aus", 14, Ui.text(c), true);
        for (TextView t : new TextView[] { tOnly, tNot }) {
            t.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1);
            lp.leftMargin = Ui.dp(c, 4);
            lp.rightMargin = Ui.dp(c, 4);
            tabs.addView(t, lp);
        }
        box.addView(tabs);

        final TextView hint = Ui.label(c, "", 12, Ui.dim(c), false);
        hint.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 6));
        box.addView(hint);

        ScrollView sv = new ScrollView(c);
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout add = new LinearLayout(c);
        add.setGravity(android.view.Gravity.CENTER_VERTICAL);
        add.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        final EditText typed = new EditText(c);
        typed.setHint("Ordner eintragen, zum Beispiel GidHub");
        typed.setSingleLine(true);
        typed.setTextSize(14);
        typed.setTextColor(Ui.text(c));
        typed.setHintTextColor(Ui.dim(c));
        typed.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add.addView(typed, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView plus = Ui.label(c, "Hinzufügen", 14, Ui.primary(c), true);
        plus.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
        add.addView(plus);
        box.addView(add);

        final Runnable[] rebuild = new Runnable[1];
        final Runnable addTyped = new Runnable() {
            @Override
            public void run() {
                String name = CatFilter.clean(typed.getText().toString());
                if (name == null) {
                    Toast.makeText(c, "Bitte einen Ordnernamen eintragen", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!all.contains(name)) {
                    all.add(name);
                }
                (mode[0] == 0 ? inc : exc).add(name);
                typed.setText("");
                rebuild[0].run();
            }
        };
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addTyped.run();
            }
        });
        typed.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int action, android.view.KeyEvent e) {
                addTyped.run();
                return true;
            }
        });
        rebuild[0] = new Runnable() {
            @Override
            public void run() {
                tOnly.setBackground(Ui.outlined(c, mode[0] == 0 ? Ui.chip(c) : Ui.surface(c)));
                tNot.setBackground(Ui.outlined(c, mode[0] == 1 ? Ui.chip(c) : Ui.surface(c)));
                hint.setText(mode[0] == 0
                        ? KIND_NAMES[kind] + " nur aus den angekreuzten Ordnern zeigen. Nichts angekreuzt: alle Ordner."
                        : KIND_NAMES[kind] + " aus den angekreuzten Ordnern nicht zeigen. Auch Unterordner sind dabei.");
                list.removeAllViews();
                final Set<String> set = mode[0] == 0 ? inc : exc;
                for (final String n : all) {
                    LinearLayout row = new LinearLayout(c);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
                    final android.widget.CheckBox cb = new android.widget.CheckBox(c);
                    cb.setButtonTintList(android.content.res.ColorStateList.valueOf(mode[0] == 0 ? Ui.primary(c) : Ui.danger(c)));
                    cb.setChecked(set.contains(n));
                    cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton b, boolean checked) {
                            if (checked) {
                                set.add(n);
                            } else {
                                set.remove(n);
                            }
                        }
                    });
                    row.addView(cb);
                    row.addView(Ui.label(c, n, 15, Ui.text(c), false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    Integer count = counts.get(n);
                    row.addView(Ui.label(c, count == null ? "" : String.valueOf(count), 13, Ui.dim(c), false));
                    row.setBackground(Ui.pressable(c));
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            cb.setChecked(!cb.isChecked());
                        }
                    });
                    list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));
                    View line = new View(c);
                    line.setBackgroundColor(Ui.line(c));
                    list.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                }
            }
        };
        tOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mode[0] = 0;
                rebuild[0].run();
            }
        });
        tNot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mode[0] = 1;
                rebuild[0].run();
            }
        });
        rebuild[0].run();

        TextView ok = Ui.label(c, "Übernehmen", 16, android.graphics.Color.WHITE, true);
        ok.setGravity(android.view.Gravity.CENTER);
        ok.setBackgroundColor(Ui.primary(c));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Conn.setCatFolders(c, kind, inc);
                Conn.setCatExclude(c, kind, exc);
                signature = "";
                refresh();
                dialog.dismiss();
            }
        });
        box.addView(ok, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 56)));
        dialog.setContentView(box);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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

        // Anzeige nach Art
        LinearLayout kinds = Ui.card(c);
        kinds.addView(Ui.label(c, "Anzeige nach Art", 16, Ui.text(c), true));
        for (int i = 0; i < KIND_ORDER.length; i++) {
            kinds.addView(kindRow(KIND_ORDER[i]));
        }
        TextView kindHint = Ui.label(c, "Gilt für „Zum PC“. Die Ordner bleiben erreichbar.", 12, Ui.dim(c), false);
        kindHint.setPadding(0, Ui.dp(c, 8), 0, 0);
        kinds.addView(kindHint);
        col.addView(kinds);

        // Galerie des Handys
        LinearLayout gallery = Ui.card(c);
        gallery.addView(Ui.label(c, "Galerie des Handys", 16, Ui.text(c), true));
        Switch hide = new Switch(c);
        hide.setText("Vom PC geladene Ordner in der Galerie verstecken");
        hide.setTextSize(15);
        hide.setTextColor(Ui.text(c));
        hide.setChecked(Conn.hideGallery(c));
        hide.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
        Ui.tintSwitch(c, hide);
        hide.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Conn.setHideGallery(c, checked);
            }
        });
        gallery.addView(hide);
        TextView apply = Ui.label(c, "Auch bei schon geladenen Ordnern anwenden", 13, Ui.primary(c), false);
        apply.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final int n = SyncService.hideLoadedFolders(c);
                        root.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(c, n == 0 ? "Nichts zu tun" : n + (n == 1 ? " Ordner versteckt" : " Ordner versteckt"),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }, "nolphin-nomedia").start();
            }
        });
        gallery.addView(apply);
        col.addView(gallery);

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
