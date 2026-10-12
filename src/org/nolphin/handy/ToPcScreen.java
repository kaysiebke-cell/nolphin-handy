package org.nolphin.handy;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handy → PC als Dateimanager: Übersicht (Hauptspeicher, SD-Karte, Downloads, Bilder, Audio, Videos, Dokumente, Apps),
 * Ordneransicht mit Pfadleiste als Kacheln oder Liste, Suche (auch in Unterordnern), Mehrfachauswahl und
 * „An den PC senden“. Braucht den Zugriff auf alle Dateien.
 */
final class ToPcScreen implements Screen {
    private static final int HOME = 0, FOLDER = 1, CATEGORY = 2, APPS = 3;
    private static final int FOLDER_COLOR = 0xFFE0A93B;
    private static final String[] CATS = { "Bilder", "Audio", "Videos", "Dokumente" };
    private static final int[] CAT_ICONS = { R.drawable.ic_image, R.drawable.ic_audio, R.drawable.ic_video, R.drawable.ic_doc };
    private static final int[] CAT_COLORS = { 0xFF9B59B6, 0xFF1FA59C, 0xFFD64545, 0xFF3C7DB5 };

    /** Ein Eintrag in der Liste: Ordner oder Datei. */
    private static final class Entry {
        final File file;
        final boolean dir;
        final long size;
        final long modified;
        final int count;      // Elemente in einem Ordner
        String sub = "";      // bei Suchergebnissen: der Ordner, in dem es liegt
        String label;         // bei App-Ordnern: Name der App
        String pkg;           // bei App-Ordnern: Paketname (für das Symbol)

        Entry(File file, boolean dir, long size, long modified, int count) {
            this.file = file;
            this.dir = dir;
            this.size = size;
            this.modified = modified;
            this.count = count;
        }

        String path() {
            return file.getPath();
        }
    }

    /** Ein Speicher: Hauptspeicher oder SD-Karte. */
    private static final class Vol {
        final String name;
        final File root;
        final boolean card;

        Vol(String name, File root, boolean card) {
            this.name = name;
            this.root = root;
            this.card = card;
        }
    }

    private final Context c;
    private final Host host;
    private final LinearLayout root;
    private final TextView title;
    private final ImageView toggle;
    private final EditText search;
    private final LinearLayout crumbBar;
    private final LinearLayout crumbs;
    private final TextView usage;
    private final LinearLayout selectBar;
    private final TextView selCount;
    private final FrameLayout body;
    private final android.widget.ScrollView homeScroll;
    private final LinearLayout homeBox;
    private final ImageView back;
    private final GridView grid;
    private final TextView empty;
    private final LinearLayout permCard;
    private final LinearLayout footer;
    private final TextView footerText;

    private int mode = HOME;
    private File dir;                     // im Modus FOLDER
    private int cat;                      // im Modus CATEGORY
    private String query = "";
    private boolean useGrid;
    private boolean loading;
    private String error;
    private boolean access;
    private boolean accessKnown;
    private int gen;                      // verwirft Ergebnisse veralteter Ladevorgänge

    private final List<Vol> vols = new ArrayList<>();
    private final String[] catSub = { "", "", "", "" };
    private String downloadsSub = "";
    private String appsSub = "";
    private final List<Entry> all = new ArrayList<>();       // Inhalt des Ordners oder der Kategorie
    private final List<Entry> entries = new ArrayList<>();   // was angezeigt wird (gefiltert oder Suchergebnis)
    private final Map<String, Long> selected = new LinkedHashMap<>();   // Pfad → Größe

    private final Runnable startSearch = new Runnable() {
        @Override
        public void run() {
            applyQuery();
        }
    };

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int i) {
            return entries.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Entry e = entries.get(i);
            return useGrid ? entryTile(e) : entryRow(e);
        }
    };

    ToPcScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        useGrid = Conn.grid(c);
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int p = Ui.dp(c, 16);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(p, p, p, Ui.dp(c, 4));
        back = Ui.icon(c, R.drawable.ic_back, Ui.primary(c), 24);
        back.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        blp.rightMargin = Ui.dp(c, 6);
        back.setLayoutParams(blp);
        back.setContentDescription("Zurück");
        back.setVisibility(View.GONE);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        head.addView(back);
        title = Ui.label(c, "Zum PC", 22, Ui.text(c), true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        toggle = Ui.icon(c, useGrid ? R.drawable.ic_list : R.drawable.ic_grid, Ui.primary(c), 24);
        toggle.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        toggle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        toggle.setContentDescription("Ansicht wechseln");
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useGrid = !useGrid;
                Conn.setGrid(ToPcScreen.this.c, useGrid);
                applyMode();
            }
        });
        head.addView(toggle);
        root.addView(head);

        LinearLayout searchRow = new LinearLayout(c);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(Ui.round(c, Ui.surface(c), 24));
        searchRow.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
        searchRow.addView(Ui.icon(c, R.drawable.ic_search, Ui.dim(c), 20));
        search = new EditText(c);
        search.setHint("Auf dem Handy suchen");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(Ui.text(c));
        search.setHintTextColor(Ui.dim(c));
        search.setBackground(null);
        search.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int n) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim();
                if (q.equals(query)) {
                    return;
                }
                query = q;
                root.removeCallbacks(startSearch);
                root.postDelayed(startSearch, 300);
            }
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView clear = Ui.icon(c, R.drawable.ic_close, Ui.dim(c), 20);
        clear.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        clear.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
        clear.setContentDescription("Suche leeren");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search.setText("");
            }
        });
        searchRow.addView(clear);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 44));
        slp.setMargins(p, 0, p, Ui.dp(c, 6));
        root.addView(searchRow, slp);

        crumbBar = new LinearLayout(c);
        crumbBar.setOrientation(LinearLayout.HORIZONTAL);
        crumbBar.setGravity(Gravity.CENTER_VERTICAL);
        crumbBar.setPadding(p, 0, p, Ui.dp(c, 4));
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        crumbs = new LinearLayout(c);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(crumbs);
        crumbBar.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        usage = Ui.label(c, "", 12, Ui.primary(c), true);
        usage.setBackground(Ui.round(c, Ui.chip(c), 12));
        usage.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        crumbBar.addView(usage);
        crumbBar.setVisibility(View.GONE);
        root.addView(crumbBar);

        selectBar = new LinearLayout(c);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.setPadding(p, 0, p, Ui.dp(c, 4));
        selectBar.setVisibility(View.GONE);
        ImageView closeSel = Ui.icon(c, R.drawable.ic_close, Ui.primary(c), 24);
        closeSel.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        closeSel.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        closeSel.setContentDescription("Auswahl beenden");
        closeSel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected.clear();
                render();
            }
        });
        selectBar.addView(closeSel);
        selCount = Ui.label(c, "", 14, Ui.text(c), true);
        selCount.setPadding(Ui.dp(c, 6), 0, 0, 0);
        selectBar.addView(selCount, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        selectBar.addView(chip("Alle auswählen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        }));
        root.addView(selectBar);

        body = new FrameLayout(c);
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        homeScroll = new android.widget.ScrollView(c);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeBox = new LinearLayout(c);
        homeBox.setOrientation(LinearLayout.VERTICAL);
        homeBox.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 16));
        homeScroll.addView(homeBox);
        body.addView(homeScroll);

        grid = newGrid();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long id) {
                Entry e = entries.get(i);
                if (e.dir) {
                    openFolder(e.file);
                } else {
                    toggleSelect(e);
                }
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int i, long id) {
                Entry e = entries.get(i);
                if (!e.dir) {
                    toggleSelect(e);
                    return true;
                }
                return false;
            }
        });
        body.addView(grid);

        empty = Ui.label(c, "", 15, Ui.dim(c), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(p * 2, p * 3, p * 2, p);
        body.addView(empty, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        permCard = Ui.card(c);
        permCard.setGravity(Gravity.CENTER_HORIZONTAL);
        permCard.addView(Ui.label(c, "Zugriff auf alle Dateien", 18, Ui.text(c), true));
        TextView why = Ui.label(c, "Damit du Ordner auf dem Handy und der SD-Karte durchsuchen und Dateien an den PC "
                + "senden kannst, braucht die App den Zugriff auf alle Dateien. Es wird nichts gesendet, bis du es "
                + "ausdrücklich anstößt.", 14, Ui.dim(c), false);
        why.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        permCard.addView(why);
        permCard.addView(Ui.button(c, "Zugriff erlauben", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToPcScreen.this.host.requestFilesAccess();
            }
        }));
        permCard.addView(Ui.button(c, "Dateien über die Systemauswahl wählen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Conn.connected(ToPcScreen.this.c)) {
                    ToPcScreen.this.host.pickFiles();
                } else {
                    ToPcScreen.this.host.show(Host.TAB_HOME);
                }
            }
        }));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.setMargins(p, p, p, p);
        permCard.setLayoutParams(plp);
        permCard.setVisibility(View.GONE);
        body.addView(permCard);

        footer = new LinearLayout(c);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(Ui.surface(c));
        footer.setElevation(Ui.dp(c, 6));
        footer.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        footerText = Ui.label(c, "", 14, Ui.text(c), true);
        footer.addView(footerText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button send = Ui.button(c, "An den PC senden", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSelected();
            }
        });
        send.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 180), Ui.dp(c, 44)));
        footer.addView(send);
        footer.setVisibility(View.GONE);
        root.addView(footer);

        applyMode();
    }

    private GridView newGrid() {
        GridView g = new GridView(c);
        g.setSelector(android.R.color.transparent);
        g.setVerticalSpacing(Ui.dp(c, 6));
        g.setHorizontalSpacing(Ui.dp(c, 6));
        g.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 10));
        g.setClipToPadding(false);
        return g;
    }

    private void applyMode() {
        grid.setNumColumns(useGrid ? 3 : 1);
        toggle.setImageResource(useGrid ? R.drawable.ic_list : R.drawable.ic_grid);
        adapter.notifyDataSetChanged();
    }

    // ---- Übersicht ---------------------------------------------------------------------------

    private void buildHome() {
        homeBox.removeAllViews();
        homeBox.addView(heading("Speicher"));
        for (final Vol v : vols) {
            homeBox.addView(storageCard(v));
        }
        homeBox.addView(heading("Schnellzugriff"));
        homeBox.addView(pair(
                shortcut(R.drawable.ic_download, Ui.primary(c), "Downloads", downloadsSub, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openFolder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                    }
                }),
                shortcut(R.drawable.ic_apps, Ui.accent(c), "Apps", appsSub, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openApps();
                    }
                })));
        homeBox.addView(heading("Kategorien"));
        for (int k = 0; k < CATS.length; k += 2) {
            homeBox.addView(pair(category(k), category(k + 1)));
        }
    }

    private View category(final int k) {
        return shortcut(CAT_ICONS[k], CAT_COLORS[k], CATS[k], catSub[k], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCategory(k);
            }
        });
    }

    private TextView heading(String text) {
        TextView t = Ui.label(c, text, 13, Ui.dim(c), true);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), 0, Ui.dp(c, 6));
        return t;
    }

    private LinearLayout pair(View a, View b) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        l.rightMargin = Ui.dp(c, 4);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        r.leftMargin = Ui.dp(c, 4);
        row.addView(a, l);
        row.addView(b, r);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = Ui.dp(c, 8);
        row.setLayoutParams(rp);
        return row;
    }

    /** Kompakte Karte: Symbol links, Name und Angabe rechts. */
    private View shortcut(int icon, int color, String name, String sub, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(c, Ui.surface(c), 14));
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        card.addView(Ui.badge(c, icon, Ui.chip(c), color, 44));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 10), 0, 0, 0);
        TextView n = Ui.label(c, name, 15, Ui.text(c), true);
        n.setSingleLine(true);
        text.addView(n);
        TextView sTxt = Ui.label(c, sub.isEmpty() ? " " : sub, 12, Ui.dim(c), false);
        sTxt.setSingleLine(true);
        sTxt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(sTxt);
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.setOnClickListener(l);
        return card;
    }

    /** Breite Karte für einen Speicher mit Füllstand. */
    private View storageCard(final Vol v) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(c, Ui.surface(c), 14));
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = Ui.dp(c, 8);
        card.setLayoutParams(cp);
        long total = 0, used = 0;
        try {
            StatFs sf = new StatFs(v.root.getPath());
            total = sf.getTotalBytes();
            used = total - sf.getAvailableBytes();
        } catch (RuntimeException ignored) {
        }
        int pct = total > 0 ? (int) Math.round(used * 100.0 / total) : 0;
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.badge(c, v.card ? R.drawable.ic_sd : R.drawable.ic_storage, Ui.chip(c), Ui.primary(c), 44));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 10), 0, 0, 0);
        text.addView(Ui.label(c, v.name, 16, Ui.text(c), true));
        text.addView(Ui.label(c, total > 0 ? Ui.size(used) + " von " + Ui.size(total) + " belegt" : "", 12, Ui.dim(c), false));
        top.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(Ui.label(c, pct + " %", 14, Ui.primary(c), true));
        card.addView(top);
        LinearLayout track = new LinearLayout(c);
        track.setWeightSum(100);
        track.setBackground(Ui.round(c, Ui.chip(c), 4));
        View fill = new View(c);
        fill.setBackground(Ui.round(c, pct > 90 ? Ui.danger(c) : Ui.primary(c), 4));
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(pct, 2)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 8));
        tp.topMargin = Ui.dp(c, 10);
        card.addView(track, tp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                openFolder(v.root);
            }
        });
        return card;
    }

    // ---- Speicher ----------------------------------------------------------------------------

    private void findVolumes() {
        vols.clear();
        vols.add(new Vol("Hauptspeicher", Environment.getExternalStorageDirectory(), false));
        try {
            StorageManager sm = c.getSystemService(StorageManager.class);
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (v.isPrimary() || !Environment.MEDIA_MOUNTED.equals(v.getState())) {
                    continue;
                }
                File d = null;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    d = v.getDirectory();
                } else {
                    for (File f : c.getExternalFilesDirs(null)) {
                        if (f != null && f.getPath().contains("/Android/") && !f.getPath().startsWith(
                                Environment.getExternalStorageDirectory().getPath())) {
                            d = new File(f.getPath().substring(0, f.getPath().indexOf("/Android/")));
                        }
                    }
                }
                if (d != null) {
                    vols.add(new Vol(v.isRemovable() ? "SD-Karte" : v.getDescription(c), d, v.isRemovable()));
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static String volumeSub(Vol v) {
        try {
            StatFs s = new StatFs(v.root.getPath());
            return Ui.size(s.getTotalBytes() - s.getAvailableBytes()) + " / " + Ui.size(s.getTotalBytes());
        } catch (RuntimeException e) {
            return "";
        }
    }

    private Vol volumeOf(File f) {
        Vol best = null;
        for (Vol v : vols) {
            String r = v.root.getPath();
            if ((f.getPath().equals(r) || f.getPath().startsWith(r + "/"))
                    && (best == null || r.length() > best.root.getPath().length())) {
                best = v;
            }
        }
        return best;
    }

    /** Anzahl und Größe je Kategorie für die Übersicht, im Hintergrund. */
    private void loadHomeInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] sub = new String[CATS.length];
                for (int i = 0; i < CATS.length; i++) {
                    long n = 0, bytes = 0;
                    try (Cursor cur = c.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                            new String[] { MediaStore.Files.FileColumns.SIZE }, categoryWhere(i), null, null)) {
                        while (cur != null && cur.moveToNext()) {
                            n++;
                            bytes += cur.getLong(0);
                        }
                    } catch (Exception ignored) {
                    }
                    sub[i] = n == 0 ? "leer" : Ui.size(bytes) + " (" + n + ")";
                }
                File[] dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles();
                final String dls = dl == null ? "" : dl.length + " Elemente";
                int na = appFolders().size();
                final String apps = na + (na == 1 ? " App" : " Apps");
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        System.arraycopy(sub, 0, catSub, 0, sub.length);
                        downloadsSub = dls;
                        appsSub = apps;
                        buildHome();
                    }
                });
            }
        }, "nolphin-home").start();
    }

    private static String categoryWhere(int k) {
        String t = MediaStore.Files.FileColumns.MEDIA_TYPE;
        String m = MediaStore.Files.FileColumns.MIME_TYPE;
        switch (k) {
            case 0: return t + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
            case 1: return t + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
            case 2: return t + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            default: return t + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_NONE + " AND (" + m + " = 'application/pdf'"
                    + " OR " + m + " LIKE 'text/%' OR " + m + " LIKE 'application/vnd.%' OR " + m + " = 'application/msword'"
                    + " OR " + m + " = 'application/rtf' OR " + m + " = 'application/zip')";
        }
    }

    // ---- Navigation --------------------------------------------------------------------------

    private void resetQuery() {
        root.removeCallbacks(startSearch);
        query = "";
        if (search.length() > 0) {
            search.setText("");
        }
        query = "";
    }

    private void openApps() {
        resetQuery();
        mode = APPS;
        loadList();
    }

    /** Ordner der Apps unter Android/media (WhatsApp, Facebook, Google …); Android/data sperrt das System. */
    private List<File> appFolders() {
        List<File> out = new ArrayList<>();
        for (Vol v : vols) {
            File[] fs = new File(v.root, "Android/media").listFiles();
            if (fs != null) {
                for (File f : fs) {
                    String[] inside = f.isDirectory() ? f.list() : null;
                    if (inside != null && inside.length > 0) {   // leere Ordner sind für den Austausch uninteressant
                        out.add(f);
                    }
                }
            }
        }
        return out;
    }

    private String appLabel(String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String nameOf(Entry e) {
        return e.label != null ? e.label : e.file.getName();
    }

    private void openFolder(File f) {
        resetQuery();
        mode = FOLDER;
        dir = f;
        loadList();
    }

    private void openCategory(int k) {
        resetQuery();
        mode = CATEGORY;
        cat = k;
        loadList();
    }

    private void goHome() {
        resetQuery();
        mode = HOME;
        ++gen;
        loading = false;
        all.clear();
        entries.clear();
        findVolumes();
        buildHome();
        loadHomeInfo();
        render();
    }

    @Override
    public boolean onBack() {
        if (!selected.isEmpty()) {
            selected.clear();
            render();
            return true;
        }
        if (!query.isEmpty()) {
            search.setText("");
            applyQuery();
            return true;
        }
        if (mode == FOLDER) {
            Vol v = volumeOf(dir);
            File up = dir.getParentFile();
            if (up != null && up.getPath().endsWith("/Android/media") && !appFolders().isEmpty()) {
                openApps();
                return true;
            }
            if (dir.getParentFile() == null || (v != null && dir.equals(v.root)) || v == null) {
                goHome();
            } else {
                openFolder(dir.getParentFile());
            }
            return true;
        }
        if (mode == CATEGORY || mode == APPS) {
            goHome();
            return true;
        }
        return false;
    }

    // ---- Laden und Suchen --------------------------------------------------------------------

    private static final Comparator<Entry> BY_NAME = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            if (a.dir != b.dir) {
                return a.dir ? -1 : 1;
            }
            return a.file.getName().compareToIgnoreCase(b.file.getName());
        }
    };

    private static Entry entryOf(File f) {
        if (f.isDirectory()) {
            String[] l = f.list();
            return new Entry(f, true, 0, f.lastModified(), l == null ? 0 : l.length);
        }
        return new Entry(f, false, f.length(), f.lastModified(), 0);
    }

    private void loadList() {
        final int my = ++gen;
        loading = true;
        error = null;
        all.clear();
        entries.clear();
        render();
        final int m = mode;
        final File d = dir;
        final int k = cat;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Entry> found = new ArrayList<>();
                String err = null;
                if (m == APPS) {
                    for (File f : appFolders()) {
                        Entry e = entryOf(f);
                        e.pkg = f.getName();
                        e.label = appLabel(e.pkg);
                        Vol v = volumeOf(f);
                        if (v != null && v.card) {
                            e.label = (e.label != null ? e.label : e.pkg) + " (SD-Karte)";
                        }
                        found.add(e);
                    }
                    Collections.sort(found, new Comparator<Entry>() {
                        @Override
                        public int compare(Entry a, Entry b) {
                            return nameOf(a).compareToIgnoreCase(nameOf(b));
                        }
                    });
                } else if (m == FOLDER) {
                    File[] fs = d.listFiles();
                    if (fs == null) {
                        err = "Auf diesen Ordner darf die App nicht zugreifen";
                    } else {
                        for (File f : fs) {
                            if (!f.getName().startsWith(".")) {
                                found.add(entryOf(f));
                            }
                        }
                        Collections.sort(found, BY_NAME);
                    }
                } else {
                    try (Cursor cur = c.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                            new String[] { MediaStore.Files.FileColumns.DATA },
                            categoryWhere(k), null, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")) {
                        while (cur != null && cur.moveToNext() && found.size() < 1500) {
                            String path = cur.getString(0);
                            if (path != null) {
                                File f = new File(path);
                                if (f.isFile()) {
                                    Entry e = new Entry(f, false, f.length(), f.lastModified(), 0);
                                    e.sub = parentName(f);
                                    found.add(e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        err = "Dateien konnten nicht gelesen werden";
                    }
                }
                final String fe = err;
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        if (my != gen) {
                            return;
                        }
                        loading = false;
                        error = fe;
                        all.clear();
                        all.addAll(found);
                        applyQuery();
                    }
                });
            }
        }, "nolphin-list").start();
    }

    private static String parentName(File f) {
        File p = f.getParentFile();
        return p == null ? "" : p.getName();
    }

    /** Ohne Suchtext die Liste zeigen; sonst in der Kategorie filtern oder ab dem Ordner in der Tiefe suchen. */
    private void applyQuery() {
        final String q = query.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            if (mode == HOME) {
                ++gen;
                loading = false;
                entries.clear();
            } else {
                ++gen;
                loading = false;
                entries.clear();
                entries.addAll(all);
            }
            render();
            return;
        }
        if (mode == CATEGORY || mode == APPS) {
            entries.clear();
            for (Entry e : all) {
                if (nameOf(e).toLowerCase(Locale.ROOT).contains(q)) {
                    entries.add(e);
                }
            }
            render();
            return;
        }
        final int my = ++gen;
        loading = true;
        error = null;
        entries.clear();
        render();
        final List<File> roots = new ArrayList<>();
        if (mode == FOLDER) {
            roots.add(dir);
        } else {
            for (Vol v : vols) {
                roots.add(v.root);
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Entry> found = new ArrayList<>();
                ArrayDeque<File> queue = new ArrayDeque<>(roots);
                while (!queue.isEmpty() && found.size() < 300 && my == gen) {
                    File d = queue.poll();
                    File[] fs = d.listFiles();
                    if (fs == null) {
                        continue;
                    }
                    for (File f : fs) {
                        String n = f.getName();
                        if (n.startsWith(".")) {
                            continue;
                        }
                        boolean dirF = f.isDirectory();
                        if (dirF) {
                            String path = f.getPath();
                            if (path.endsWith("/Android/data") || path.endsWith("/Android/obb")) {
                                continue;
                            }
                            queue.add(f);
                        }
                        if (n.toLowerCase(Locale.ROOT).contains(q)) {
                            Entry e = entryOf(f);
                            e.sub = parentName(f);
                            found.add(e);
                        }
                    }
                }
                Collections.sort(found, BY_NAME);
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        if (my != gen) {
                            return;
                        }
                        loading = false;
                        entries.clear();
                        entries.addAll(found);
                        render();
                    }
                });
            }
        }, "nolphin-search").start();
    }

    // ---- Auswahl und Senden ------------------------------------------------------------------

    private void toggleSelect(Entry e) {
        if (selected.remove(e.path()) == null) {
            selected.put(e.path(), e.size);
        }
        render();
    }

    private void selectAll() {
        for (Entry e : entries) {
            if (!e.dir) {
                selected.put(e.path(), e.size);
            }
        }
        render();
    }

    private void sendSelected() {
        if (selected.isEmpty()) {
            return;
        }
        if (!Conn.connected(c)) {
            Toast.makeText(c, "Zuerst mit dem PC verbinden", Toast.LENGTH_LONG).show();
            host.show(Host.TAB_HOME);
            return;
        }
        List<String> paths = new ArrayList<>(selected.keySet());
        selected.clear();
        render();
        host.sendPaths(paths);
    }

    // ---- Screen ------------------------------------------------------------------------------

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        access = host.hasFilesAccess();
        accessKnown = true;
        if (access) {
            if (mode == HOME) {
                findVolumes();
                buildHome();
                loadHomeInfo();
            } else if (query.isEmpty()) {
                loadList();
            }
        }
        render();
    }

    @Override
    public void refresh() {
        boolean now = host.hasFilesAccess();
        if (!accessKnown || now != access) {
            onShow();
        }
    }

    private void render() {
        boolean home = mode == HOME;
        boolean searching = !query.isEmpty();
        boolean sel = !selected.isEmpty();
        if (!access) {
            title.setText("Zum PC");
            permCard.setVisibility(View.VISIBLE);
            homeScroll.setVisibility(View.GONE);
            grid.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            crumbBar.setVisibility(View.GONE);
            selectBar.setVisibility(View.GONE);
            footer.setVisibility(View.GONE);
            toggle.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            return;
        }
        permCard.setVisibility(View.GONE);
        title.setText(home ? "Zum PC" : mode == APPS ? "Apps" : mode == CATEGORY ? CATS[cat] : folderTitle());
        toggle.setVisibility(home && !searching ? View.GONE : View.VISIBLE);
        back.setVisibility(home ? View.GONE : View.VISIBLE);
        showCrumbs(sel);
        selectBar.setVisibility(sel ? View.VISIBLE : View.GONE);
        selCount.setText(selected.size() + " ausgewählt");

        boolean showHome = home && !searching;
        homeScroll.setVisibility(showHome ? View.VISIBLE : View.GONE);
        grid.setVisibility(!showHome && !entries.isEmpty() ? View.VISIBLE : View.GONE);
        String msg = null;
        if (!showHome && entries.isEmpty()) {
            if (loading) {
                msg = searching ? "Suche …" : "Lade …";
            } else if (error != null) {
                msg = error + ".";
            } else if (searching) {
                msg = "Nichts gefunden für „" + query + "“.";
            } else {
                msg = "Dieser Ordner ist leer.";
            }
        } else if (loading && searching) {
            msg = null;
        }
        empty.setText(msg == null ? "" : msg);
        empty.setVisibility(msg == null ? View.GONE : View.VISIBLE);

        footer.setVisibility(sel ? View.VISIBLE : View.GONE);
        long total = 0;
        for (long s : selected.values()) {
            total += s;
        }
        footerText.setText(selected.size() + (selected.size() == 1 ? " Datei" : " Dateien") + " · " + Ui.size(total));
        adapter.notifyDataSetChanged();
    }

    private String folderTitle() {
        Vol v = volumeOf(dir);
        if (v != null && dir.equals(v.root)) {
            return v.name;
        }
        return dir.getName();
    }

    private void showCrumbs(boolean selecting) {
        crumbs.removeAllViews();
        boolean show = mode != HOME && !selecting;
        crumbBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            return;
        }
        ImageView home = Ui.icon(c, R.drawable.ic_home, Ui.primary(c), 20);
        home.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        home.setContentDescription("Übersicht");
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goHome();
            }
        });
        crumbs.addView(home);
        usage.setVisibility(View.GONE);
        if (mode == CATEGORY || mode == APPS) {
            crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
            crumbs.addView(crumb(mode == APPS ? "Apps" : CATS[cat], null, true));
            return;
        }
        Vol v = volumeOf(dir);
        if (v == null) {
            crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
            crumbs.addView(crumb(dir.getName(), dir, true));
            return;
        }
        List<File> chain = new ArrayList<>();
        for (File f = dir; f != null && !f.equals(v.root); f = f.getParentFile()) {
            chain.add(0, f);
        }
        crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
        crumbs.addView(crumb(v.name, v.root, chain.isEmpty()));
        for (int i = 0; i < chain.size(); i++) {
            crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
            crumbs.addView(crumb(chain.get(i).getName(), chain.get(i), i == chain.size() - 1));
        }
        try {
            StatFs s = new StatFs(v.root.getPath());
            long used = s.getTotalBytes() - s.getAvailableBytes();
            usage.setText(Math.round(used * 100.0 / s.getTotalBytes()) + " % belegt");
            usage.setVisibility(View.VISIBLE);
        } catch (RuntimeException ignored) {
        }
    }

    private TextView crumb(String text, final File target, boolean last) {
        TextView t = Ui.label(c, text, 14, last ? Ui.primary(c) : Ui.dim(c), last);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        if (!last && target != null) {
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFolder(target);
                }
            });
        }
        return t;
    }

    private TextView chip(String text, View.OnClickListener l) {
        TextView t = Ui.label(c, text, 13, Ui.primary(c), true);
        t.setBackground(Ui.round(c, Ui.chip(c), 16));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        t.setOnClickListener(l);
        return t;
    }

    // ---- Kacheln und Zeilen ------------------------------------------------------------------

    private ImageView symbol(int res, int color) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        return v;
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isImage(String e) {
        return e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("gif") || e.equals("webp")
                || e.equals("heic") || e.equals("bmp");
    }

    private static boolean isVideo(String e) {
        return e.equals("mp4") || e.equals("mkv") || e.equals("webm") || e.equals("3gp") || e.equals("mov")
                || e.equals("avi");
    }

    private static boolean isAudio(String e) {
        return e.equals("mp3") || e.equals("m4a") || e.equals("wav") || e.equals("ogg") || e.equals("flac")
                || e.equals("aac") || e.equals("opus");
    }

    private static boolean isDoc(String e) {
        return e.equals("pdf") || e.equals("txt") || e.equals("doc") || e.equals("docx") || e.equals("odt")
                || e.equals("xls") || e.equals("xlsx") || e.equals("ppt") || e.equals("pptx") || e.equals("rtf")
                || e.equals("csv");
    }

    private int typeIcon(String e) {
        return isImage(e) ? R.drawable.ic_image : isVideo(e) ? R.drawable.ic_video : isAudio(e) ? R.drawable.ic_audio
                : isDoc(e) ? R.drawable.ic_doc : R.drawable.ic_file;
    }

    private int typeColor(String e) {
        return isImage(e) ? CAT_COLORS[0] : isVideo(e) ? CAT_COLORS[2] : isAudio(e) ? CAT_COLORS[1]
                : isDoc(e) ? CAT_COLORS[3] : Ui.primary(c);
    }

    /** Symbol des Eintrags; bei Fotos und Videos später durch das Vorschaubild ersetzt. */
    private ImageView iconOf(Entry e) {
        if (e.pkg != null) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            v.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
            try {
                v.setImageDrawable(c.getPackageManager().getApplicationIcon(e.pkg));
                return v;
            } catch (Exception ignored) {
            }
        }
        if (e.dir) {
            return symbol(R.drawable.ic_folder, FOLDER_COLOR);
        }
        String x = ext(e.file.getName());
        final ImageView v = symbol(typeIcon(x), typeColor(x));
        String kind = isImage(x) ? "image" : isVideo(x) ? "video" : isAudio(x) ? "audio" : x.equals("pdf") ? "pdf"
                : x.equals("apk") ? "apk" : null;
        if (kind == null || kind.equals("pdf") || kind.equals("audio") || kind.equals("apk")) {
            // Symbol der Standard-App, die Android für diesen Dateityp eingestellt hat
            Drawable sys = systemIcon(x);
            if (sys != null) {
                v.clearColorFilter();
                v.setImageDrawable(sys);
                v.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
            }
        }
        if (kind != null) {
            v.setBackground(Ui.round(c, Ui.chip(c), 8));
            v.setClipToOutline(true);
            v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            Thumbs.loadFile(c, e.file, kind, 168, v, new Runnable() {
                @Override
                public void run() {
                }
            });
        }
        return v;
    }

    private final Map<String, Drawable> systemIcons = new java.util.HashMap<>();

    /** Symbol der App, die Android für diese Endung als Standard zum Öffnen kennt; null, wenn keine eingestellt ist. */
    private Drawable systemIcon(String x) {
        if (x.isEmpty()) {
            return null;
        }
        if (!systemIcons.containsKey(x)) {
            Drawable d = null;
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(x);
            if (mime != null) {
                try {
                    android.content.pm.PackageManager pm = c.getPackageManager();
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                            .setType(mime);
                    android.content.pm.ResolveInfo ri = pm.resolveActivity(i,
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                    if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                        d = ri.loadIcon(pm);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            systemIcons.put(x, d);
        }
        Drawable d = systemIcons.get(x);
        return d == null ? null : d.getConstantState() != null ? d.getConstantState().newDrawable() : d;
    }

    private View tile(View top, String name, String sub, int subColor, boolean picked) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Ui.round(c, picked ? Ui.chip(c) : Ui.surface(c), 12));
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        t.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.dp(c, 132)));
        t.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 56)));
        TextView n = Ui.label(c, name, 12, Ui.text(c), false);
        n.setMaxLines(2);
        n.setGravity(Gravity.CENTER);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        n.setPadding(Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2), 0);
        t.addView(n);
        TextView s = Ui.label(c, sub, 10, subColor, false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(s);
        return picked ? withCheck(t) : t;
    }

    /** Legt ein Häkchen oben rechts über die Kachel. */
    private View withCheck(View t) {
        FrameLayout fl = new FrameLayout(c);
        fl.setLayoutParams(t.getLayoutParams());
        fl.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout b = Ui.badge(c, R.drawable.ic_check, Ui.primary(c), 0xFFFFFFFF, 24);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.dp(c, 4), Ui.dp(c, 4), 0);
        fl.addView(b, lp);
        return fl;
    }

    private String subOf(Entry e) {
        String s = e.dir ? e.count + (e.count == 1 ? " Element" : " Elemente") : Ui.size(e.size);
        return e.sub.isEmpty() ? s : e.sub + " · " + s;
    }

    private View entryTile(Entry e) {
        boolean picked = selected.containsKey(e.path());
        return tile(iconOf(e), nameOf(e), subOf(e), picked ? Ui.primary(c) : Ui.dim(c), picked);
    }

    private View entryRow(Entry e) {
        boolean picked = selected.containsKey(e.path());
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(c, picked ? Ui.chip(c) : Ui.surface(c), 14));
        int p = Ui.dp(c, 12);
        card.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        card.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.dp(c, 68)));
        ImageView ic = picked ? symbol(R.drawable.ic_check, Ui.accent(c)) : iconOf(e);
        card.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        TextView n = Ui.label(c, nameOf(e), 15, Ui.text(c), false);
        n.setMaxLines(1);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        TextView s = Ui.label(c, subOf(e), 12, Ui.dim(c), false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(s);
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(Ui.label(c, new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(new Date(e.modified)), 12,
                Ui.dim(c), false));
        return card;
    }
}
