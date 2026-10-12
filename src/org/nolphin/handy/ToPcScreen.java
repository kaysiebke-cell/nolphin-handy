package org.nolphin.handy;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
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
    private static final String[] CATS = { "Bilder", "Audio", "Videos", "Dokumente" };
    private static final int[] CAT_ICONS = { R.drawable.ic_image, R.drawable.ic_audio, R.drawable.ic_video, R.drawable.ic_doc };

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
    private final HorizontalScrollView crumbScroll;
    private final LinearLayout head;
    private final LinearLayout selectBar;
    private final TextView selCount;
    private final FrameLayout body;
    private final android.widget.ScrollView homeScroll;
    private final LinearLayout homeBox;
    private final ImageView back;
    private final GridView grid;
    private final TextView empty;
    private final LinearLayout permCard;
    private final ImageView newFolder;
    private final LinearLayout pasteBar;
    private final TextView pasteText;
    /** Endungen, die unter „Dokumente“ erscheinen (keine Programm- und Systemdateien anderer Apps). */
    private static final String[] DOC_EXT = { "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv",
            "ppt", "pptx", "odp", "epub", "zip", "rar", "7z", "step", "stp", "stl", "obj", "3mf", "dxf", "dwg", "iges", "igs" };
    private static final String[] DOC_FILTERS = { "Alle", "DOC", "XLS", "PDF", "PPT", "Sonstige" };
    private int docFilter;
    private static final String[] STORAGE = { "Beides", "Intern", "SD-Karte" };
    private int storage;                  // 0 beide Speicher, 1 intern, 2 SD-Karte
    private final LinearLayout storageRow;
    private final View storageLine;
    private final LinearLayout docChips;
    private final HorizontalScrollView docChipScroll;
    private List<File> clip;              // zum Einfügen vorgemerkt (Kopieren oder Verschieben)
    private boolean clipMove;

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
        back.setVisibility(View.GONE);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack();
            }
        });
        head.addView(back);
        title = Ui.label(c, "Zum PC", 18, Ui.text(c), true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        newFolder = Ui.icon(c, R.drawable.ic_newfolder, Ui.iconTint(c), 24);
        newFolder.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        newFolder.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        newFolder.setContentDescription("Neuer Ordner");
        newFolder.setVisibility(View.GONE);
        newFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askNewFolder();
            }
        });
        head.addView(newFolder);
        toggle = Ui.icon(c, useGrid ? R.drawable.ic_list : R.drawable.ic_grid, Ui.iconTint(c), 24);
        toggle.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        toggle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
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
        root.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));
        root.addView(divider());

        LinearLayout searchRow = new LinearLayout(c);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackgroundColor(Ui.bg(c));
        searchRow.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
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
        root.addView(searchRow, slp);
        root.addView(divider());

        crumbBar = new LinearLayout(c);
        crumbBar.setOrientation(LinearLayout.HORIZONTAL);
        crumbBar.setGravity(Gravity.CENTER_VERTICAL);
        crumbBar.setPadding(Ui.dp(c, 8), 0, p, 0);
        crumbBar.setBackgroundColor(Ui.bg(c));
        HorizontalScrollView scroll = new HorizontalScrollView(c);
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
                goHome();
            }
        });
        
        crumbBar.addView(homeBtn);
        crumbBar.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        crumbBar.setVisibility(View.GONE);
        root.addView(crumbBar);
        root.addView(divider());

        HorizontalScrollView chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        docChips = new LinearLayout(c);
        docChips.setOrientation(LinearLayout.HORIZONTAL);
        docChips.setPadding(Ui.dp(c, 4), 0, p, 0);
        chipScroll.setBackgroundColor(Ui.bg(c));
        chipScroll.addView(docChips);
        chipScroll.setVisibility(View.GONE);
        docChipScroll = chipScroll;
        root.addView(chipScroll);

        storageRow = new LinearLayout(c);
        storageRow.setOrientation(LinearLayout.HORIZONTAL);
        storageRow.setBackgroundColor(Ui.bg(c));
        storageRow.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        storageRow.setVisibility(View.GONE);
        root.addView(storageRow);
        storageLine = divider();
        storageLine.setVisibility(View.GONE);
        root.addView(storageLine);

        // Auswahl: die Leiste oben wird grün und zeigt die Funktionen (wie im Systemmanager)
        selectBar = new LinearLayout(c);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.setBackgroundColor(Ui.selBar(c));
        selectBar.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
        selectBar.setVisibility(View.GONE);
        selectBar.addView(barIcon(R.drawable.ic_close, "Auswahl beenden", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected.clear();
                render();
            }
        }));
        selCount = Ui.label(c, "", 16, Color.WHITE, true);
        selCount.setSingleLine(true);
        selCount.setPadding(Ui.dp(c, 4), 0, 0, 0);
        selectBar.addView(selCount, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        selectBar.addView(barIcon(R.drawable.ic_upload, "An den PC senden", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSelected();
            }
        }));
        selectBar.addView(barIcon(R.drawable.ic_copy, "Kopieren", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markClip(false);
            }
        }));
        selectBar.addView(barIcon(R.drawable.ic_move, "Verschieben", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markClip(true);
            }
        }));
        selectBar.addView(barIcon(R.drawable.ic_trash, "Löschen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askDelete();
            }
        }));
        selectBar.addView(barIcon(R.drawable.ic_more, "Mehr: Umbenennen, Eigenschaften", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                more();
            }
        }));
        root.addView(selectBar, root.indexOfChild(head) + 1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));

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
                if (e.dir && selected.isEmpty()) {
                    openFolder(e.file);
                } else {
                    toggleSelect(e);
                }
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int i, long id) {
                toggleSelect(entries.get(i));
                return true;
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

        pasteBar = new LinearLayout(c);
        pasteBar.setOrientation(LinearLayout.HORIZONTAL);
        pasteBar.setGravity(Gravity.CENTER_VERTICAL);
        pasteBar.setBackgroundColor(Ui.surface(c));
        pasteBar.setElevation(Ui.dp(c, 6));
        pasteBar.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        pasteText = Ui.label(c, "", 14, Ui.text(c), true);
        pasteBar.addView(pasteText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44));
        pb.leftMargin = Ui.dp(c, 8);
        pasteBar.addView(Ui.actionButton(c, "Abbrechen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clip = null;
                render();
            }
        }), pb);
        pasteBar.addView(Ui.actionButton(c, "Hierher", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteHere();
            }
        }), pb);
        pasteBar.setVisibility(View.GONE);
        root.addView(pasteBar);

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
        // Kacheln mit Abstand; die Liste als durchgehende Fläche mit Trennlinien
        grid.setVerticalSpacing(useGrid ? Ui.dp(c, 6) : 0);
        grid.setHorizontalSpacing(useGrid ? Ui.dp(c, 6) : 0);
        grid.setPadding(useGrid ? Ui.dp(c, 10) : 0, Ui.dp(c, 4), useGrid ? Ui.dp(c, 10) : 0, Ui.dp(c, 10));
        toggle.setImageResource(useGrid ? R.drawable.ic_list : R.drawable.ic_grid);
        adapter.notifyDataSetChanged();
    }

    // ---- Übersicht ---------------------------------------------------------------------------

    private void buildHome() {
        homeBox.removeAllViews();
        homeBox.addView(heading("Speicher"));
        LinearLayout store = Ui.group(c);
        for (final Vol v : vols) {
            Ui.addRow(c, store, storageRow(v));
        }
        homeBox.addView(store);
        homeBox.addView(heading("Schnellzugriff"));
        LinearLayout quick = Ui.group(c);
        Ui.addRow(c, quick, Ui.row(c, R.drawable.ic_download, Ui.iconTint(c), "Downloads", downloadsSub, null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openFolder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                    }
                }));
        Ui.addRow(c, quick, Ui.row(c, R.drawable.ic_apps, Ui.iconTint(c), "Apps", appsSub, null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openApps();
                    }
                }));
        Ui.addRow(c, quick, Ui.row(c, R.drawable.ic_image, Ui.iconTint(c), "Google Fotos",
                "Fotos und Videos aus der Cloud, auch ohne Speicher auf dem Handy", null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ToPcScreen.this.host.pickPhotos();
                    }
                }));
        // Cloud und andere Apps (Google Drive, Google Fotos, Dateien anderer Apps): über die Systemauswahl,
        // denn deren Daten liegen nicht als Dateien auf dem Handy, sondern hinter einem Dokumentenanbieter.
        Ui.addRow(c, quick, Ui.row(c, R.drawable.ic_cloud, Ui.iconTint(c), "Cloud und andere Apps",
                "Google Drive und Dateien anderer Apps", null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ToPcScreen.this.host.pickFiles();
                    }
                }));
        homeBox.addView(quick);
        homeBox.addView(heading("Kategorien"));
        LinearLayout cats = Ui.group(c);
        for (int k = 0; k < CATS.length; k++) {
            Ui.addRow(c, cats, category(k));
        }
        homeBox.addView(cats);
    }

    private View category(final int k) {
        return Ui.row(c, CAT_ICONS[k], Ui.iconTint(c), CATS[k], catSub[k], null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCategory(k);
            }
        });
    }

    private TextView heading(String text) {
        return Ui.heading(c, text);
    }

    /** Zeile für einen Speicher: Name, Belegung und ein schmaler Füllstandsbalken darunter. */
    private View storageRow(final Vol v) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.pressable(c));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 14));
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
        top.addView(Ui.icon(c, v.card ? R.drawable.ic_sd : R.drawable.ic_storage, Ui.iconTint(c), 24));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
        text.addView(Ui.label(c, v.name, 16, Ui.text(c), true));
        text.addView(Ui.label(c, total > 0 ? Ui.size(used) + " von " + Ui.size(total) + " belegt" : "", 12, Ui.dim(c), false));
        top.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(Ui.label(c, pct + " %", 14, pct > 90 ? Ui.danger(c) : Ui.primary(c), true));
        row.addView(top);
        LinearLayout track = new LinearLayout(c);
        track.setWeightSum(100);
        track.setBackground(Ui.round(c, Ui.line(c), 3));
        View fill = new View(c);
        fill.setBackground(Ui.round(c, pct > 90 ? Ui.danger(c) : Ui.primary(c), 3));
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(pct, 2)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 6));
        tp.topMargin = Ui.dp(c, 10);
        tp.leftMargin = Ui.dp(c, 40);
        row.addView(track, tp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                openFolder(v.root);
            }
        });
        return row;
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
                    sub[i] = n == 0 ? "leer" : Ui.size(bytes) + " · " + n + (n == 1 ? " Datei" : " Dateien");
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
            default: {
                StringBuilder names = new StringBuilder();
                for (String e : DOC_EXT) {
                    names.append(names.length() == 0 ? "" : " OR ")
                            .append(MediaStore.Files.FileColumns.DISPLAY_NAME).append(" LIKE '%.").append(e).append("'");
                }
                // Ab Android 11 haben PDFs und Office-Dateien einen eigenen Medientyp: nur Bilder, Audio und Video ausschließen
                return t + " NOT IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
                        + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ") AND " + m + " IS NOT NULL AND "
                        + MediaStore.Files.FileColumns.DATA + " NOT LIKE '%/Android/data/%' AND "
                        + MediaStore.Files.FileColumns.DATA + " NOT LIKE '%/Android/obb/%' AND (" + names + ")";
            }
        }
    }

    // ---- Navigation --------------------------------------------------------------------------

    private static String docGroup(String name) {
        String e = Ui.ext(name);
        switch (e) {
            case "doc": case "docx": case "odt": case "rtf": case "txt": case "md":
                return "DOC";
            case "xls": case "xlsx": case "ods": case "csv":
                return "XLS";
            case "pdf":
                return "PDF";
            case "ppt": case "pptx": case "odp":
                return "PPT";
            default:
                return "Sonstige";
        }
    }

    private boolean onCard(File f) {
        Vol v = volumeOf(f);
        return v != null && v.card;
    }

    private boolean passesStorage(File f) {
        return storage == 0 || (storage == 2) == onCard(f);
    }

    private boolean hasCard() {
        for (Vol v : vols) {
            if (v.card) {
                return true;
            }
        }
        return false;
    }

    private boolean passesFilter(Entry e) {
        if (mode == CATEGORY && !passesStorage(e.file)) {
            return false;
        }
        return mode != CATEGORY || cat != 3 || docFilter == 0 || docGroup(e.file.getName()).equals(DOC_FILTERS[docFilter]);
    }

    /** Schalter Beides / Intern / SD-Karte: einfache Reiter wie bei den Dokumenten. */
    private void buildStorageRow() {
        storageRow.removeAllViews();
        for (int i = 0; i < STORAGE.length; i++) {
            final int k = i;
            boolean on = i == storage;
            LinearLayout tab = new LinearLayout(c);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setBackground(Ui.pressable(c));
            TextView t = Ui.label(c, STORAGE[i], 14, on ? Ui.primary(c) : Ui.dim(c), on);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 8));
            tab.addView(t);
            View bar = new View(c);
            bar.setBackgroundColor(on ? Ui.primary(c) : 0x00000000);
            tab.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 3)));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    storage = k;
                    selected.clear();
                    applyQuery();
                }
            });
            storageRow.addView(tab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    private void buildDocChips() {
        docChips.removeAllViews();
        for (int i = 0; i < DOC_FILTERS.length; i++) {
            final int k = i;
            boolean on = i == docFilter;
            LinearLayout tab = new LinearLayout(c);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setBackground(Ui.pressable(c));
            TextView t = Ui.label(c, DOC_FILTERS[i], 15, on ? Ui.primary(c) : Ui.dim(c), on);
            t.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 10));
            tab.addView(t);
            View bar = new View(c);
            bar.setBackgroundColor(on ? Ui.primary(c) : 0x00000000);
            tab.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 3)));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    docFilter = k;
                    selected.clear();
                    applyQuery();
                }
            });
            docChips.addView(tab);
        }
    }

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
        docFilter = 0;
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

    /** Ordner, in dem die Datei liegt; bei der SD-Karte mit dem Zusatz "SD-Karte", damit klar ist, wo sie liegt. */
    private String parentName(File f) {
        File p = f.getParentFile();
        String name = p == null ? "" : p.getName();
        Vol v = volumeOf(f);
        return v != null && v.card ? "SD-Karte · " + name : name;
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
                for (Entry e : all) {
                    if (passesFilter(e)) {
                        entries.add(e);
                    }
                }
            }
            render();
            return;
        }
        if (mode == CATEGORY || mode == APPS) {
            entries.clear();
            for (Entry e : all) {
                if (nameOf(e).toLowerCase(Locale.ROOT).contains(q) && passesFilter(e)) {
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
                if (storage == 0 || (storage == 2) == v.card) {
                    roots.add(v.root);
                }
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
            selected.put(e.path(), e.size);
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
        final List<String> paths = new ArrayList<>(selected.keySet());
        selected.clear();
        render();
        boolean hasDir = false;
        for (String path : paths) {
            if (new File(path).isDirectory()) {
                hasDir = true;
            }
        }
        if (!hasDir) {
            host.sendPaths(paths);
            return;
        }
        Toast.makeText(c, "Ordner werden gepackt …", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> out = new ArrayList<>();
                String err = null;
                for (String path : paths) {
                    File f = new File(path);
                    try {
                        out.add(f.isDirectory() ? FileOps.zip(f, c.getCacheDir()).getPath() : path);
                    } catch (java.io.IOException e) {
                        err = e.getMessage();
                    }
                }
                final String fe = err;
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fe != null) {
                            Toast.makeText(c, "Packen nicht möglich: " + fe, Toast.LENGTH_LONG).show();
                        }
                        host.sendPaths(out);
                    }
                });
            }
        }, "nolphin-zip").start();
    }

    // ---- Dateiverwaltung: Kopieren, Verschieben, Löschen, Umbenennen, Neuer Ordner ------------

    private View barIcon(int icon, String description, View.OnClickListener l) {
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

    private List<File> selectedFiles() {
        List<File> l = new ArrayList<>();
        for (String path : selected.keySet()) {
            l.add(new File(path));
        }
        return l;
    }

    private void reload() {
        if (!query.isEmpty()) {
            applyQuery();
        } else if (mode == FOLDER || mode == CATEGORY || mode == APPS) {
            loadList();
        } else {
            render();
        }
    }

    /** Merkt die Auswahl zum Einfügen vor; dann einen Zielordner öffnen und "Hierher" tippen. */
    private void markClip(boolean move) {
        if (selected.isEmpty()) {
            return;
        }
        clip = selectedFiles();
        clipMove = move;
        selected.clear();
        Toast.makeText(c, "Zielordner öffnen und „Hierher“ tippen", Toast.LENGTH_LONG).show();
        if (mode != FOLDER) {
            goHome();
        }
        render();
    }

    private void pasteHere() {
        if (clip == null || mode != FOLDER) {
            return;
        }
        final List<File> items = clip;
        final boolean move = clipMove;
        final File target = dir;
        clip = null;
        render();
        runOp(move ? "Verschiebe …" : "Kopiere …", new Op() {
            @Override
            public String run() {
                int ok = 0;
                String err = null;
                for (File f : items) {
                    try {
                        File r = move ? FileOps.moveTo(f, target) : FileOps.copyTo(f, target);
                        scan(r);
                        if (move) {
                            scan(f);
                        }
                        ok++;
                    } catch (java.io.IOException e) {
                        err = e.getMessage();
                    }
                }
                String done = ok + (ok == 1 ? " Element " : " Elemente ") + (move ? "verschoben" : "kopiert");
                return err == null ? done : done + ". Fehler: " + err;
            }
        });
    }

    private void askDelete() {
        if (selected.isEmpty()) {
            return;
        }
        final List<File> items = selectedFiles();
        String what = items.size() == 1 ? "„" + items.get(0).getName() + "“" : items.size() + " Elemente";
        new AlertDialog.Builder(host.activity())
                .setTitle("Löschen?")
                .setMessage(what + " wird endgültig gelöscht. Das lässt sich nicht rückgängig machen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        selected.clear();
                        render();
                        runOp("Lösche …", new Op() {
                            @Override
                            public String run() {
                                int ok = 0;
                                String err = null;
                                for (File f : items) {
                                    try {
                                        FileOps.deleteTree(f);
                                        scan(f);
                                        ok++;
                                    } catch (java.io.IOException e) {
                                        err = e.getMessage();
                                    }
                                }
                                String done = ok + (ok == 1 ? " Element " : " Elemente ") + "gelöscht";
                                return err == null ? done : done + ". Fehler: " + err;
                            }
                        });
                    }
                }).show();
    }

    private void more() {
        if (selected.isEmpty()) {
            return;
        }
        final File one = selected.size() == 1 ? new File(selected.keySet().iterator().next()) : null;
        final String[] names = one != null ? new String[] { "Alle auswählen", "Umbenennen", "Eigenschaften" }
                : new String[] { "Alle auswählen" };
        new AlertDialog.Builder(host.activity())
                .setTitle(one != null ? one.getName() : selected.size() + " Elemente gewählt")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            selectAll();
                        } else if (which == 1) {
                            askRename(one);
                        } else {
                            showProperties(one);
                        }
                    }
                }).show();
    }

    /** Der Knopf ⋮ in jeder Zeile: alle Funktionen für dieses eine Element, gut lesbar in einer Liste. */
    private void rowMenu(final Entry e) {
        final File f = e.file;
        final String[] names = { "An den PC senden" + (e.dir ? " (als ZIP)" : ""), "Kopieren", "Verschieben",
                "Umbenennen", "Eigenschaften", "Löschen" };
        new AlertDialog.Builder(host.activity())
                .setTitle(f.getName())
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        selected.clear();
                        selected.put(f.getPath(), e.size);
                        switch (which) {
                            case 0: sendSelected(); break;
                            case 1: markClip(false); break;
                            case 2: markClip(true); break;
                            case 3: askRename(f); break;
                            case 4: selected.clear(); showProperties(f); break;
                            default: askDelete(); break;
                        }
                    }
                }).show();
    }

    private EditText nameField(String text) {
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setText(text);
        e.setSelectAllOnFocus(true);
        e.setTextSize(17);
        return e;
    }

    private void askRename(final File f) {
        final EditText e = nameField(f.getName());
        new AlertDialog.Builder(host.activity())
                .setTitle("Umbenennen")
                .setView(e)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Umbenennen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        final String n = e.getText().toString();
                        selected.clear();
                        runOp("Benenne um …", new Op() {
                            @Override
                            public String run() {
                                try {
                                    File r = FileOps.rename(f, n);
                                    scan(f);
                                    scan(r);
                                    return "Umbenannt in „" + r.getName() + "“";
                                } catch (java.io.IOException ex) {
                                    return "Nicht umbenannt: " + ex.getMessage();
                                }
                            }
                        });
                    }
                }).show();
    }

    private void askNewFolder() {
        if (mode != FOLDER) {
            return;
        }
        final File parent = dir;
        final EditText e = nameField("");
        e.setHint("Name des Ordners");
        new AlertDialog.Builder(host.activity())
                .setTitle("Neuer Ordner")
                .setView(e)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Anlegen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        final String n = e.getText().toString();
                        runOp("Lege an …", new Op() {
                            @Override
                            public String run() {
                                try {
                                    return "Ordner „" + FileOps.newFolder(parent, n).getName() + "“ angelegt";
                                } catch (java.io.IOException ex) {
                                    return "Nicht angelegt: " + ex.getMessage();
                                }
                            }
                        });
                    }
                }).show();
    }

    private void showProperties(File f) {
        long[] m = f.isDirectory() ? FileOps.measure(f) : new long[] { f.length(), 1 };
        String text = (f.isDirectory() ? "Ordner" : "Datei") + "\n\nOrt: " + f.getParent()
                + "\nGröße: " + Ui.size(m[0]) + (f.isDirectory() ? "\nDateien darin: " + m[1] : "")
                + "\nGeändert: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(new Date(f.lastModified()));
        new AlertDialog.Builder(host.activity()).setTitle(f.getName()).setMessage(text).setPositiveButton("Schließen", null).show();
    }

    private interface Op {
        /** Läuft im Hintergrund; liefert die Meldung für den Nutzer. */
        String run();
    }

    private void runOp(final String start, final Op op) {
        Toast.makeText(c, start, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String msg = op.run();
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
                        reload();
                    }
                });
            }
        }, "nolphin-fileops").start();
    }

    /** Sagt der Galerie und den anderen Apps, dass sich eine Datei geändert hat. */
    private void scan(File f) {
        if (f != null && !f.isDirectory()) {
            android.media.MediaScannerConnection.scanFile(c, new String[] { f.getPath() }, null, null);
        }
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
            pasteBar.setVisibility(View.GONE);
            newFolder.setVisibility(View.GONE);
            docChipScroll.setVisibility(View.GONE);
            storageRow.setVisibility(View.GONE);
            storageLine.setVisibility(View.GONE);
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

        long total = 0;
        for (long sz : selected.values()) {
            total += sz;
        }
        selCount.setText(selected.size() + " gewählt");
        head.setVisibility(sel ? View.GONE : View.VISIBLE);
        pasteBar.setVisibility(clip != null && mode == FOLDER && !sel ? View.VISIBLE : View.GONE);
        if (clip != null) {
            pasteText.setText(clip.size() + (clip.size() == 1 ? " Element " : " Elemente ") + (clipMove ? "verschieben" : "kopieren"));
        }
        newFolder.setVisibility(mode == FOLDER && !searching ? View.VISIBLE : View.GONE);
        boolean showStorage = hasCard() && (mode == CATEGORY || (mode == HOME && searching));
        storageRow.setVisibility(showStorage ? View.VISIBLE : View.GONE);
        storageLine.setVisibility(showStorage ? View.VISIBLE : View.GONE);
        if (showStorage) {
            buildStorageRow();
        }
        boolean chips = mode == CATEGORY && cat == 3;
        docChipScroll.setVisibility(chips ? View.VISIBLE : View.GONE);
        if (chips) {
            buildDocChips();
        }
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
        crumbScroll.post(new Runnable() {
            @Override
            public void run() {
                crumbScroll.scrollTo(crumbs.getWidth(), 0);
            }
        });
    }

    private TextView crumb(String text, final File target, boolean last) {
        TextView t = Ui.label(c, text, 14, last ? Ui.primary(c) : Ui.dim(c), last);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14));
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

    // ---- Kacheln und Zeilen ------------------------------------------------------------------

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

    /** Symbol des Eintrags: Ordner und Dateien einfarbig; Fotos und Videos zeigen ihr Vorschaubild, Apps ihr Symbol. */
    private ImageView iconOf(Entry e) {
        final ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        if (e.pkg != null) {
            try {
                v.setImageDrawable(c.getPackageManager().getApplicationIcon(e.pkg));
                return v;
            } catch (Exception ignored) {
            }
        }
        if (e.dir) {
            v.setImageDrawable(Ui.folderIcon(c));
            return v;
        }
        String x = Ui.ext(e.file.getName());
        v.setImageDrawable(Ui.typeIcon(c, x));
        String kind = isImage(x) ? "image" : isVideo(x) ? "video" : isAudio(x) ? "audio" : x.equals("pdf") ? "pdf"
                : x.equals("apk") ? "apk" : null;
        if (kind != null) {
            v.setPadding(0, 0, 0, 0);
            v.setBackground(Ui.round(c, 0x00000000, 4));
            v.setClipToOutline(true);
            v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            Thumbs.loadFile(c, e.file, kind, 168, v, new Runnable() {
                @Override
                public void run() {
                    // kein Vorschaubild (zum Beispiel Audio ohne Albumbild): das Symbol mit Rand zeigen
                    v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    v.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
                }
            });
        }
        return v;
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
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        return t;
    }

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
        TextView s = Ui.label(c, sub, 11, subColor, false);
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
        boolean selecting = !selected.isEmpty();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.listRowBg(c, picked));
        row.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        row.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.dp(c, 60)));
        if (selecting) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
            cp.rightMargin = Ui.dp(c, 14);
            row.addView(checkbox(picked), cp);
        }
        row.addView(iconOf(e), new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 4), 0);
        TextView n = Ui.label(c, nameOf(e), 15, Ui.text(c), false);
        n.setMaxLines(1);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        String sub = e.dir ? subOf(e)
                : subOf(e) + " · " + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(new Date(e.modified));
        TextView s = Ui.label(c, sub, 12, Ui.dim(c), false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.setPadding(0, Ui.dp(c, 1), 0, 0);
        text.addView(s);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (e.pkg == null && !selecting) {
            ImageView more = Ui.icon(c, R.drawable.ic_more, Ui.dim(c), 24);
            more.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            more.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            more.setContentDescription("Funktionen für " + e.file.getName());
            more.setBackground(Ui.pressable(c));
            final Entry en = e;
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rowMenu(en);
                }
            });
            row.addView(more);
        }
        return row;
    }
}
