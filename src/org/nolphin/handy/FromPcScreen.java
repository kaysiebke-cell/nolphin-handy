package org.nolphin.handy;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
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
import java.util.Collections;
import java.util.Comparator;
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
    private final HorizontalScrollView crumbScroll;
    private final GridView grid;
    private final TextView empty;
    private final ImageView toggle;
    private final LinearLayout head;
    private final LinearLayout normalBar;
    private final LinearLayout selectBar;
    private final TextView selCount;
    private final View selSave;
    private final LinearLayout actionBar;
    private final EditText search;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Aktueller Unterordner relativ zu „Handy“, mit "/" getrennt; leer = oberste Ebene. */
    private String dir = "";
    /** Übersicht (Handy-Ordner und persönlicher Ordner) statt einer Ordneransicht. */
    private boolean overview = true;
    private final android.widget.ScrollView homeScroll;
    private final LinearLayout homeBox;
    private final ImageView back;
    private final List<String> homeDirs = new ArrayList<>();
    private Boolean homeSupported;       // null = unbekannt, sonst: kennt der PC den persönlichen Ordner?
    private long lastOverview;
    private boolean overviewLoading;
    private boolean overviewFailed;     // der PC hat auf die letzte Anfrage nicht geantwortet
    private String overviewSig = "";
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

        head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(Ui.surface(c));
        head.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
        back = Ui.icon(c, R.drawable.ic_back, Ui.iconTint(c), 24);
        back.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
        blp.rightMargin = Ui.dp(c, 4);
        back.setLayoutParams(blp);
        back.setContentDescription("Zurück");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        head.addView(back);
        head.addView(Ui.label(c, "Vom PC", 18, Ui.text(c), true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        toggle = Ui.icon(c, useGrid ? R.drawable.ic_list : R.drawable.ic_grid, Ui.iconTint(c), 24);
        toggle.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        toggle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
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
        root.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));
        root.addView(divider());

        search = new EditText(c);
        search.setHint("Auf dem PC suchen");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(Ui.text(c));
        search.setHintTextColor(Ui.dim(c));
        search.setBackgroundColor(Ui.bg(c));
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
        root.addView(search, slp);
        root.addView(divider());

        normalBar = new LinearLayout(c);
        normalBar.setOrientation(LinearLayout.VERTICAL);
        normalBar.setPadding(Ui.dp(c, 8), 0, p, Ui.dp(c, 8));
        normalBar.setBackgroundColor(Ui.bg(c));
        final HorizontalScrollView scroll = new HorizontalScrollView(c);
        crumbScroll = scroll;
        scroll.setHorizontalScrollBarEnabled(false);
        crumbs = new LinearLayout(c);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(crumbs);
        // bei jeder Änderung des Pfads ans Ende scrollen, damit der aktuelle Ordner sichtbar bleibt
        crumbs.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol) {
                    crumbScroll.scrollTo(r - l, 0);
                }
            }
        });
        ImageView homeBtn = Ui.icon(c, R.drawable.ic_home, Ui.primary(c), 44);
        homeBtn.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        homeBtn.setContentDescription("Übersicht");
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goOverview();
            }
        });
        // erste Zeile: Haus-Symbol und Pfad; zweite Zeile: die Knöpfe, rechtsbündig
        LinearLayout crumbRow = new LinearLayout(c);
        crumbRow.setOrientation(LinearLayout.HORIZONTAL);
        crumbRow.setGravity(Gravity.CENTER_VERTICAL);
        crumbRow.addView(homeBtn);
        crumbRow.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        normalBar.addView(crumbRow);
        LinearLayout actionRow = new LinearLayout(c);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, Ui.dp(c, 2), 0, 0);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44));
        ap.leftMargin = Ui.dp(c, 8);
        actionRow.setPadding(0, Ui.dp(c, 2), Ui.dp(c, 0), 0);
        actionRow.addView(Ui.actionButton(c, "Auswählen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        }), ap);
        actionRow.addView(Ui.actionButton(c, "Alle laden", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadAll();
            }
        }), ap);
        normalBar.addView(actionRow);
        root.addView(normalBar);
        root.addView(divider());

        selectBar = new LinearLayout(c);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.setBackgroundColor(Ui.selBar(c));
        selectBar.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
        selectBar.setVisibility(View.GONE);
        selectBar.addView(barIcon(R.drawable.ic_close, "Auswahl beenden", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSelection();
            }
        }));
        selCount = Ui.label(c, "", 16, Color.WHITE, true);
        selCount.setSingleLine(true);
        selCount.setPadding(Ui.dp(c, 4), 0, 0, 0);
        selectBar.addView(selCount, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        selectBar.addView(barIcon(R.drawable.ic_checks, "Alle auswählen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        }));
        selectBar.addView(barIcon(R.drawable.ic_invert, "Auswahl umkehren", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                invertSelection();
            }
        }));
        // die Funktionen stehen unten in Daumenreichweite und ersetzen während der Auswahl die Reiter
        actionBar = new LinearLayout(c);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setBackgroundColor(Ui.surface(c));
        actionBar.setElevation(Ui.dp(c, 8));
        actionBar.setVisibility(View.GONE);
        actionBar.addView(actionItem(R.drawable.ic_download, "Laden", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadSelected();
            }
        }));
        actionBar.addView(actionItem(R.drawable.ic_share, "Teilen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSelected();
            }
        }));
        selSave = actionItem(R.drawable.ic_save, "Speichern", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSelected();
            }
        });
        actionBar.addView(selSave);
        root.addView(selectBar, root.indexOfChild(head) + 1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));

        homeScroll = new android.widget.ScrollView(c);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeBox = new LinearLayout(c);
        homeBox.setOrientation(LinearLayout.VERTICAL);
        homeBox.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 16));
        homeScroll.addView(homeBox);
        root.addView(homeScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

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
        root.addView(actionBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        applyMode();
        buildCrumbs();
    }

    private void applyMode() {
        int cols = useGrid ? 3 : 1;
        grid.setNumColumns(cols);
        // Kacheln mit Abstand; die Liste als durchgehende Fläche mit Trennlinien
        grid.setVerticalSpacing(useGrid ? Ui.dp(c, 6) : 0);
        grid.setHorizontalSpacing(useGrid ? Ui.dp(c, 6) : 0);
        grid.setPadding(useGrid ? Ui.dp(c, 10) : 0, 0, useGrid ? Ui.dp(c, 10) : 0, Ui.dp(c, 10));
        toggle.setImageResource(useGrid ? R.drawable.ic_list : R.drawable.ic_grid);
        adapter.notifyDataSetChanged();
    }

    // ---- Navigation ------------------------------------------------------------------------

    private void go(String to) {
        overview = false;
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
        if (overview) {
            return false;
        }
        if (dir.isEmpty() || dir.equals("~")) {
            goOverview();
            return true;
        }
        int slash = dir.lastIndexOf('/');
        go(slash < 0 ? "" : dir.substring(0, slash));
        return true;
    }

    private void goOverview() {
        selected.clear();
        overview = true;
        signature = "";
        overviewSig = "";
        lastOverview = 0;
        refresh();
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        boolean personal = dir.equals("~") || dir.startsWith("~/");
        String rootKey = personal ? "~" : "";
        String rel = personal ? (dir.length() > 2 ? dir.substring(2) : "") : dir;
        crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
        addCrumb(personal ? "Persönlich" : "Handy-Ordner", rootKey, rel.isEmpty());
        if (!rel.isEmpty()) {
            String[] parts = rel.split("/");
            StringBuilder path = new StringBuilder(personal ? "~" : "");
            for (int i = 0; i < parts.length; i++) {
                if (path.length() > 0) {
                    path.append('/');
                }
                path.append(parts[i]);
                crumbs.addView(Ui.label(c, "  ›  ", 14, Ui.dim(c), false));
                addCrumb(parts[i], path.toString(), i == parts.length - 1);
            }
        }
        // der aktuelle (letzte) Teil soll sichtbar sein, auch bei langen Pfaden
        crumbScroll.post(new Runnable() {
            @Override
            public void run() {
                crumbScroll.scrollTo(crumbs.getWidth(), 0);
            }
        });
    }

    private void addCrumb(String text, final String target, boolean last) {
        TextView t = Ui.label(c, text, 14, last ? Ui.primary(c) : Ui.dim(c), last);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14));
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
                    URL url = new URL("https://" + host + "/dir?t=" + Uri.encode(token) + RemoteFile.params(want));
                    HttpURLConnection h = Secure.connect(url, Conn.pin(c));
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
        homeScroll.setVisibility(overview ? View.VISIBLE : View.GONE);
        search.setVisibility(overview ? View.GONE : View.VISIBLE);
        toggle.setVisibility(overview ? View.GONE : View.VISIBLE);
        back.setVisibility(overview ? View.GONE : View.VISIBLE);
        if (overview) {
            normalBar.setVisibility(View.GONE);
            selectBar.setVisibility(View.GONE);
            grid.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            refreshOverview(connected);
            return;
        }
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

    // ---- Übersicht ---------------------------------------------------------------------------

    private void refreshOverview(boolean connected) {
        if (connected && !overviewLoading && System.currentTimeMillis() - lastOverview > 10000) {
            loadOverview();
        }
        String sig = connected + "|" + homeSupported + "|" + homeDirs + "|" + overviewFailed;
        if (!sig.equals(overviewSig)) {
            overviewSig = sig;
            buildOverview(connected);
        }
    }

    /** Fragt den PC, ob er den persönlichen Ordner kennt (/info), und holt dessen Ordner der obersten Ebene. */
    private void loadOverview() {
        final String host = Conn.host(c);
        final String token = Conn.token(c);
        if (host == null || token == null) {
            return;
        }
        overviewLoading = true;
        lastOverview = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Boolean supported = null;
                final List<String> found = new ArrayList<>();
                try {
                    String info = httpText("https://" + host + "/info?t=" + Uri.encode(token), Conn.pin(c));
                    supported = info != null && info.contains("\"home\":true");
                    if (supported) {
                        String dirsJson = httpText("https://" + host + "/dir?t=" + Uri.encode(token) + "&r=home", Conn.pin(c));
                        if (dirsJson != null) {
                            JSONArray d = new JSONObject(dirsJson).getJSONArray("dirs");
                            for (int i = 0; i < d.length(); i++) {
                                found.add(d.getJSONObject(i).getString("n"));
                            }
                        } else {
                            supported = null;
                        }
                    }
                } catch (Exception ignored) {
                    supported = null;
                }
                final Boolean sup = supported;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        overviewLoading = false;
                        homeSupported = sup;
                        overviewFailed = sup == null;
                        homeDirs.clear();
                        homeDirs.addAll(found);
                        overviewSig = "";
                        refresh();
                    }
                });
            }
        }, "nolphin-overview").start();
    }

    private static String httpText(String address, String pin) throws java.io.IOException {
        HttpURLConnection h = Secure.connect(new URL(address), pin);
        h.setConnectTimeout(5000);
        h.setReadTimeout(10000);
        try {
            if (h.getResponseCode() != 200) {
                return null;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = h.getInputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                }
            }
            return buf.toString("UTF-8");
        } finally {
            h.disconnect();
        }
    }

    private int folderIcon(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("bilder") || n.equals("pictures")) {
            return R.drawable.ic_image;
        }
        if (n.equals("musik") || n.equals("music")) {
            return R.drawable.ic_audio;
        }
        if (n.equals("videos") || n.equals("video")) {
            return R.drawable.ic_video;
        }
        if (n.equals("dokumente") || n.equals("documents")) {
            return R.drawable.ic_doc;
        }
        if (n.equals("downloads") || n.equals("herunterladen")) {
            return R.drawable.ic_download;
        }
        return R.drawable.ic_folder;
    }

    private void buildOverview(boolean connected) {
        homeBox.removeAllViews();
        if (!connected) {
            LinearLayout card = Ui.card(c);
            card.addView(Ui.label(c, "Nicht mit dem PC verbunden", 18, Ui.text(c), true));
            TextView d = Ui.label(c, "Verbinde dich zuerst auf der Startseite.", 14, Ui.dim(c), false);
            d.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
            card.addView(d);
            card.addView(Ui.button(c, "Zur Startseite", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.show(Host.TAB_HOME);
                }
            }));
            homeBox.addView(card);
            return;
        }
        homeBox.addView(Ui.heading(c, "Handy-Ordner"));
        LinearLayout phone = Ui.group(c);
        Ui.addRow(c, phone, Ui.row(c, R.drawable.ic_phone, Ui.iconTint(c), "Zum Handy", "Vom PC bereitgestellt", null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        go("");
                    }
                }));
        homeBox.addView(phone);

        homeBox.addView(Ui.heading(c, "Persönlicher Ordner"));
        if (homeSupported == null) {
            homeBox.addView(Ui.label(c, overviewFailed ? "PC nicht erreichbar. Läuft der Handy-Austausch in Nolphin?" : "Lade …",
                    14, Ui.dim(c), false));
            return;
        }
        if (!homeSupported) {
            homeBox.addView(Ui.label(c, "Dieser PC kennt den persönlichen Ordner noch nicht. Nolphin am PC aktualisieren.", 14,
                    Ui.dim(c), false));
            return;
        }
        // Standardordner zuerst, danach höchstens sechs weitere; der Rest steht hinter „Alle Ordner“
        List<String> standard = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String d : homeDirs) {
            (isStandardFolder(d) ? standard : other).add(d);
        }
        Collections.sort(standard, new Comparator<String>() {
            @Override
            public int compare(String x, String y) {
                return standardRank(x) - standardRank(y);
            }
        });
        LinearLayout home = Ui.group(c);
        Ui.addRow(c, home, Ui.row(c, R.drawable.ic_home, Ui.iconTint(c), "Persönlicher Ordner",
                "Alles auf dem PC durchsuchen", null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        go("~");
                    }
                }));
        for (String d : standard) {
            Ui.addRow(c, home, folderShortcut(d));
        }
        homeBox.addView(home);
        if (!other.isEmpty()) {
            homeBox.addView(Ui.heading(c, "Weitere Ordner"));
            LinearLayout more = Ui.group(c);
            for (String d : other.subList(0, Math.min(6, other.size()))) {
                Ui.addRow(c, more, folderShortcut(d));
            }
            if (other.size() > 6) {
                Ui.addRow(c, more, Ui.row(c, R.drawable.ic_folder, Ui.folder(c), "Alle Ordner anzeigen",
                        other.size() - 6 + " weitere", null, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                go("~");
                            }
                        }));
            }
            homeBox.addView(more);
        }
    }

    private static final String[] STANDARD = { "bilder", "pictures", "dokumente", "documents", "downloads", "musik",
            "music", "videos", "schreibtisch", "desktop" };

    private static boolean isStandardFolder(String name) {
        return standardRank(name) < STANDARD.length;
    }

    private static int standardRank(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < STANDARD.length; i++) {
            if (STANDARD[i].equals(n)) {
                return i;
            }
        }
        return STANDARD.length;
    }

    private View folderShortcut(final String name) {
        int icon = folderIcon(name);
        return Ui.row(c, icon, icon == R.drawable.ic_folder ? Ui.folder(c) : Ui.iconTint(c), name, null, null,
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                go("~/" + name);
            }
        });
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

    /** Kästchen für die Auswahl. */
    private View checkbox(boolean on) {
        TextView t = new TextView(c);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14);
        t.setTextColor(Ui.dark(c) ? 0xFF10200A : Color.WHITE);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Ui.dp(c, 3));
        g.setColor(on ? Ui.primary(c) : 0x00000000);
        g.setStroke(Ui.dp(c, 2), on ? Ui.primary(c) : Ui.dim(c));
        t.setBackground(g);
        t.setText(on ? "✓" : "");
        return t;
    }

    private ImageView barIcon(int icon, String description, View.OnClickListener l) {
        ImageView v = Ui.icon(c, icon, Color.WHITE, 24);
        v.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        v.setContentDescription(description);
        v.setBackground(Ui.pressable(c));
        v.setOnClickListener(l);
        return v;
    }

    private View divider() {
        View v = new View(c);
        v.setBackgroundColor(Ui.line(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1) / 2 + 1)));
        return v;
    }

    private void updateBars() {
        boolean sel = !selected.isEmpty();
        normalBar.setVisibility(sel ? View.GONE : View.VISIBLE);
        selectBar.setVisibility(sel ? View.VISIBLE : View.GONE);
        selCount.setText(selected.size() + " gewählt");
        head.setVisibility(sel ? View.GONE : View.VISIBLE);
        selSave.setVisibility(selected.size() == 1 ? View.VISIBLE : View.GONE);
        actionBar.setVisibility(sel ? View.VISIBLE : View.GONE);
        if (root.isShown()) {
            host.setNavVisible(!sel);
        }
    }

    private void invertSelection() {
        for (RemoteFile f : shownFiles) {
            if (!selected.remove(f.key())) {
                selected.add(f.key());
            }
        }
        signature = "";
        refresh();
    }

    /** Knopf der unteren Aktionsleiste: Symbol mit Beschriftung darunter. */
    private View actionItem(int icon, String label, View.OnClickListener l) {
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        item.setBackground(Ui.pressable(c));
        item.setContentDescription(label);
        item.addView(Ui.icon(c, icon, Ui.primary(c), 24));
        TextView t = Ui.label(c, label, 11, Ui.primary(c), false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        item.addView(t);
        item.setOnClickListener(l);
        item.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return item;
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
        t.setBackground(Ui.outlined(c, picked ? Ui.chip(c) : Ui.surface(c)));
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        t.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.dp(c, 148)));
        t.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 64)));
        TextView n = Ui.label(c, name, 13, Ui.text(c), false);
        n.setMaxLines(2);
        n.setGravity(Gravity.CENTER);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        n.setPadding(Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2), 0);
        t.addView(n);
        t.addView(Ui.label(c, sub, 11, subColor, false));
        return picked ? withCheck(t) : t;
    }

    /** Ordnerkachel im gleichen Stil wie die Dateisymbole. */
    private ImageView folderTile48() {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageDrawable(Ui.folderIcon(c));
        v.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        return v;
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
        return tile(folderTile48(), name, "Ordner", Ui.dim(c), false);
    }

    /** Dateisymbol mit Endung; bei geladenen Dateien ersetzt das Vorschaubild es, wenn es eines gibt. */
    private ImageView fileIcon(RemoteFile f) {
        final ImageView top = new ImageView(c);
        top.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        top.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        final android.graphics.drawable.Drawable type = Ui.typeIcon(c, Ui.ext(f.name));
        top.setImageDrawable(type);
        String uri = Store.uri(c, f.key());
        String m = mime(f.name);
        if (uri != null && (m.startsWith("image/") || m.startsWith("video/"))) {
            top.setBackground(Ui.round(c, 0x00000000, 8));
            top.setClipToOutline(true);
            top.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            Thumbs.load(c, Uri.parse(uri), 168, top, new Runnable() {
                @Override
                public void run() {
                    top.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    top.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
                    top.setImageDrawable(type);
                }
            });
        }
        return top;
    }

    private View fileTile(RemoteFile f) {
        final String uri = Store.uri(c, f.key());
        return tile(fileIcon(f), f.name, Ui.size(f.size) + (uri != null ? " · Handy" : ""),
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

    private LinearLayout rowShell(boolean picked) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.listRowBg(c, picked));
        row.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        row.setMinimumHeight(Ui.dp(c, 72));
        row.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View folderRow(String name) {
        LinearLayout row = rowShell(false);
        row.addView(folderTile48(), new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        TextView n = Ui.label(c, name, 16, Ui.text(c), false);
        n.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.icon(c, R.drawable.ic_chevron, Ui.dim(c), 20));
        return row;
    }

    private View fileRow(RemoteFile f) {
        final String uri = Store.uri(c, f.key());
        boolean picked = selected.contains(f.key());
        LinearLayout row = rowShell(picked);
        if (!selected.isEmpty()) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
            cp.rightMargin = Ui.dp(c, 14);
            row.addView(checkbox(picked), cp);
        }
        row.addView(fileIcon(f), new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
        TextView n = Ui.label(c, f.name, 16, Ui.text(c), false);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        TextView s = Ui.label(c, Ui.size(f.size) + (uri != null ? "  ·  auf dem Handy" : "  ·  noch nicht geladen"), 13,
                uri != null ? Ui.accent(c) : Ui.dim(c), false);
        s.setPadding(0, Ui.dp(c, 2), 0, 0);
        text.addView(s);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
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
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
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
