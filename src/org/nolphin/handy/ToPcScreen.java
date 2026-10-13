package org.nolphin.handy;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.content.Intent;
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
    private static final class Entry implements SortOrder.Sortable {
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

        @Override
        public String sortName() {
            return file.getName();
        }

        @Override
        public boolean sortIsDir() {
            return dir;
        }

        @Override
        public long sortSize() {
            return size;
        }

        @Override
        public long sortModified() {
            return modified;
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
    private final ImageView searchBtn;
    private final ImageView menuBtn;
    private final ImageView dualBtn;
    private boolean searchOpen;
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
    private final LinearLayout pasteBar;
    private final TextView pasteText;
    /** Endungen, die unter „Dokumente“ erscheinen (keine Programm- und Systemdateien anderer Apps). */
    private static final String[] DOC_EXT = { "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv",
            "ppt", "pptx", "odp", "epub", "zip", "rar", "7z", "step", "stp", "stl", "obj", "3mf", "dxf", "dwg", "iges", "igs" };
    private static final String[] DOC_FILTERS = { "Alle", "DOC", "XLS", "PDF", "PPT", "Sonstige" };
    private final boolean compact;
    private final String paneId;
    private DualSwitch dualSwitch;
    private Partner partner;
    private int lastNotified;
    private final TextView paneTag;
    private final View activeBar;
    private final LinearLayout searchRow;
    private final View searchLine;
    private final View crumbLine;
    private View selCopy;
    private View selMove;
    private int docFilter;
    private SortOrder order = SortOrder.standard();
    private boolean orderChosen;
    private final TextView sortHint;
    private final View sortHintLine;
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
    private int viewMode;
    private boolean largePreview;
    private boolean hideNames;
    private boolean showHidden;
    private boolean loading;
    private String error;
    private boolean access;
    private boolean accessKnown;
    private int gen;                      // verwirft Ergebnisse veralteter Ladevorgänge

    private final List<Vol> vols = new ArrayList<>();
    private final String[] catSub = { "", "", "", "" };
    private String downloadsSub = "";
    private String appsSub = "";
    private String trashSub = "";
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
            return viewMode == Conn.VIEW_GRID ? entryTile(e) : entryRow(e);
        }
    };

    /** Umschalter zwischen einem und zwei Fenstern (vom Reiter "Zum PC" bereitgestellt). */
    interface DualSwitch {
        void setDual(boolean on);
    }

    /** Das gegenüberliegende Fenster im Zwei-Fenster-Modus wird über Änderungen informiert. */
    interface Partner {
        void selectionChanged(ToPcScreen s);

        void opFinished(ToPcScreen s);
    }

    ToPcScreen(Context c, Host host) {
        this(c, host, false, null);
    }

    /** @param compact kleines Fenster für den Zwei-Fenster-Modus (ohne Suche, Pfadleiste und Sortierhinweis) */
    ToPcScreen(Context c, Host host, boolean compact, String paneId) {
        this.compact = compact;
        this.paneId = paneId;
        this.c = c;
        orderChosen = Conn.hasSortOrder(c, settingPane());
        if (orderChosen) {
            order = Conn.sortOrder(c, settingPane());
        }
        this.host = host;
        viewMode = Conn.viewMode(c, settingPane());
        largePreview = Conn.largePreview(c, settingPane());
        hideNames = Conn.hideNames(c, settingPane());
        showHidden = Conn.showHidden(c, settingPane());
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int p = Ui.dp(c, 16);

        activeBar = new View(c);
        activeBar.setBackgroundColor(0x00000000);
        if (compact) {
            root.addView(activeBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 3)));
        }

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
        paneTag = Ui.label(c, "", 12, Ui.dim(c), true);
        paneTag.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
        paneTag.setBackground(Ui.outlined(c, Ui.surface(c)));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tagLp.leftMargin = Ui.dp(c, 4);
        tagLp.rightMargin = Ui.dp(c, 4);
        if (compact) {
            head.addView(paneTag, tagLp);
        }
        head.addView(back);
        title = Ui.label(c, "Zum PC", 18, Ui.text(c), true);
        title.setSingleLine(true);
        title.setEllipsize(compact ? android.text.TextUtils.TruncateAt.START : android.text.TextUtils.TruncateAt.END);
        if (compact) {
            title.setTextSize(14);
        }
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        searchBtn = headButton(R.drawable.ic_search, "Suchen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSearch();
            }
        });
        menuBtn = headButton(R.drawable.ic_menu, "Menü", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });
        dualBtn = headButton(R.drawable.ic_panes, "Zwei Fenster", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDual();
            }
        });
        dualBtn.setVisibility(View.GONE);
        head.addView(dualBtn);
        head.addView(searchBtn);
        if (compact) {
            head.addView(menuBtn);   // im Zwei-Fenster-Modus hat jedes Fenster sein eigenes Menü
        }
        root.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, compact ? 40 : 44)));
        root.addView(divider());

        searchRow = new LinearLayout(c);
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
                if (search.length() > 0) {
                    search.setText("");   // erst leeren, ein zweites Tippen schließt die Suche
                } else {
                    toggleSearch();
                }
            }
        });
        searchRow.addView(clear);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 44));
        root.addView(searchRow, slp);
        searchLine = divider();
        root.addView(searchLine);

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
        crumbLine = divider();
        root.addView(crumbLine);

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

        sortHint = Ui.label(c, "", 13, Ui.dim(c), false);
        sortHint.setBackgroundColor(Ui.bg(c));
        sortHint.setGravity(Gravity.CENTER_VERTICAL);
        sortHint.setPadding(p, 0, p, 0);
        sortHint.setVisibility(View.GONE);
        root.addView(sortHint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 36)));
        sortHintLine = divider();
        sortHintLine.setVisibility(View.GONE);
        root.addView(sortHintLine);

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
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, compact ? 44 : 52)));
        for (int i = 0; i < selectBar.getChildCount(); i++) {
            CharSequence d = selectBar.getChildAt(i).getContentDescription();
            if ("Kopieren".contentEquals(d == null ? "" : d)) {
                selCopy = selectBar.getChildAt(i);
            } else if ("Verschieben".contentEquals(d == null ? "" : d)) {
                selMove = selectBar.getChildAt(i);
            }
        }

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

        if (compact && paneId != null) {
            String saved = Conn.paneDir(c, paneId);
            if (saved != null && new File(saved).isDirectory()) {
                mode = FOLDER;
                dir = new File(saved);
            }
        }
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

    /** Schlüssel der Fenstereinstellungen: null im einzelnen Fenster, sonst die Nummer des kleinen Fensters. */
    private String settingPane() {
        return compact ? paneId : null;
    }

    private boolean bigPreview() {
        return largePreview;
    }

    private void applyMode() {
        boolean tiles = viewMode == Conn.VIEW_GRID;
        if (tiles) {
            grid.setColumnWidth(Ui.dp(c, ViewModes.tileWidthDp(bigPreview())));
            grid.setNumColumns(GridView.AUTO_FIT);
        } else {
            grid.setNumColumns(1);
        }
        // Kacheln dicht an dicht ohne Rahmen; die Liste als durchgehende Fläche
        grid.setVerticalSpacing(tiles ? Ui.dp(c, 4) : 0);
        grid.setHorizontalSpacing(tiles ? Ui.dp(c, 4) : 0);
        grid.setPadding(tiles ? Ui.dp(c, 6) : 0, Ui.dp(c, 4), tiles ? Ui.dp(c, 6) : 0, Ui.dp(c, 10));
        adapter.notifyDataSetChanged();
    }

    // ---- Übersicht ---------------------------------------------------------------------------

    /** Übersicht für die kleinen Fenster: nur einfache Zeilen, damit Speicher und Downloads sofort erreichbar sind. */
    private void buildHomeCompact() {
        LinearLayout places = Ui.group(c);
        for (final Vol v : vols) {
            String sub = "";
            try {
                StatFs sf = new StatFs(v.root.getPath());
                long total = sf.getTotalBytes();
                long used = total - sf.getAvailableBytes();
                sub = total > 0 ? Ui.size(used) + " von " + Ui.size(total) + " belegt" : "";
            } catch (RuntimeException ignored) {
            }
            Ui.addRow(c, places, Ui.row(c, v.card ? R.drawable.ic_sd : R.drawable.ic_storage, Ui.iconTint(c), v.name, sub, null,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View x) {
                            openFolder(v.root);
                        }
                    }));
        }
        Ui.addRow(c, places, Ui.row(c, R.drawable.ic_download, Ui.iconTint(c), "Downloads", downloadsSub, null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        openFolder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                    }
                }));
        Ui.addRow(c, places, Ui.row(c, R.drawable.ic_trash, Ui.iconTint(c), "Papierkorb", trashSub, null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        openTrash();
                    }
                }));
        homeBox.addView(places);
        List<String> favs = Conn.favorites(c);
        if (!favs.isEmpty()) {
            homeBox.addView(heading("Favoriten"));
            LinearLayout fg = Ui.group(c);
            for (String path : favs) {
                Ui.addRow(c, fg, placeRow(path, true));
            }
            homeBox.addView(fg);
        }
        List<String> recent = new ArrayList<>();
        for (String path : Conn.recents(c)) {
            if (new File(path).isDirectory() && recent.size() < 3) {
                recent.add(path);
            }
        }
        if (!recent.isEmpty()) {
            homeBox.addView(heading("Zuletzt geöffnet"));
            LinearLayout rg = Ui.group(c);
            for (String path : recent) {
                Ui.addRow(c, rg, placeRow(path, false));
            }
            homeBox.addView(rg);
        }
        homeBox.addView(heading("Kategorien"));
        LinearLayout cats = Ui.group(c);
        for (int k = 0; k < CATS.length; k++) {
            Ui.addRow(c, cats, category(k));
        }
        homeBox.addView(cats);
    }

    private void buildHome() {
        homeBox.removeAllViews();
        if (compact) {
            buildHomeCompact();
            return;
        }
        List<String> favs = Conn.favorites(c);
        if (!favs.isEmpty()) {
            homeBox.addView(heading("Favoriten"));
            LinearLayout fg = Ui.group(c);
            for (String path : favs) {
                Ui.addRow(c, fg, placeRow(path, true));
            }
            homeBox.addView(fg);
        }
        List<String> recent = new ArrayList<>();
        for (String path : Conn.recents(c)) {
            if (new File(path).isDirectory() && recent.size() < 5) {
                recent.add(path);
            }
        }
        if (!recent.isEmpty()) {
            homeBox.addView(heading("Zuletzt geöffnet"));
            LinearLayout rg = Ui.group(c);
            for (String path : recent) {
                Ui.addRow(c, rg, placeRow(path, false));
            }
            homeBox.addView(rg);
        }
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
        Ui.addRow(c, quick, Ui.row(c, R.drawable.ic_trash, Ui.iconTint(c), "Papierkorb", trashSub, null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openTrash();
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
                "Fotos und Videos aus der Cloud", null, new View.OnClickListener() {
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
                openFolder(v.root);   // direkt in den Speicher; die Analyse gibt es nur auf Wunsch im Menü
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
                List<File> rts = new ArrayList<>();
                for (Vol vv : new ArrayList<>(vols)) {
                    rts.add(vv.root);
                }
                TrashOps.purge(rts, TrashOps.MAX_AGE_MS, System.currentTimeMillis());
                long[] ts = TrashOps.summary(rts);
                final String trash = ts[0] == 0 ? "leer" : ts[0] + (ts[0] == 1 ? " Element · " : " Elemente · ") + Ui.size(ts[1]);
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        System.arraycopy(sub, 0, catSub, 0, sub.length);
                        downloadsSub = dls;
                        appsSub = apps;
                        trashSub = trash;
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

    /** Gewählte Sortierung; solange nichts gewählt ist, kommen die neuesten Dateien zuerst (Ordner davor). */
    private SortOrder effectiveOrder() {
        return orderChosen ? order : SortOrder.standard();
    }

    private void sortEntries(List<Entry> list) {
        if (mode == APPS) {
            return;
        }
        Collections.sort(list, effectiveOrder().comparator());
    }

    private TextView smallHeading(String text) {
        TextView t = Ui.label(c, text, 12, Ui.dim(c), true);
        t.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2));
        return t;
    }

    private android.widget.RadioButton radio(String text, int id) {
        android.widget.RadioButton rb = new android.widget.RadioButton(c);
        rb.setId(id);
        rb.setText(text);
        rb.setTextSize(15);
        rb.setTextColor(Ui.text(c));
        rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
        rb.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), 0, Ui.dp(c, 6));
        return rb;
    }

    private android.widget.CheckBox check(String text, boolean on) {
        android.widget.CheckBox cb = new android.widget.CheckBox(c);
        cb.setText(text);
        cb.setTextSize(15);
        cb.setTextColor(Ui.text(c));
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
        cb.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), 0, Ui.dp(c, 6));
        cb.setChecked(on);
        return cb;
    }

    /** Zeile mit Text links und Kästchen rechts; die ganze Zeile ist antippbar. */
    private View checkRow(String text, boolean on, android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4));
        row.setBackground(Ui.pressable(c));
        TextView t = Ui.label(c, text, 16, Ui.text(c), false);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final android.widget.CheckBox cb = new android.widget.CheckBox(c);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
        cb.setChecked(on);
        cb.setOnCheckedChangeListener(l);
        row.addView(cb);
        row.setMinimumHeight(Ui.dp(c, 52));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                cb.toggle();
            }
        });
        return row;
    }

    private static String sortLabel(SortOrder o) {
        return o.key == SortOrder.NONE ? SortOrder.LABELS[SortOrder.NONE] : SortOrder.LABELS[o.key] + (o.ascending ? " ▲" : " ▼");
    }

    private interface Pick {
        void on(int index);
    }

    private TextView sheetHeading(String text) {
        TextView t = Ui.label(c, text, 14, Ui.primary(c), true);
        t.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 4));
        return t;
    }

    /** Eine Reihe aus Auswahlknöpfen mit Symbol (genau einer ist gewählt). */
    private LinearLayout iconChoices(int[] icons, final String[] descriptions, int selected, final Pick pick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.RadioButton[] rbs = new android.widget.RadioButton[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(c);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(Ui.dp(c, 16), 0, 0, 0);
            cell.setBackground(Ui.pressable(c));
            cell.setContentDescription(descriptions[i]);
            android.widget.RadioButton rb = new android.widget.RadioButton(c);
            rb.setClickable(false);
            rb.setChecked(i == selected);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
            rbs[i] = rb;
            cell.addView(rb);
            ImageView icon = Ui.icon(c, icons[i], Ui.text(c), 26);
            cell.addView(icon);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < rbs.length; j++) {
                        rbs[j].setChecked(j == idx);
                    }
                    pick.on(idx);
                }
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(c, 56), 1));
        }
        return row;
    }

    private void saveOrder(SortOrder o) {
        order = o;
        orderChosen = true;
        Conn.setSortOrder(c, order, settingPane());
        sortEntries(entries);
        render();
    }

    /** Auswahl der Sortierung (neun Möglichkeiten) in einer Liste; die Wahl gilt sofort. */
    private void showSortChoice(final TextView label) {
        final SortOrder now = effectiveOrder();
        String[] names = new String[ViewModes.SORT_CHOICES];
        names[0] = SortOrder.LABELS[SortOrder.NONE];
        for (int k = 0; k < SortOrder.NONE; k++) {
            names[1 + k * 2] = SortOrder.LABELS[k] + " ▲";
            names[2 + k * 2] = SortOrder.LABELS[k] + " ▼";
        }
        int checked = ViewModes.sortIndex(now);
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle("Sortieren nach")
                .setSingleChoiceItems(names, checked, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        saveOrder(ViewModes.sortFromIndex(which, now.foldersFirst));
                        label.setText(sortLabel(effectiveOrder()));
                        d.dismiss();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    /**
     * Blatt von unten: Ansicht (Liste, Raster, Kompakt), Vorschaugröße, Sortierung und Sonstiges. Jede Änderung gilt sofort
     * und bleibt gespeichert; Darstellung und Vorschaugröße sind voneinander unabhängig.
     */
    private void showViewDialog() {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(0, 0, 0, Ui.dp(c, 12));
        v.addView(sheetHeading("Ansicht"));
        v.addView(iconChoices(new int[] { R.drawable.ic_list, R.drawable.ic_grid, R.drawable.ic_compact },
                new String[] { "Liste", "Raster", "Kompakt" }, viewMode, new Pick() {
                    @Override
                    public void on(int i) {
                        viewMode = i;
                        Conn.setViewMode(c, viewMode, settingPane());
                        applyMode();
                        render();
                    }
                }));
        v.addView(iconChoices(new int[] { R.drawable.ic_photo_small, R.drawable.ic_photo_large },
                new String[] { "Standard-Vorschau", "Große Vorschau" }, largePreview ? 1 : 0, new Pick() {
                    @Override
                    public void on(int i) {
                        largePreview = i == 1;
                        Conn.setLargePreview(c, largePreview, settingPane());
                        applyMode();
                        render();
                    }
                }));
        v.addView(sheetHeading("Sortieren"));
        final TextView sort = Ui.label(c, sortLabel(effectiveOrder()), 17, Ui.text(c), false);
        sort.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12));
        sort.setBackground(Ui.pressable(c));
        sort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                showSortChoice(sort);
            }
        });
        v.addView(sort);
        v.addView(sheetHeading("Sonstiges"));
        v.addView(checkRow("Ordner zuerst", effectiveOrder().foldersFirst, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                SortOrder o = effectiveOrder();
                saveOrder(new SortOrder(o.key, o.ascending, on));
            }
        }));
        v.addView(checkRow("Dateinamen ausblenden (nur Raster)", hideNames, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                hideNames = on;
                Conn.setHideNames(c, on, settingPane());
                adapter.notifyDataSetChanged();
            }
        }));
        v.addView(checkRow("Ausgeblendete Dateien anzeigen", showHidden, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                showHidden = on;
                Conn.setShowHidden(c, on, settingPane());
                loadList();
            }
        }));
        TextView reset = Ui.label(c, "Auf Standard zurücksetzen", 14, Ui.primary(c), true);
        reset.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 8));
        final android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(v);
        final AlertDialog dlg = new AlertDialog.Builder(host.activity(), R.style.NolphinDialog).setView(sv).create();
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                Conn.resetView(c, settingPane());
                orderChosen = false;
                order = SortOrder.standard();
                viewMode = Conn.VIEW_LIST;
                largePreview = false;
                hideNames = false;
                showHidden = false;
                dlg.dismiss();
                applyMode();
                loadList();
            }
        });
        v.addView(reset);
        dlg.show();
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Ui.surface(c));
            float r = Ui.dp(c, 16);
            bg.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
            w.setBackgroundDrawable(bg);
        }
    }

    // ---- Menü und Suche -----------------------------------------------------------------------

    private ImageView headButton(int res, String description, View.OnClickListener l) {
        ImageView b = Ui.icon(c, res, Ui.iconTint(c), 24);
        b.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        b.setContentDescription(description);
        b.setBackground(Ui.pressable(c));
        b.setOnClickListener(l);
        return b;
    }

    private final List<String> menuNames = new ArrayList<>();
    private final List<Runnable> menuRuns = new ArrayList<>();

    private void menuItem(String name, Runnable run) {
        menuNames.add(name);
        menuRuns.add(run);
    }

    /** Einträge für das zentrale Menü; hängen vom aktuellen Ort ab. */
    @Override
    public String[] menuLabels() {
        menuNames.clear();
        menuRuns.clear();
        if (access) {
            if (!compact) {
                menuItem("Suchen", new Runnable() {
                    @Override
                    public void run() {
                        toggleSearch();
                    }
                });
            }
            if (mode == FOLDER) {
                menuItem("Neuer Ordner", new Runnable() {
                    @Override
                    public void run() {
                        askNewFolder();
                    }
                });
            }
            if (mode == FOLDER || mode == CATEGORY) {
                menuItem("Ansicht und Sortierung", new Runnable() {
                    @Override
                    public void run() {
                        showViewDialog();
                    }
                });
            }
            menuItem("Speicheranalyse", new Runnable() {
                @Override
                public void run() {
                    openAnalysis();
                }
            });
            if (!compact && dualSwitch != null) {
                menuItem("Zwei Fenster", new Runnable() {
                    @Override
                    public void run() {
                        openDual();
                    }
                });
            }
        }
        return menuNames.toArray(new String[0]);
    }

    @Override
    public void menuPicked(int index) {
        if (index >= 0 && index < menuRuns.size()) {
            menuRuns.get(index).run();
        }
    }

    private void showMenu(View anchor) {
        Ui.popupMenu(c, anchor, menuLabels(), new Ui.MenuPick() {
            @Override
            public void on(int index) {
                menuPicked(index);
            }
        });
    }

    private static final int[] CAT_TO_SCAN = { StorageScan.PICTURES, StorageScan.AUDIO, StorageScan.VIDEOS, StorageScan.DOCUMENTS };

    private void runAnalysis(String name, File root, int focus, boolean wholeVolume) {
        StorageAnalysis.show(host.activity(), name, root, new StorageAnalysis.Opener() {
            @Override
            public void openFolder(File dir) {
                ToPcScreen.this.openFolder(dir);
            }
        }, focus, wholeVolume);
    }

    /**
     * Speicheranalyse nur auf Wunsch und für den Bereich, in dem man gerade ist: im Ordner dieser Ordner, in einer Gruppe
     * (Bilder, Audio, Videos, Dokumente) der gewählte Speicher mit dieser Gruppe im Mittelpunkt, sonst ein Speicher zur Wahl.
     */
    private void openAnalysis() {
        if (mode == FOLDER && dir != null) {
            boolean isRoot = false;
            for (Vol v : vols) {
                isRoot |= v.root.equals(dir);
            }
            runAnalysis(folderTitle(), dir, -1, isRoot);
            return;
        }
        final int focus = mode == CATEGORY && cat >= 0 && cat < CAT_TO_SCAN.length ? CAT_TO_SCAN[cat] : -1;
        if (mode == CATEGORY && storage != 0) {
            for (Vol v : vols) {
                if (v.card == (storage == 2)) {
                    runAnalysis(v.name, v.root, focus, true);
                    return;
                }
            }
        }
        if (vols.size() == 1) {
            runAnalysis(vols.get(0).name, vols.get(0).root, focus, true);
            return;
        }
        String[] names = new String[vols.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = vols.get(i).name;
        }
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle("Speicheranalyse für …")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        runAnalysis(vols.get(which).name, vols.get(which).root, focus, true);
                    }
                })
                .show();
    }

    /** Zwei Fenster öffnen; ist der Modus noch nicht angeboten, wird er dabei eingeschaltet (Einstellungen: „Zwei-Fenster-Modus anbieten“). */
    private void openDual() {
        if (dualSwitch == null) {
            return;
        }
        if (!Conn.dualAvailable(c)) {
            Conn.setDualAvailable(c, true);
        }
        dualSwitch.setDual(true);
    }

    private void toggleSearch() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (searchOpen || !query.isEmpty()) {
            searchOpen = false;
            search.setText("");
            if (imm != null) {
                imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
            }
            render();
            return;
        }
        searchOpen = true;
        render();
        search.requestFocus();
        if (imm != null) {
            imm.showSoftInput(search, 0);
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
        Conn.pushRecent(c, f.getPath());
        if (compact && paneId != null) {
            Conn.setPaneDir(c, paneId, f.getPath());
        }
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
        homeScroll.scrollTo(0, 0);
        if (compact && paneId != null) {
            Conn.setPaneDir(c, paneId, null);
        }
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

    /** Versteckte Dateien (Punkt am Anfang) nur auf Wunsch; der Papierkorb der App bleibt immer verborgen. */
    private boolean visibleName(String name) {
        if (name.equals(TrashOps.DIR_NAME)) {
            return false;
        }
        return showHidden || !name.startsWith(".");
    }

    private void loadList() {
        grid.setSelection(0);   // ein neuer Ordner beginnt oben, nicht an der alten Scrollstelle
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
                            if (visibleName(f.getName())) {
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
                sortEntries(entries);
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
            sortEntries(entries);
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
                        if (!visibleName(n)) {
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
                        sortEntries(entries);
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
        List<String> paths = new ArrayList<>(selected.keySet());
        selected.clear();
        render();
        // Ordner schickt die App Datei für Datei mit Unterordnern (bei älterem Nolphin am PC als ZIP)
        host.sendPaths(paths);
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
        beginTransfer(items, target, move);
    }

    /** Erst prüfen, ob es im Ziel schon gleiche Namen gibt, dann (nach der Konfliktfrage) übertragen. */
    private void beginTransfer(final List<File> items, final File target, final boolean move) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> conflicts = FileOps.conflicts(items, target);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (conflicts.isEmpty()) {
                            startTransfer(items, target, move, FileOps.Conflict.RENAME);
                        } else {
                            askConflict(target, conflicts, new ConflictChoice() {
                                @Override
                                public void chosen(FileOps.Conflict conflict) {
                                    startTransfer(items, target, move, conflict);
                                }
                            });
                        }
                    }
                });
            }
        }, "nolphin-conflicts").start();
    }

    private void startTransfer(final List<File> items, final File target, final boolean move, final FileOps.Conflict conflict) {
        runBatch(move ? "Verschiebe …" : "Kopiere …", move ? "verschoben" : "kopiert", new BatchWork() {
            @Override
            public FileOps.Result run(FileOps.Progress p) {
                return move ? FileOps.moveAll(items, target, conflict, p) : FileOps.copyAll(items, target, conflict, p);
            }
        }, new Retry() {
            @Override
            public void run(List<File> failedItems) {
                // nur das Fehlgeschlagene noch einmal; schon Erledigtes im Ziel bleibt unverändert
                startTransfer(failedItems, target, move, FileOps.Conflict.SKIP);
            }
        });
    }

    private CharSequence twoLines(String title, String sub) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(title + "\n" + sub);
        b.setSpan(new android.text.style.RelativeSizeSpan(0.82f), title.length() + 1, b.length(), 0);
        b.setSpan(new android.text.style.ForegroundColorSpan(Ui.dim(c)), title.length() + 1, b.length(), 0);
        return b;
    }

    /** Einmal pro Vorgang fragen: beide behalten, überspringen oder ersetzen. */
    /** Wird mit der gewählten Regel aufgerufen, wenn die Konfliktfrage beantwortet ist. */
    private interface ConflictChoice {
        void chosen(FileOps.Conflict conflict);
    }

    /** Einmal pro Vorgang fragen: beide behalten, überspringen oder ersetzen. */
    private void askConflict(final File target, final List<String> conflicts, final ConflictChoice cb) {
        File example = new File(target, conflicts.get(0));
        String renamed = FileOps.uniqueIn(example.getParentFile(), example.getName()).getName();
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), 0);
        TextView msg = Ui.label(c, conflicts.size() + (conflicts.size() == 1 ? " Element gibt es" : " Elemente gibt es")
                + " im Ziel schon, zum Beispiel „" + example.getName() + "“.", 14, Ui.dim(c), false);
        msg.setPadding(0, 0, 0, Ui.dp(c, 8));
        v.addView(msg);
        final android.widget.RadioGroup group = new android.widget.RadioGroup(c);
        CharSequence[] labels = {
            twoLines("Beide behalten", "Die neue Datei heißt „" + renamed + "“"),
            twoLines("Überspringen", "Vorhandene Dateien bleiben unberührt"),
            twoLines("Ersetzen", "Vorhandene Dateien werden überschrieben")
        };
        for (int i = 0; i < labels.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(c);
            rb.setId(i + 1);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            rb.setTextColor(Ui.text(c));
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
            rb.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), 0, Ui.dp(c, 8));
            group.addView(rb);
        }
        group.check(1);
        v.addView(group);
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle("Es gibt schon Dateien mit diesem Namen")
                .setView(v)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Weiter", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        int id = group.getCheckedRadioButtonId();
                        if (id == 3) {
                            confirmReplace(target, conflicts, cb);
                        } else {
                            cb.chosen(id == 2 ? FileOps.Conflict.SKIP : FileOps.Conflict.RENAME);
                        }
                    }
                }).show();
    }

    /** Ersetzen verlangt eine ausdrückliche Bestätigung mit der Liste der betroffenen Dateien. */
    private void confirmReplace(final File target, final List<String> conflicts, final ConflictChoice cb) {
        StringBuilder b = new StringBuilder("Diese Dateien im Ziel werden überschrieben. Das lässt sich nicht rückgängig machen.\n\n");
        int shown = Math.min(conflicts.size(), 10);
        for (int i = 0; i < shown; i++) {
            b.append("• ").append(conflicts.get(i)).append("\n");
        }
        if (conflicts.size() > shown) {
            b.append("… und ").append(conflicts.size() - shown).append(" weitere");
        }
        AlertDialog d = new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle(conflicts.size() + (conflicts.size() == 1 ? " Datei ersetzen?" : " Dateien ersetzen?"))
                .setMessage(b.toString().trim())
                .setNegativeButton("Zurück", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        askConflict(target, conflicts, cb);
                    }
                })
                .setPositiveButton("Ersetzen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        cb.chosen(FileOps.Conflict.REPLACE);
                    }
                }).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.danger(c));
    }

    private List<File> volRoots() {
        List<File> l = new ArrayList<>();
        for (Vol v : vols) {
            l.add(v.root);
        }
        return l;
    }

    private void askDelete() {
        if (selected.isEmpty()) {
            return;
        }
        final List<File> items = selectedFiles();
        String what = items.size() == 1 ? "„" + items.get(0).getName() + "“" : items.size() + " Elemente";
        if (TrashOps.canTrashAll(items, volRoots())) {
            new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                    .setTitle("In den Papierkorb?")
                    .setMessage(what + (items.size() == 1 ? " wird" : " werden")
                            + " in den Papierkorb verschoben. Dort bleibt " + (items.size() == 1 ? "es" : "alles")
                            + " 30 Tage und lässt sich wiederherstellen.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("In den Papierkorb", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int which) {
                            selected.clear();
                            render();
                            startTrash(items);
                        }
                    }).show();
            return;
        }
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle("Löschen?")
                .setMessage(what + " wird endgültig gelöscht. Das lässt sich nicht rückgängig machen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        selected.clear();
                        render();
                        startDelete(items);
                    }
                }).show();
    }

    private void startTrash(final List<File> items) {
        final List<File> roots = volRoots();
        runBatch("Lege in den Papierkorb …", "in den Papierkorb gelegt", new BatchWork() {
            @Override
            public FileOps.Result run(FileOps.Progress p) {
                return TrashOps.moveToTrash(items, roots, p);
            }
        }, new Retry() {
            @Override
            public void run(List<File> failedItems) {
                startTrash(failedItems);
            }
        });
    }

    private void openTrash() {
        TrashScreen.show(host.activity(), volRoots(), new TrashScreen.Changed() {
            @Override
            public void changed(List<File> touched) {
                for (File f : touched) {
                    scan(f);
                }
                reload();
                if (partner != null) {
                    partner.opFinished(ToPcScreen.this);
                }
                loadHomeInfo();
            }
        });
    }

    private void startDelete(final List<File> items) {
        runBatch("Lösche …", "gelöscht", new BatchWork() {
            @Override
            public FileOps.Result run(FileOps.Progress p) {
                return FileOps.deleteAll(items, p);
            }
        }, new Retry() {
            @Override
            public void run(List<File> failedItems) {
                startDelete(failedItems);
            }
        });
    }

    private void more() {
        if (selected.isEmpty()) {
            return;
        }
        final List<File> items = selectedFiles();
        final File one = items.size() == 1 ? items.get(0) : null;
        final List<String> labels = new ArrayList<>();
        labels.add("Alle auswählen");
        labels.add(one != null ? "Umbenennen" : "Umbenennen …");
        if (one != null) {
            labels.add("Eigenschaften");
            if (one.isFile()) {
                labels.add("Öffnen mit …");
                labels.add("Bearbeiten mit …");
            }
        }
        labels.add("Als ZIP packen");
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle(one != null ? one.getName() : items.size() + " Elemente gewählt")
                .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String what = labels.get(which);
                        if (what.equals("Alle auswählen")) {
                            selectAll();
                        } else if (what.equals("Umbenennen")) {
                            askRename(one);
                        } else if (what.equals("Umbenennen …")) {
                            askBatchRename(items);
                        } else if (what.equals("Eigenschaften")) {
                            showProperties(one);
                        } else if (what.equals("Öffnen mit …") || what.equals("Bearbeiten mit …")) {
                            selected.clear();
                            render();
                            openWith(one, what.startsWith("Bearbeiten"));
                        } else {
                            selected.clear();
                            render();
                            startZip(items);
                        }
                    }
                }).show();
    }

    // ---- ZIP und Stapelumbenennung ---------------------------------------------------------------

    private void startZip(final List<File> items) {
        final File outDir = items.get(0).getParentFile();
        final String name = items.size() == 1 ? items.get(0).getName() + ".zip" : "Archiv.zip";
        runBatch("Packe …", "gepackt", new BatchWork() {
            @Override
            public FileOps.Result run(FileOps.Progress p) {
                return ZipOps.zipItems(items, outDir, name, p);
            }
        }, null);
    }

    private void showZipContents(final File zip) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ZipOps.Info info = null;
                String err = null;
                try {
                    info = ZipOps.inspect(zip);
                } catch (java.io.IOException e) {
                    err = e.getMessage() != null ? e.getMessage() : "Archiv nicht lesbar";
                }
                final ZipOps.Info fi = info;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fi == null) {
                            Toast.makeText(c, zip.getName() + ": " + fe, Toast.LENGTH_LONG).show();
                            return;
                        }
                        String head = fi.files + (fi.files == 1 ? " Datei" : " Dateien") + " in " + fi.folders
                                + (fi.folders == 1 ? " Ordner" : " Ordnern") + ", entpackt " + ZipOps.sizeText(fi.uncompressed);
                        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                                .setTitle(zip.getName())
                                .setMessage(head + "\n\n" + joinLimited(fi.names, 40)
                                        + (fi.unsafe.isEmpty() ? "" : "\n\nWürde nicht entpackt (unsicher): " + fi.unsafe.size()))
                                .setPositiveButton("Schließen", null)
                                .show();
                    }
                });
            }
        }, "nolphin-ziplist").start();
    }

    private static String joinLimited(List<String> list, int max) {
        StringBuilder b = new StringBuilder();
        int shown = Math.min(list.size(), max);
        for (int i = 0; i < shown; i++) {
            b.append(list.get(i)).append("\n");
        }
        if (list.size() > shown) {
            b.append("… und ").append(list.size() - shown).append(" weitere");
        }
        return b.toString().trim();
    }

    /** Entpacken: erst prüfen (Inhalt, unsichere Einträge, Speicherplatz), dann bestätigen lassen. */
    private void askUnzip(final File zip) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ZipOps.Info info = null;
                String err = null;
                try {
                    info = ZipOps.inspect(zip);
                } catch (java.io.IOException e) {
                    err = e.getMessage() != null ? e.getMessage() : "Archiv nicht lesbar";
                }
                final ZipOps.Info fi = info;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fi == null) {
                            Toast.makeText(c, zip.getName() + ": " + fe, Toast.LENGTH_LONG).show();
                            return;
                        }
                        confirmUnzip(zip, fi);
                    }
                });
            }
        }, "nolphin-zipinfo").start();
    }

    private void confirmUnzip(final File zip, final ZipOps.Info info) {
        final File parent = zip.getParentFile();
        String name = zip.getName();
        final String stem = name.toLowerCase(Locale.ROOT).endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
        final long free = parent.getUsableSpace();
        final long need = info.uncompressed;
        StringBuilder msg = new StringBuilder();
        msg.append("Das Archiv enthält ").append(info.files).append(info.files == 1 ? " Datei" : " Dateien").append(" in ")
                .append(info.folders).append(info.folders == 1 ? " Ordner" : " Ordnern").append(" (")
                .append(ZipOps.sizeText(info.compressed)).append(", entpackt ").append(ZipOps.sizeText(need)).append(").");
        if (info.root != null && !new File(parent, info.root).exists()) {
            msg.append("\n\nZiel: Ordner „").append(info.root).append("“ in ").append(parent.getName());
        } else {
            msg.append("\n\nZiel: Ordner „").append(stem).append("“ in ").append(parent.getName());
        }
        boolean tooBig = need > Math.max(0, free - 20L * 1024 * 1024);
        if (!info.unsafe.isEmpty()) {
            msg.append("\n\n").append(info.unsafe.size()).append(info.unsafe.size() == 1 ? " Eintrag wird" : " Einträge werden")
                    .append(" aus Sicherheitsgründen übersprungen.");
        }
        if (tooBig) {
            msg.append("\n\nNicht genug Speicherplatz: nötig ").append(ZipOps.sizeText(need)).append(", frei ")
                    .append(ZipOps.sizeText(free)).append(".");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle("„" + name + "“ entpacken?")
                .setMessage(msg.toString())
                .setNegativeButton(tooBig ? "Schließen" : "Abbrechen", null);
        if (!tooBig) {
            b.setPositiveButton("Entpacken", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int which) {
                    if (info.root != null && !new File(parent, info.root).exists()) {
                        // Das Archiv bringt seinen eigenen obersten Ordner mit: gleich hier entpacken, kein doppelter Ordner
                        startUnzip(zip, parent, FileOps.Conflict.RENAME);
                        return;
                    }
                    File dest = new File(parent, stem);
                    if (dest.exists()) {
                        List<String> conflicts = new ArrayList<>();
                        conflicts.add(stem);
                        askConflict(parent, conflicts, new ConflictChoice() {
                            @Override
                            public void chosen(FileOps.Conflict conflict) {
                                File target = conflict == FileOps.Conflict.RENAME ? FileOps.uniqueIn(parent, stem) : new File(parent, stem);
                                startUnzip(zip, target, conflict);
                            }
                        });
                    } else {
                        startUnzip(zip, dest, FileOps.Conflict.RENAME);
                    }
                }
            });
        }
        b.show();
    }

    private void startUnzip(final File zip, final File dest, final FileOps.Conflict conflict) {
        final long limit = Math.max(0, zip.getParentFile().getUsableSpace() - 20L * 1024 * 1024);
        runBatch("Entpacke …", "entpackt", new BatchWork() {
            @Override
            public FileOps.Result run(FileOps.Progress p) {
                return ZipOps.unzip(zip, dest, conflict, limit, p);
            }
        }, null);
    }

    private EditText field(String hint, String text, boolean number) {
        final EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(text);
        e.setTextSize(15);
        e.setTextColor(Ui.text(c));
        e.setHintTextColor(Ui.dim(c));
        if (number) {
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        }
        return e;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Stapelumbenennung mit Vorschau "alt → neu"; der Knopf bleibt gesperrt, solange es Probleme gibt oder nichts zu ändern ist. */
    private void askBatchRename(final List<File> picked) {
        final List<File> files = new ArrayList<>(picked);
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        final String[] tabNames = { "Ersetzen", "Nummer", "Anhängen", "Groß/Klein" };
        final BatchRename.Mode[] modes = { BatchRename.Mode.REPLACE, BatchRename.Mode.NUMBER, BatchRename.Mode.AFFIX, BatchRename.Mode.CASE };
        final int[] current = { 1 };
        final EditText find = field("Suchen", "", false);
        final EditText repl = field("Ersetzen durch", "", false);
        final EditText prefix = field("Name davor, zum Beispiel „Urlaub - “", "", false);
        final EditText start = field("Erste Nummer", "1", true);
        final EditText digits = field("Anzahl Stellen", "3", true);
        final EditText pre = field("Text vorn", "", false);
        final EditText suf = field("Text hinten (vor der Endung)", "", false);
        final android.widget.RadioGroup caseGroup = new android.widget.RadioGroup(c);
        caseGroup.addView(radio("klein schreiben", 21));
        caseGroup.addView(radio("GROSS SCHREIBEN", 22));
        caseGroup.addView(radio("Erster Buchstabe groß", 23));
        caseGroup.check(21);
        final LinearLayout fields = new LinearLayout(c);
        fields.setOrientation(LinearLayout.VERTICAL);
        final TextView preview = Ui.label(c, "", 13, Ui.text(c), false);
        preview.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        preview.setBackground(Ui.outlined(c, Ui.surface(c)));
        final TextView warn = Ui.label(c, "", 13, Ui.danger(c), false);
        warn.setPadding(0, Ui.dp(c, 6), 0, 0);
        final LinearLayout tabs = new LinearLayout(c);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        final AlertDialog[] dialog = { null };
        final BatchRename.Plan[] last = { null };

        final Runnable recalc = new Runnable() {
            @Override
            public void run() {
                BatchRename.Options o = new BatchRename.Options();
                o.mode = modes[current[0]];
                o.find = find.getText().toString();
                o.replacement = repl.getText().toString();
                o.prefix = o.mode == BatchRename.Mode.AFFIX ? pre.getText().toString() : prefix.getText().toString();
                o.suffix = suf.getText().toString();
                o.start = parseInt(start.getText().toString(), 1);
                o.digits = parseInt(digits.getText().toString(), 3);
                int cm = caseGroup.getCheckedRadioButtonId();
                o.caseMode = cm == 22 ? BatchRename.CaseMode.UPPER : cm == 23 ? BatchRename.CaseMode.FIRST : BatchRename.CaseMode.LOWER;
                BatchRename.Plan plan = BatchRename.plan(files, o);
                last[0] = plan;
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
                int shown = Math.min(plan.items.size(), 8);
                for (int i = 0; i < shown; i++) {
                    BatchRename.Item it = plan.items.get(i);
                    int from = sb.length();
                    sb.append(it.file.getName()).append("  →  ").append(it.newName);
                    if (it.problem != null) {
                        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.danger(c)), from, sb.length(), 0);
                    } else if (!it.changes()) {
                        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.dim(c)), from, sb.length(), 0);
                    }
                    sb.append("\n");
                }
                if (plan.items.size() > shown) {
                    sb.append("… und ").append(Integer.toString(plan.items.size() - shown)).append(" weitere");
                }
                preview.setText(sb);
                String problem = plan.firstProblem();
                warn.setText(problem != null ? problem : plan.changes() == 0 ? "Bisher ändert sich kein Name." : "");
                warn.setTextColor(problem != null ? Ui.danger(c) : Ui.dim(c));
                if (dialog[0] != null) {
                    dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(plan.ready());
                }
            }
        };
        final Runnable showFields = new Runnable() {
            @Override
            public void run() {
                fields.removeAllViews();
                switch (current[0]) {
                    case 0:
                        fields.addView(find);
                        fields.addView(repl);
                        break;
                    case 1:
                        fields.addView(prefix);
                        fields.addView(Ui.label(c, "Erste Nummer", 12, Ui.dim(c), false));
                        fields.addView(start);
                        fields.addView(Ui.label(c, "Anzahl Stellen (001 = 3)", 12, Ui.dim(c), false));
                        fields.addView(digits);
                        break;
                    case 2:
                        fields.addView(pre);
                        fields.addView(suf);
                        break;
                    default:
                        fields.addView(caseGroup);
                }
                tabs.removeAllViews();
                for (int i = 0; i < tabNames.length; i++) {
                    final int k = i;
                    boolean on = i == current[0];
                    TextView t = Ui.label(c, tabNames[i], 13, on ? Ui.primary(c) : Ui.dim(c), on);
                    t.setSingleLine(true);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 8));
                    LinearLayout tab = new LinearLayout(c);
                    tab.setOrientation(LinearLayout.VERTICAL);
                    tab.addView(t);
                    View bar = new View(c);
                    bar.setBackgroundColor(on ? Ui.primary(c) : 0x00000000);
                    tab.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 3)));
                    tab.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            current[0] = k;
                            showFieldsRef[0].run();
                            recalcRef[0].run();
                        }
                    });
                    tabs.addView(tab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                }
            }
        };
        showFieldsRef[0] = showFields;
        recalcRef[0] = recalc;
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int n) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                recalc.run();
            }
        };
        for (EditText e : new EditText[] { find, repl, prefix, start, digits, pre, suf }) {
            e.addTextChangedListener(watcher);
        }
        caseGroup.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.RadioGroup g, int id) {
                recalc.run();
            }
        });
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.dp(c, 24), Ui.dp(c, 4), Ui.dp(c, 24), 0);
        v.addView(tabs);
        v.addView(fields);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = Ui.dp(c, 10);
        v.addView(preview, pp);
        v.addView(warn);
        android.widget.ScrollView scroll = new android.widget.ScrollView(c);
        scroll.addView(v);
        dialog[0] = new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle(files.size() + " Dateien umbenennen")
                .setView(scroll)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Umbenennen", null)
                .create();
        dialog[0].show();
        dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View b) {
                final BatchRename.Plan plan = last[0];
                if (plan == null || !plan.ready()) {
                    return;
                }
                dialog[0].dismiss();
                selected.clear();
                render();
                runBatch("Benenne um …", "umbenannt", new BatchWork() {
                    @Override
                    public FileOps.Result run(FileOps.Progress p) {
                        return BatchRename.apply(plan, p);
                    }
                }, null);
            }
        });
        showFields.run();
        recalc.run();
    }

    private final Runnable[] showFieldsRef = new Runnable[1];
    private final Runnable[] recalcRef = new Runnable[1];

    /** Der Knopf ⋮ in jeder Zeile: alle Funktionen für dieses eine Element, gut lesbar in einer Liste. */
    private void rowMenu(final Entry e) {
        final File f = e.file;
        final List<String> labels = new ArrayList<>();
        final List<Integer> codes = new ArrayList<>();
        if (e.dir) {
            boolean fav = Conn.isFavorite(c, f.getPath());
            labels.add(fav ? "Aus Favoriten entfernen" : "Zu Favoriten hinzufügen");
            codes.add(FAVORITE);
        } else if (Ui.ext(f.getName()).equals("zip")) {
            labels.add("Entpacken");
            codes.add(UNZIP);
            labels.add("Inhalt ansehen");
            codes.add(ZIPLIST);
        }
        if (!e.dir) {
            labels.add("Öffnen mit …");
            codes.add(OPEN);
            labels.add("Bearbeiten mit …");
            codes.add(EDIT);
        }
        String[] base = { "An den PC senden", "Kopieren", "Verschieben", "Umbenennen", "Eigenschaften", "Löschen" };
        int[] baseCodes = { SEND, COPY, MOVE, RENAME, PROPS, DELETE };
        for (int i = 0; i < base.length; i++) {
            labels.add(base[i]);
            codes.add(baseCodes[i]);
        }
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle(f.getName())
                .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        int code = codes.get(which);
                        if (code == FAVORITE) {
                            toggleFavorite(f);
                            return;
                        }
                        if (code == OPEN || code == EDIT) {
                            openWith(f, code == EDIT);
                            return;
                        }
                        if (code == UNZIP) {
                            askUnzip(f);
                            return;
                        }
                        if (code == ZIPLIST) {
                            showZipContents(f);
                            return;
                        }
                        selected.clear();
                        selected.put(f.getPath(), e.size);
                        switch (code) {
                            case SEND: sendSelected(); break;
                            case COPY: markClip(false); break;
                            case MOVE: markClip(true); break;
                            case RENAME: askRename(f); break;
                            case PROPS: selected.clear(); showProperties(f); break;
                            default: askDelete(); break;
                        }
                    }
                }).show();
    }

    private static final int FAVORITE = 0, SEND = 1, COPY = 2, MOVE = 3, RENAME = 4, PROPS = 5, DELETE = 6, UNZIP = 7, ZIPLIST = 8, OPEN = 9, EDIT = 10;

    private File pendingFile;
    private boolean pendingEdit;
    private long pendingSize;
    private long pendingModified;

    /**
     * Öffnet eine Datei in einer anderen App (über {@link FilesProvider}, content://) und merkt sich ihren Stand, um nach
     * der Rückkehr zu prüfen, ob sich die Originaldatei wirklich geändert hat.
     */
    private void openWith(File f, boolean edit) {
        if (!f.isFile()) {
            Toast.makeText(c, "Die Datei gibt es nicht mehr.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri u = FilesProvider.uriFor(f);
        Intent i = new Intent(edit ? Intent.ACTION_EDIT : Intent.ACTION_VIEW);
        i.setDataAndType(u, FilesProvider.mimeOf(f.getName()));
        i.setClipData(android.content.ClipData.newRawUri(f.getName(), u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | (edit ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0));
        if (i.resolveActivity(c.getPackageManager()) == null) {
            Toast.makeText(c, edit ? "Keine App zum Bearbeiten dieser Datei gefunden."
                    : "Keine App zum Öffnen dieser Datei gefunden.", Toast.LENGTH_LONG).show();
            return;
        }
        pendingFile = f;
        pendingEdit = edit;
        pendingSize = f.length();
        pendingModified = f.lastModified();
        try {
            host.activity().startActivity(Intent.createChooser(i, f.getName()));
        } catch (RuntimeException ex) {
            pendingFile = null;
            Toast.makeText(c, "Die Datei lässt sich nicht öffnen.", Toast.LENGTH_LONG).show();
        }
    }

    /** Nach der Rückkehr aus einer anderen App: Stand der Datei prüfen und ehrlich melden. */
    private void checkAfterOpen() {
        File f = pendingFile;
        if (f == null) {
            return;
        }
        pendingFile = null;
        String msg = FileUris.afterReturn(pendingEdit, f.isFile(), pendingSize, pendingModified, f.length(), f.lastModified());
        if (msg != null) {
            Toast.makeText(c, "„" + f.getName() + "“: " + msg, Toast.LENGTH_LONG).show();
        }
    }

    private void toggleFavorite(File f) {
        List<String> favs = Conn.favorites(c);
        boolean was = favs.contains(f.getPath());
        Conn.setFavorites(c, was ? PlaceLists.remove(favs, f.getPath()) : PlaceLists.add(favs, f.getPath()));
        Toast.makeText(c, "„" + f.getName() + "“ " + (was ? "aus den Favoriten entfernt" : "zu den Favoriten hinzugefügt"),
                Toast.LENGTH_SHORT).show();
    }

    /** Wo der Ordner liegt: der übergeordnete Ordner, bei einem Speicher-Hauptordner der Name des Speichers statt "0". */
    private String placeLabel(File f, Vol v) {
        File p = f.getParentFile();
        if (v != null && p != null && p.equals(v.root)) {
            return v.name;
        }
        return parentName(f);
    }

    /** Eine Zeile für einen gemerkten Ordner (Favorit oder zuletzt geöffnet). */
    private View placeRow(final String path, final boolean favorite) {
        final File f = new File(path);
        final boolean here = f.isDirectory();
        Vol v = volumeOf(f);
        String name = v != null && f.equals(v.root) ? v.name : f.getName();
        String sub = here ? (v != null && f.equals(v.root) ? "" : placeLabel(f, v)) : "Nicht verfügbar";
        LinearLayout row = Ui.row(c, favorite ? R.drawable.ic_star : R.drawable.ic_folder,
                favorite ? Ui.primary(c) : Ui.folder(c), name, sub, null, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (here) {
                            openFolder(f);
                        } else {
                            Toast.makeText(c, "Der Ordner ist nicht verfügbar (zum Beispiel SD-Karte entfernt)", Toast.LENGTH_LONG).show();
                        }
                    }
                });
        if (favorite) {
            row.removeViewAt(row.getChildCount() - 1);   // statt des Pfeils das Menü ⋮
            ImageView more = Ui.icon(c, R.drawable.ic_more, Ui.dim(c), 24);
            more.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            more.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            more.setContentDescription("Funktionen für den Favoriten " + name);
            more.setBackground(Ui.pressable(c));
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final List<String> labels = new ArrayList<>();
                    if (here) {
                        labels.add("Öffnen");
                    }
                    labels.add("Aus Favoriten entfernen");
                    new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                            .setTitle(f.getName().isEmpty() ? path : f.getName())
                            .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int which) {
                                    if (labels.get(which).equals("Öffnen")) {
                                        openFolder(f);
                                    } else {
                                        Conn.setFavorites(c, PlaceLists.remove(Conn.favorites(c), path));
                                        buildHome();
                                    }
                                }
                            }).show();
                }
            });
            row.addView(more);
        }
        return row;
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
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
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
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
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
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog).setTitle(f.getName()).setMessage(text).setPositiveButton("Schließen", null).show();
    }

    private interface Op {
        /** Läuft im Hintergrund; liefert die Meldung für den Nutzer. */
        String run();
    }

    // ---- Vorgänge mit Fortschritt, Abbruch, Konfliktfrage und Ergebnis -----------------------------

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    private interface BatchWork {
        FileOps.Result run(FileOps.Progress progress);
    }

    private interface Retry {
        void run(List<File> failedItems);
    }

    /** Fortschritt eines Vorgangs; die Anzeige wird höchstens zehnmal pro Sekunde aufgefrischt. */
    private final class BatchProgress implements FileOps.Progress {
        volatile boolean cancel;
        volatile String name = "";
        volatile long done;
        volatile long total;
        volatile int idx;
        volatile int cnt;
        Runnable refresh;
        private long lastPost;

        @Override
        public boolean cancelled() {
            return cancel;
        }

        @Override
        public void update(String n, long d, long t, int i, int cn) {
            name = n;
            done = d;
            total = t;
            idx = i;
            cnt = cn;
            long now = System.currentTimeMillis();
            if (refresh != null && now - lastPost >= 100) {
                lastPost = now;
                ui.post(refresh);
            }
        }
    }

    /**
     * Führt einen Vorgang im Hintergrund aus. Dauert er länger als einen Augenblick, erscheint ein Fenster mit
     * Fortschritt und "Abbrechen". Danach folgen die Meldung und bei Fehlern eine Liste mit "Nochmal versuchen".
     */
    private void runBatch(final String title, final String verbPast, final BatchWork work, final Retry retry) {
        final BatchProgress prog = new BatchProgress();
        final boolean[] finished = { false };
        final AlertDialog[] dlg = { null };
        final TextView nameView = Ui.label(c, "", 14, Ui.text(c), false);
        final android.widget.ProgressBar bar = new android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        final TextView sizeView = Ui.label(c, "", 13, Ui.dim(c), false);
        final TextView countView = Ui.label(c, "", 13, Ui.dim(c), false);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        prog.refresh = new Runnable() {
            @Override
            public void run() {
                nameView.setText(prog.name);
                bar.setProgress(prog.total > 0 ? (int) Math.min(1000, prog.done * 1000 / prog.total) : 0);
                sizeView.setText(prog.total > 0 ? Ui.size(prog.done) + " von " + Ui.size(prog.total) : "");
                countView.setText(prog.cnt > 1 ? "Datei " + prog.idx + " von " + prog.cnt : "");
            }
        };
        final Runnable show = new Runnable() {
            @Override
            public void run() {
                if (finished[0]) {
                    return;
                }
                LinearLayout v = new LinearLayout(c);
                v.setOrientation(LinearLayout.VERTICAL);
                v.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), 0);
                v.addView(nameView);
                v.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 24)));
                v.addView(sizeView);
                v.addView(countView);
                prog.refresh.run();
                dlg[0] = new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                        .setTitle(title)
                        .setView(v)
                        .setCancelable(false)
                        .setNegativeButton("Abbrechen", null)
                        .create();
                dlg[0].show();
                final android.widget.Button cancelButton = dlg[0].getButton(AlertDialog.BUTTON_NEGATIVE);
                cancelButton.setTextColor(Ui.danger(c));
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View b) {
                        prog.cancel = true;
                        cancelButton.setEnabled(false);
                        cancelButton.setText("Breche ab …");
                    }
                });
            }
        };
        ui.postDelayed(show, 600);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FileOps.Result r = work.run(prog);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        finished[0] = true;
                        ui.removeCallbacks(show);
                        if (dlg[0] != null) {
                            dlg[0].dismiss();
                        }
                        batchDone(r, verbPast, retry);
                    }
                });
            }
        }, "nolphin-fileops").start();
    }

    private void batchDone(FileOps.Result r, String verbPast, Retry retry) {
        for (File f : r.touched) {
            scan(f);
        }
        Toast.makeText(c, r.summary(verbPast), Toast.LENGTH_LONG).show();
        reload();
        if (partner != null) {
            partner.opFinished(this);
        }
        if (!r.failed.isEmpty()) {
            showProblems(r, retry);
        }
    }

    private void showProblems(final FileOps.Result r, final Retry retry) {
        List<String> list = new ArrayList<>(r.failed);
        list.addAll(r.skipped);
        StringBuilder b = new StringBuilder(r.summary("erledigt")).append("\n\n");
        int shown = Math.min(list.size(), 10);
        for (int i = 0; i < shown; i++) {
            b.append("• ").append(list.get(i)).append("\n");
        }
        if (list.size() > shown) {
            b.append("… und ").append(list.size() - shown).append(" weitere");
        }
        AlertDialog.Builder d = new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle("Nicht alles hat geklappt")
                .setMessage(b.toString().trim())
                .setPositiveButton("Schließen", null);
        if (retry != null && !r.retry.isEmpty() && !r.cancelled) {
            d.setNeutralButton("Nochmal versuchen", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    retry.run(new ArrayList<>(r.retry));
                }
            });
        }
        d.show();
    }

    /** Für kurze Vorgänge mit einer Meldung (Umbenennen, Neuer Ordner). */
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
        checkAfterOpen();
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
            docChipScroll.setVisibility(View.GONE);
            sortHint.setVisibility(View.GONE);
            sortHintLine.setVisibility(View.GONE);
            storageRow.setVisibility(View.GONE);
            storageLine.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            return;
        }
        permCard.setVisibility(View.GONE);
        title.setText(home ? "Zum PC" : mode == APPS ? "Apps" : mode == CATEGORY ? CATS[cat] : folderTitle());
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
        boolean showStorage = hasCard() && (mode == CATEGORY || (mode == HOME && searching));
        storageRow.setVisibility(showStorage ? View.VISIBLE : View.GONE);
        storageLine.setVisibility(showStorage ? View.VISIBLE : View.GONE);
        if (showStorage) {
            buildStorageRow();
        }
        boolean listing = mode == FOLDER || mode == CATEGORY;
        sortHint.setVisibility(View.GONE);   // Platz für die Dateien; die Sortierung steht im Menü „Ansicht und Sortierung“
        sortHintLine.setVisibility(View.GONE);
        boolean chips = mode == CATEGORY && cat == 3;
        docChipScroll.setVisibility(chips ? View.VISIBLE : View.GONE);
        if (chips) {
            buildDocChips();
        }
        boolean showSearch = !compact && (searchOpen || searching);
        searchRow.setVisibility(showSearch ? View.VISIBLE : View.GONE);
        searchLine.setVisibility(showSearch ? View.VISIBLE : View.GONE);
        searchBtn.setVisibility(compact ? View.GONE : View.VISIBLE);
        dualBtn.setVisibility(!compact && dualSwitch != null && Conn.dualAvailable(c) ? View.VISIBLE : View.GONE);
        searchBtn.setColorFilter(showSearch ? Ui.primary(c) : Ui.iconTint(c));
        if (compact) {
            crumbBar.setVisibility(View.GONE);
            crumbLine.setVisibility(View.GONE);
            searchRow.setVisibility(View.GONE);
            searchLine.setVisibility(View.GONE);
            sortHint.setVisibility(View.GONE);
            sortHintLine.setVisibility(View.GONE);
            title.setText(pathLabel());
            if (selCopy != null) {
                selCopy.setVisibility(View.GONE);   // kopiert und verschoben wird mit der unteren Leiste
            }
            if (selMove != null) {
                selMove.setVisibility(View.GONE);
            }
        }
        adapter.notifyDataSetChanged();
        if (partner != null && lastNotified != selected.size()) {
            lastNotified = selected.size();
            partner.selectionChanged(this);
        }
    }

    // ---- Schnittstelle für den Zwei-Fenster-Modus -----------------------------------------------

    void setDualSwitch(DualSwitch sw) {
        dualSwitch = sw;
    }

    void setPartner(Partner p) {
        partner = p;
    }

    void setActive(boolean on) {
        activeBar.setBackgroundColor(on ? Ui.primary(c) : 0x00000000);
        // die Marke des aktiven Fensters ist gefüllt
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Ui.dp(c, 10));
        if (on) {
            g.setColor(Ui.primary(c));
            paneTag.setTextColor(Ui.dark(c) ? 0xFF10200A : 0xFFFFFFFF);
        } else {
            g.setColor(Ui.surface(c));
            g.setStroke(Math.max(1, Ui.dp(c, 1)), Ui.line(c));
            paneTag.setTextColor(Ui.dim(c));
        }
        paneTag.setBackground(g);
    }

    void setPaneLabel(String text) {
        paneTag.setText(text);
    }

    int selectionCount() {
        return selected.size();
    }

    List<File> selectionFiles() {
        return selectedFiles();
    }

    void clearSelection() {
        selected.clear();
        render();
    }

    /** Der geöffnete Ordner, oder null in der Übersicht, einer Kategorie oder bei den Apps. */
    File currentDir() {
        return mode == FOLDER ? dir : null;
    }

    /** Wo das Fenster gerade steht, zum Beispiel "Hauptspeicher › Download › Bilder". */
    String pathLabel() {
        if (mode == HOME) {
            return "Übersicht";
        }
        if (mode == APPS) {
            return "Apps";
        }
        if (mode == CATEGORY) {
            return CATS[cat];
        }
        Vol v = volumeOf(dir);
        if (v == null) {
            return dir.getPath();
        }
        StringBuilder b = new StringBuilder(v.name);
        String rest = dir.getPath().substring(v.root.getPath().length());
        for (String part : rest.split("/")) {
            if (!part.isEmpty()) {
                b.append(" › ").append(part);
            }
        }
        return b.toString();
    }

    void reloadList() {
        reload();
    }

    /** Kopiert oder verschiebt die Auswahl in einen Ordner (mit Konfliktfrage, Fortschritt und Ergebnisliste). */
    void transferSelection(File target, boolean move) {
        List<File> items = selectedFiles();
        selected.clear();
        render();
        beginTransfer(items, target, move);
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
    private final Runnable metaLoaded = new Runnable() {
        @Override
        public void run() {
            root.removeCallbacks(metaRefresh);
            root.postDelayed(metaRefresh, 150);   // viele Dateien auf einmal: nur einmal neu zeichnen
        }
    };
    private final Runnable metaRefresh = new Runnable() {
        @Override
        public void run() {
            adapter.notifyDataSetChanged();
        }
    };

    /** Musikdaten einer Audiodatei (aus dem Speicher gelesen) oder null; ohne Titel zeigt die Liste den Dateinamen. */
    private Thumbs.Meta metaOf(Entry e) {
        if (e.dir || e.pkg != null || e.file == null || !isAudio(Ui.ext(e.file.getName()))) {
            return null;
        }
        Thumbs.Meta m = Thumbs.meta(e.file, metaLoaded);
        return m == null || m.empty() ? null : m;
    }

    /** Anzeigename: bei Musik der Titel, wenn die Datei einen enthält. */
    private String shownName(Entry e) {
        Thumbs.Meta m = metaOf(e);
        return m != null && !m.title.isEmpty() ? m.title : nameOf(e);
    }

    /** Symbol- oder Vorschaubildgröße in dp für die Liste (Standard / groß) und die Kompaktansicht. */
    private int rowIcon() {
        return ViewModes.rowIconDp(viewMode, bigPreview());
    }

    /**
     * Symbol oder Vorschaubild. In der Rasteransicht ist die Fläche quadratisch und füllt die Kachelbreite; Dateisymbole
     * bekommen nur einen kleinen Rand, Fotos und Cover füllen alles aus.
     */
    private ImageView iconOf(Entry e) {
        final boolean tile = viewMode == Conn.VIEW_GRID;
        final int pad = tile ? Ui.dp(c, bigPreview() ? 34 : 10) : Ui.dp(c, 2);
        final ImageView v = tile ? new SquareImage(c) : new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setPadding(pad, pad, pad, pad);
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
            v.setBackground(Ui.round(c, 0x00000000, tile ? 8 : 4));
            v.setClipToOutline(true);
            v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            int px = Ui.dp(c, tile ? (bigPreview() ? 170 : 112) : rowIcon());
            Thumbs.loadFile(c, e.file, kind, Math.max(96, px), v, new Runnable() {
                @Override
                public void run() {
                    // kein Vorschaubild (zum Beispiel Audio ohne Albumbild): das Symbol mit Rand zeigen
                    v.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    v.setPadding(pad, pad, pad, pad);
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

    /** Kachel ohne Rahmen: das Symbol füllt die Breite, darunter der Name (wenn nicht ausgeblendet). */
    private View tile(View top, String name, boolean picked) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(picked ? Ui.round(c, Ui.chip(c), 8) : null);
        t.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        t.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT));
        t.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (!hideNames) {
            TextView n = Ui.label(c, name, 12, Ui.text(c), false);
            n.setLines(2);   // feste Höhe: das Raster nimmt die Zeilenhöhe von der ersten Kachel jeder Reihe
            n.setGravity(Gravity.CENTER_HORIZONTAL);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            n.setPadding(Ui.dp(c, 2), Ui.dp(c, 1), Ui.dp(c, 2), Ui.dp(c, 2));
            t.addView(n);
        }
        return picked ? withCheck(t) : t;
    }

    /** Legt ein Häkchen oben rechts über die Kachel. */
    private View withCheck(View t) {
        FrameLayout fl = new FrameLayout(c);
        fl.setLayoutParams(t.getLayoutParams());
        fl.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout b = Ui.badge(c, R.drawable.ic_check, Ui.primary(c), 0xFFFFFFFF, 22);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.dp(c, 6), Ui.dp(c, 6), 0);
        fl.addView(b, lp);
        return fl;
    }

    private String subOf(Entry e) {
        String s = e.dir ? e.count + (e.count == 1 ? " Element" : " Elemente") : Ui.size(e.size);
        return e.sub.isEmpty() ? s : e.sub + " · " + s;
    }

    private View entryTile(Entry e) {
        boolean picked = selected.containsKey(e.path());
        return tile(iconOf(e), shownName(e), picked);
    }

    private View entryRow(Entry e) {
        boolean picked = selected.containsKey(e.path());
        boolean selecting = !selected.isEmpty();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.listRowBg(c, picked));
        boolean slim = viewMode == Conn.VIEW_COMPACT;
        int icon = rowIcon();
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        row.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
                Ui.dp(c, ViewModes.rowHeightDp(viewMode, bigPreview()))));
        if (selecting) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
            cp.rightMargin = Ui.dp(c, 14);
            row.addView(checkbox(picked), cp);
        }
        row.addView(iconOf(e), new LinearLayout.LayoutParams(Ui.dp(c, icon), Ui.dp(c, icon)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 4), 0);
        TextView n = Ui.label(c, shownName(e), slim ? 14 : 15, Ui.text(c), false);
        n.setMaxLines(1);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        Thumbs.Meta mm = metaOf(e);
        String who = mm == null ? "" : mm.artist.isEmpty() ? mm.album : mm.album.isEmpty() ? mm.artist : mm.artist + " · " + mm.album;
        String sub = !who.isEmpty() ? who + (slim ? "" : " · " + Ui.size(e.size))
                : slim ? (e.dir ? subOf(e) : Ui.size(e.size)) : e.dir ? subOf(e)
                : subOf(e) + " · " + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(new Date(e.modified));
        TextView s = Ui.label(c, sub, 12, Ui.dim(c), false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.setPadding(0, Ui.dp(c, 1), 0, 0);
        text.addView(s);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (e.pkg == null && !selecting) {
            ImageView more = Ui.icon(c, R.drawable.ic_more, Ui.dim(c), 24);
            int mp = slim ? 8 : 12;
            more.setPadding(Ui.dp(c, mp), Ui.dp(c, mp), Ui.dp(c, mp), Ui.dp(c, mp));
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
