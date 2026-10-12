package org.nolphin.handy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;

/**
 * PC → Handy: der Ordner „Handy“ am PC und seine Unterordner wie in einem Dateimanager, als Kacheln oder Liste.
 * Ordner öffnet ein Tipp, Dateien lädt und öffnet er. Suchfeld filtert den Ordner; langer Druck startet die
 * Mehrfachauswahl (Laden, Teilen, Speichern unter).
 */
final class FromPcScreen implements Screen {
    private static final long RELOAD_MS = 3000;

    private final Context c;
    private final Host host;
    private final LinearLayout root;
    private final LinearLayout crumbs;
    private final GridView grid;
    private final TextView empty;
    private final ImageView toggle;
    private final LinearLayout normalBar;
    private final LinearLayout selectBar;
    private final TextView selCount;
    private final TextView selSave;
    private final EditText search;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Aktueller Unterordner relativ zu „Handy“, mit "/" getrennt; leer = oberste Ebene. */
    private String dir = "";
    private final List<String> dirs = new ArrayList<>();
    private final List<RemoteFile> files = new ArrayList<>();
    private String signature = "";
    private String loadedDir = null;
    private long lastLoad;
    private boolean loading;
    private String notice;
    private boolean useGrid;
    private String query = "";
    private final List<String> shownDirs = new ArrayList<>();
    private final List<RemoteFile> shownFiles = new ArrayList<>();
    /** Schlüssel (RemoteFile.key()) der ausgewählten Dateien; leer = keine Auswahl. */
    private final Set<String> selected = new LinkedHashSet<>();

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return shownDirs.size() + shownFiles.size();
        }

        @Override
        public Object getItem(int i) {
            return i < shownDirs.size() ? shownDirs.get(i) : shownFiles.get(i - shownDirs.size());
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            if (i < shownDirs.size()) {
                return useGrid ? folderTile(shownDirs.get(i)) : folderRow(shownDirs.get(i));
            }
            RemoteFile f = shownFiles.get(i - shownDirs.size());
            return useGrid ? fileTile(f) : fileRow(f);
        }
    };

    FromPcScreen(Context c, Host host) {
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
        head.addView(Ui.label(c, "Vom PC", 22, Ui.text(c), true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        toggle = Ui.icon(c, useGrid ? R.drawable.ic_list : R.drawable.ic_grid, Ui.primary(c), 24);
        toggle.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        toggle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        toggle.setContentDescription("Ansicht wechseln");
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useGrid = !useGrid;
                Conn.setGrid(FromPcScreen.this.c, useGrid);
                applyMode();
            }
        });
        head.addView(toggle);
        root.addView(head);

        search = new EditText(c);
        search.setHint("Suchen in diesem Ordner");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(Ui.text(c));
        search.setHintTextColor(Ui.dim(c));
        search.setBackground(Ui.round(c, Ui.surface(c), 24));
        search.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int n) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim();
                signature = "";
                refresh();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 44));
        slp.setMargins(p, 0, p, Ui.dp(c, 6));
        root.addView(search, slp);

        normalBar = new LinearLayout(c);
        normalBar.setOrientation(LinearLayout.HORIZONTAL);
        normalBar.setGravity(Gravity.CENTER_VERTICAL);
        normalBar.setPadding(p, 0, p, Ui.dp(c, 6));
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        crumbs = new LinearLayout(c);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(crumbs);
        normalBar.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        normalBar.addView(chip("Auswählen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        }));
        normalBar.addView(chip("Alle laden", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadAll();
            }
        }));
        root.addView(normalBar);

        selectBar = new LinearLayout(c);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.setPadding(p, 0, p, Ui.dp(c, 6));
        selectBar.setVisibility(View.GONE);
        ImageView closeSel = Ui.icon(c, R.drawable.ic_close, Ui.primary(c), 24);
        closeSel.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        closeSel.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        closeSel.setContentDescription("Auswahl beenden");
        closeSel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSelection();
            }
        });
        selectBar.addView(closeSel);
        selCount = Ui.label(c, "", 14, Ui.text(c), true);
        selCount.setPadding(Ui.dp(c, 6), 0, 0, 0);
        selectBar.addView(selCount, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        selectBar.addView(chip("Alle", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        }));
        selectBar.addView(chip("Laden", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadSelected();
            }
        }));
        selectBar.addView(chip("Teilen", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSelected();
            }
        }));
        selSave = chip("Speichern", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSelected();
            }
        });
        selectBar.addView(selSave);
        root.addView(selectBar);

        grid = new GridView(c);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalSpacing(Ui.dp(c, 6));
        grid.setHorizontalSpacing(Ui.dp(c, 6));
        grid.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), Ui.dp(c, 10));
        grid.setClipToPadding(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object o = adapter.getItem(position);
                if (o instanceof String) {
                    if (selected.isEmpty()) {
                        go(dir.isEmpty() ? (String) o : dir + "/" + o);
                    }
                } else if (!selected.isEmpty()) {
                    toggle((RemoteFile) o);
                } else {
                    open((RemoteFile) o);
                }
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Object o = adapter.getItem(position);
                if (o instanceof RemoteFile) {
                    toggle((RemoteFile) o);
                    return true;
                }
                return false;
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        empty = Ui.label(c, "", 15, Ui.dim(c), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(p * 2, p * 3, p * 2, p);
        root.addView(empty);
        applyMode();
        buildCrumbs();
    }

    private void applyMode() {
        int cols = useGrid ? 3 : 1;
        grid.setNumColumns(cols);
        toggle.setImageResource(useGrid ? R.drawable.ic_list : R.drawable.ic_grid);
        adapter.notifyDataSetChanged();
    }

    // ---- Navigation ------------------------------------------------------------------------

    private void go(String to) {
        selected.clear();
        if (!query.isEmpty()) {
            search.setText("");   // setzt query über den TextWatcher zurück
        }
        dir = to;
        dirs.clear();
        files.clear();
        loadedDir = null;
        notice = null;
        signature = "";
        buildCrumbs();
        adapter.notifyDataSetChanged();
        load();
    }

    @Override
    public boolean onBack() {
        if (!selected.isEmpty()) {
            clearSelection();
            return true;
        }
        if (dir.isEmpty()) {
            return false;
        }
        int slash = dir.lastIndexOf('/');
        go(slash < 0 ? "" : dir.substring(0, slash));
        return true;
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        addCrumb("Handy", "", dir.isEmpty());
        if (!dir.isEmpty()) {
            String[] parts = dir.split("/");
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    path.append('/');
                }
                path.append(parts[i]);
                crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
                addCrumb(parts[i], path.toString(), i == parts.length - 1);
            }
        }
    }

    private void addCrumb(String text, final String target, boolean last) {
        TextView t = Ui.label(c, text, 14, last ? Ui.primary(c) : Ui.dim(c), last);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        if (!last) {
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(target);
                }
            });
        }
        crumbs.addView(t);
    }

    // ---- Laden der Ordnerliste ---------------------------------------------------------------

    private void load() {
        final String host = Conn.host(c);
        final String token = Conn.token(c);
        if (loading || host == null || token == null) {
            return;
        }
        loading = true;
        lastLoad = System.currentTimeMillis();
        final String want = dir;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> newDirs = new ArrayList<>();
                final List<RemoteFile> newFiles = new ArrayList<>();
                String error = null;
                try {
                    URL url = new URL("http://" + host + "/dir?t=" + Uri.encode(token)
                            + (want.isEmpty() ? "" : "&p=" + Uri.encode(want)));
                    HttpURLConnection h = (HttpURLConnection) url.openConnection();
                    h.setConnectTimeout(5000);
                    h.setReadTimeout(10000);
                    try {
                        int code = h.getResponseCode();
                        if (code == 404) {
                            error = want.isEmpty() ? "Dieser PC kennt die Ordneransicht noch nicht. Nolphin am PC aktualisieren"
                                    : "Den Ordner gibt es am PC nicht mehr";
                        } else if (code == 403) {
                            error = "Schlüssel abgelaufen. Am PC einen neuen QR-Code erzeugen";
                        } else if (code != 200) {
                            error = "PC antwortet mit Fehler " + code;
                        } else {
                            ByteArrayOutputStream buf = new ByteArrayOutputStream();
                            try (InputStream in = h.getInputStream()) {
                                byte[] b = new byte[8192];
                                int n;
                                while ((n = in.read(b)) > 0) {
                                    buf.write(b, 0, n);
                                }
                            }
                            JSONObject o = new JSONObject(buf.toString("UTF-8"));
                            JSONArray d = o.getJSONArray("dirs");
                            for (int i = 0; i < d.length(); i++) {
                                newDirs.add(d.getJSONObject(i).getString("n"));
                            }
                            JSONArray f = o.getJSONArray("files");
                            for (int i = 0; i < f.length(); i++) {
                                JSONObject e = f.getJSONObject(i);
                                newFiles.add(new RemoteFile(want, e.getString("n"), e.getLong("s")));
                            }
                        }
                    } finally {
                        h.disconnect();
                    }
                } catch (java.io.FileNotFoundException e) {
                    error = "Dieser PC kennt die Ordneransicht noch nicht. Nolphin am PC aktualisieren";
                } catch (Exception e) {
                    error = "PC nicht erreichbar";
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (!want.equals(dir)) {
                            return;   // inzwischen woanders
                        }
                        notice = err;
                        if (err == null) {
                            dirs.clear();
                            files.clear();
                            dirs.addAll(newDirs);
                            files.addAll(newFiles);
                            loadedDir = want;
                        }
                        signature = "";
                        refresh();
                    }
                });
            }
        }, "nolphin-dir").start();
    }

    private void loadAll() {
        int n = 0;
        for (RemoteFile f : files) {
            if (Store.uri(c, f.key()) == null) {
                host.fetch(f);
                n++;
            }
        }
        Toast.makeText(c, n == 0 ? "In diesem Ordner ist schon alles auf dem Handy" : n + " Dateien werden geladen …",
                Toast.LENGTH_SHORT).show();
    }

    // ---- Screen ----------------------------------------------------------------------------

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        signature = "";
        lastLoad = 0;
        refresh();
    }

    @Override
    public void refresh() {
        boolean connected = Conn.connected(c);
        if (connected && System.currentTimeMillis() - lastLoad > RELOAD_MS) {
            load();
        }
        StringBuilder sb = new StringBuilder();
        for (RemoteFile f : files) {
            sb.append(f.key()).append(Store.uri(c, f.key()) != null ? '+' : '-').append(';');
        }
        sb.append(dirs).append(connected).append(notice).append(useGrid).append(query).append(selected);
        if (!sb.toString().equals(signature)) {
            signature = sb.toString();
            applyFilter();
            adapter.notifyDataSetChanged();
            updateBars();
        }
        boolean none = shownDirs.isEmpty() && shownFiles.isEmpty();
        if (!connected) {
            empty.setText("Nicht mit dem PC verbunden.\nVerbinde dich zuerst auf der Startseite.");
        } else if (notice != null) {
            empty.setText(notice + ".");
        } else if (loadedDir == null) {
            empty.setText("Lade …");
        } else if (!query.isEmpty() && none) {
            empty.setText("Nichts gefunden für „" + query + "“.");
        } else {
            empty.setText(dir.isEmpty()
                    ? "Noch nichts vom PC.\nLege am PC Dateien in den Ordner „Handy“ oder wähle in Nolphin „An Handy senden“."
                    : "Dieser Ordner ist leer.");
        }
        empty.setVisibility(none || !connected ? View.VISIBLE : View.GONE);
        grid.setVisibility(none || !connected ? View.GONE : View.VISIBLE);
    }

    // ---- Suche und Auswahl -------------------------------------------------------------------

    private void applyFilter() {
        shownDirs.clear();
        shownFiles.clear();
        String q = query.toLowerCase(Locale.ROOT);
        for (String d : dirs) {
            if (q.isEmpty() || d.toLowerCase(Locale.ROOT).contains(q)) {
                shownDirs.add(d);
            }
        }
        for (RemoteFile f : files) {
            if (q.isEmpty() || f.name.toLowerCase(Locale.ROOT).contains(q)) {
                shownFiles.add(f);
            }
        }
        // Ausgewählte, die es nicht mehr gibt (z. B. am PC gelöscht), fallen heraus.
        Set<String> keep = new LinkedHashSet<>();
        for (RemoteFile f : files) {
            if (selected.contains(f.key())) {
                keep.add(f.key());
            }
        }
        selected.retainAll(keep);
    }

    private TextView chip(String text, boolean filled, View.OnClickListener l) {
        TextView t = Ui.label(c, text, 13, filled ? 0xFFFFFFFF : Ui.primary(c), true);
        t.setBackground(filled ? Ui.round(c, Ui.primary(c), 16) : Ui.round(c, Ui.chip(c), 16));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(c, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    private void updateBars() {
        boolean sel = !selected.isEmpty();
        normalBar.setVisibility(sel ? View.GONE : View.VISIBLE);
        selectBar.setVisibility(sel ? View.VISIBLE : View.GONE);
        selCount.setText(selected.size() + " ausgewählt");
        selSave.setVisibility(selected.size() == 1 ? View.VISIBLE : View.GONE);
    }

    private void toggle(RemoteFile f) {
        if (!selected.remove(f.key())) {
            selected.add(f.key());
        }
        signature = "";
        refresh();
    }

    private void clearSelection() {
        selected.clear();
        signature = "";
        refresh();
    }

    /** Alle sichtbaren Dateien (nach Suche) auswählen. */
    private void selectAll() {
        for (RemoteFile f : shownFiles) {
            selected.add(f.key());
        }
        signature = "";
        refresh();
    }

    private List<RemoteFile> selectedFiles() {
        List<RemoteFile> out = new ArrayList<>();
        for (RemoteFile f : files) {
            if (selected.contains(f.key())) {
                out.add(f);
            }
        }
        return out;
    }

    private void loadSelected() {
        int n = 0;
        for (RemoteFile f : selectedFiles()) {
            if (Store.uri(c, f.key()) == null) {
                host.fetch(f);
                n++;
            }
        }
        Toast.makeText(c, n == 0 ? "Alles schon auf dem Handy" : n + " Dateien werden geladen …",
                Toast.LENGTH_SHORT).show();
        clearSelection();
    }

    private void shareSelected() {
        ArrayList<Uri> uris = new ArrayList<>();
        String type = null;
        int missing = 0;
        for (RemoteFile f : selectedFiles()) {
            String u = Store.uri(c, f.key());
            if (u == null) {
                host.fetch(f);
                missing++;
                continue;
            }
            uris.add(Uri.parse(u));
            String m = mime(f.name);
            if (type == null) {
                type = m;
            } else if (!type.equals(m)) {
                type = m.substring(0, m.indexOf('/') + 1).equals(type.substring(0, type.indexOf('/') + 1))
                        ? type.substring(0, type.indexOf('/')) + "/*" : "*/*";
            }
        }
        if (missing > 0) {
            Toast.makeText(c, missing + " noch nicht geladene Dateien werden geladen. Danach erneut Teilen.",
                    Toast.LENGTH_LONG).show();
        }
        if (uris.isEmpty()) {
            return;
        }
        Intent i = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        i.setType(type);
        if (uris.size() == 1) {
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.activity().startActivity(Intent.createChooser(i, "Teilen"));
        } catch (RuntimeException e) {
            Toast.makeText(c, "Teilen nicht möglich", Toast.LENGTH_LONG).show();
        }
    }

    private void saveSelected() {
        List<RemoteFile> sel = selectedFiles();
        if (sel.size() != 1) {
            return;
        }
        RemoteFile f = sel.get(0);
        String uri = Store.uri(c, f.key());
        if (uri == null) {
            host.fetch(f);
            Toast.makeText(c, "Wird geladen …", Toast.LENGTH_SHORT).show();
            return;
        }
        host.saveAs(f.name, mime(f.name), Uri.parse(uri));
    }

    // ---- Kacheln und Zeilen ----------------------------------------------------------------

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
        t.addView(Ui.label(c, sub, 10, subColor, false));
        return picked ? withCheck(t) : t;
    }

    private ImageView symbol(int res, int color) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        return v;
    }

    private View folderTile(String name) {
        return tile(symbol(R.drawable.ic_folder, 0xFFE0A93B), name, "Ordner", Ui.dim(c), false);
    }

    private View fileTile(RemoteFile f) {
        final String uri = Store.uri(c, f.key());
        final ImageView top = symbol(R.drawable.ic_file, Ui.primary(c));
        if (uri != null) {
            top.setScaleType(ImageView.ScaleType.CENTER_CROP);
            top.setPadding(0, 0, 0, 0);
            top.setColorFilter(null);
            top.setBackground(Ui.round(c, Ui.chip(c), 8));
            top.setClipToOutline(true);
            top.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            final Runnable icon = new Runnable() {
                @Override
                public void run() {
                    top.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    top.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
                    top.setImageResource(R.drawable.ic_file);
                    top.setColorFilter(Ui.primary(c));
                }
            };
            icon.run();
            top.setPadding(0, 0, 0, 0);
            Thumbs.load(c, Uri.parse(uri), 168, top, icon);
        }
        return tile(top, f.name, Ui.size(f.size) + (uri != null ? " · Handy" : ""),
                uri != null ? Ui.accent(c) : Ui.dim(c), selected.contains(f.key()));
    }

    /** Legt ein Häkchen oben rechts über die Kachel. */
    private View withCheck(View t) {
        FrameLayout fl = new FrameLayout(c);
        fl.setLayoutParams(t.getLayoutParams());
        fl.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout b = Ui.badge(c, R.drawable.ic_check, Ui.primary(c), 0xFFFFFFFF, 24);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.dp(c, 4), Ui.dp(c, 4), 0);
        fl.addView(b, lp);
        return fl;
    }

    private LinearLayout rowShell() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(c, Ui.surface(c), 14));
        int p = Ui.dp(c, 12);
        card.setPadding(p, p, p, p);
        card.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.dp(c, 84)));
        return card;
    }

    private View folderRow(String name) {
        LinearLayout card = rowShell();
        ImageView ic = symbol(R.drawable.ic_folder, 0xFFE0A93B);
        card.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        TextView n = Ui.label(c, name, 15, Ui.text(c), false);
        n.setPadding(Ui.dp(c, 8), 0, 0, 0);
        card.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private View fileRow(RemoteFile f) {
        final String uri = Store.uri(c, f.key());
        LinearLayout card = rowShell();
        boolean picked = selected.contains(f.key());
        if (picked) {
            card.setBackground(Ui.round(c, Ui.chip(c), 14));
        }
        ImageView ic = symbol(picked ? R.drawable.ic_check : R.drawable.ic_file, picked ? Ui.accent(c) : Ui.primary(c));
        card.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 8), 0, 0, 0);
        TextView n = Ui.label(c, f.name, 15, Ui.text(c), false);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        text.addView(Ui.label(c, Ui.size(f.size) + (uri != null ? "  ·  auf dem Handy" : "  ·  noch nicht geladen"), 12,
                uri != null ? Ui.accent(c) : Ui.dim(c), false));
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    // ---- Aktionen ----------------------------------------------------------------------------

    private static String mime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (m != null) {
                return m;
            }
        }
        return "application/octet-stream";
    }

    private void open(RemoteFile f) {
        String uri = Store.uri(c, f.key());
        if (uri == null) {
            host.fetch(f);
            Toast.makeText(c, "Wird geladen …", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(uri), mime(f.name));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.activity().startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, "Keine App zum Öffnen gefunden. Halte die Datei gedrückt zum Teilen oder Speichern.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Langer Druck: Teilen oder an einen frei gewählten Ort speichern. */
    private void more(final RemoteFile f) {
        final String uri = Store.uri(c, f.key());
        if (uri == null) {
            host.fetch(f);
            Toast.makeText(c, "Wird geladen …", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(host.activity())
                .setTitle(f.name)
                .setItems(new CharSequence[] { "Speichern unter …", "Teilen" }, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            host.saveAs(f.name, mime(f.name), Uri.parse(uri));
                        } else {
                            share(f, uri);
                        }
                    }
                }).show();
    }

    private void share(RemoteFile f, String uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime(f.name));
        i.putExtra(Intent.EXTRA_STREAM, Uri.parse(uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.activity().startActivity(Intent.createChooser(i, "Teilen"));
        } catch (RuntimeException e) {
            Toast.makeText(c, "Teilen nicht möglich", Toast.LENGTH_LONG).show();
        }
    }
}
