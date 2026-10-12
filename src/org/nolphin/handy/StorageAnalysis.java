package org.nolphin.handy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speicheranalyse als eigenes Fenster: Belegung nach Gruppen, größte Ordner, größte Dateien. Gezählt wird im
 * Hintergrund (abbrechbar), das Ergebnis bleibt bis zum Neuzählen oder Beenden der App erhalten.
 */
final class StorageAnalysis {
    interface Opener {
        void openFolder(File dir);
    }

    private static final int[] CAT_COLORS = { 0xFF3F8A21, 0xFFE0A020, 0xFF3B82C4, 0xFF9B59B6, 0xFFD9534F, 0xFF8A9486 };
    private static final Map<String, StorageScan.Result> CACHE = new HashMap<>();

    private final Activity activity;
    private final Context c;
    private final String name;
    private final File root;
    private final Opener opener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Dialog dialog;
    private final LinearLayout content;
    private AtomicBoolean cancel = new AtomicBoolean(false);
    private volatile boolean closed;

    static void show(Activity activity, String name, File root, Opener opener) {
        new StorageAnalysis(activity, name, root, opener).start(false);
    }

    private StorageAnalysis(Activity activity, String name, File root, Opener opener) {
        this.activity = activity;
        this.c = activity;
        this.name = name;
        this.root = root;
        this.opener = opener;
        dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.bg(c));
        page.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog.setContentView(page);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.bg(c)));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setStatusBarColor(Ui.bar(c));
            w.setNavigationBarColor(Ui.bg(c));
            if (!Ui.dark(c)) {
                w.getDecorView().setSystemUiVisibility(w.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                closed = true;
                cancel.set(true);
            }
        });
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.surface(c));
        bar.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        bar.addView(iconButton(R.drawable.ic_back, "Zurück", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        }));
        TextView t = Ui.label(c, name, 18, Ui.text(c), true);
        t.setPadding(Ui.dp(c, 8), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(iconButton(R.drawable.ic_refresh, "Neu zählen", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel.set(true);
                start(true);
            }
        }));
        return bar;
    }

    private View iconButton(int res, String desc, View.OnClickListener l) {
        android.widget.ImageView i = Ui.icon(c, res, Ui.iconTint(c), 24);
        i.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        i.setBackground(Ui.pressable(c));
        i.setContentDescription(desc);
        i.setOnClickListener(l);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        return i;
    }

    private void start(boolean forceNew) {
        if (!dialog.isShowing()) {
            dialog.show();
        }
        StorageScan.Result cached = forceNew ? null : CACHE.get(root.getPath());
        if (cached != null) {
            showResult(cached);
            return;
        }
        final AtomicBoolean mine = new AtomicBoolean(false);
        cancel = mine;
        showScanning(0, 0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StorageScan.Result r = StorageScan.scan(root, new StorageScan.Progress() {
                    @Override
                    public void update(final long files, final long bytes) {
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!closed && !mine.get()) {
                                    updateCounter(files, bytes);
                                }
                            }
                        });
                    }
                }, mine);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (closed || cancel != mine) {
                            return;
                        }
                        if (!r.cancelled) {
                            CACHE.put(root.getPath(), r);
                        }
                        showResult(r);
                    }
                });
            }
        }, "nolphin-speicheranalyse").start();
    }

    private TextView counter;

    private void showScanning(long files, long bytes) {
        content.removeAllViews();
        LinearLayout card = Ui.card(c);
        card.addView(Ui.label(c, "Wird gezählt …", 22, Ui.text(c), true));
        TextView hint = Ui.label(c, "Das läuft im Hintergrund. Sie können die App weiter benutzen.", 14, Ui.dim(c), false);
        hint.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12));
        card.addView(hint);
        ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
        card.addView(pb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 6)));
        counter = Ui.label(c, "", 14, Ui.dim(c), false);
        counter.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        card.addView(counter);
        updateCounter(files, bytes);
        card.addView(Ui.actionButton(c, "Abbrechen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));
        content.addView(card);
    }

    private void updateCounter(long files, long bytes) {
        if (counter != null) {
            counter.setText(String.format(java.util.Locale.GERMANY, "%,d", files) + " Dateien · " + Ui.size(bytes) + " bisher");
        }
    }

    private void showResult(StorageScan.Result r) {
        content.removeAllViews();
        long total = 0, used = 0;
        try {
            StatFs sf = new StatFs(root.getPath());
            total = sf.getTotalBytes();
            used = total - sf.getAvailableBytes();
        } catch (RuntimeException ignored) {
        }
        LinearLayout card = Ui.card(c);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.label(c, Ui.size(used > 0 ? used : r.bytes), 22, Ui.text(c), true));
        TextView of = Ui.label(c, total > 0 ? "  von " + Ui.size(total) + " belegt" : "  belegt", 14, Ui.dim(c), false);
        head.addView(of);
        card.addView(head);

        long sum = 0;
        for (long v : r.byCat) {
            sum += v;
        }
        LinearLayout seg = new LinearLayout(c);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setWeightSum(Math.max(1, sum));
        for (int i = 0; i < r.byCat.length; i++) {
            if (r.byCat[i] <= 0) {
                continue;
            }
            View s = new View(c);
            s.setBackgroundColor(CAT_COLORS[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, r.byCat[i]);
            lp.rightMargin = Ui.dp(c, 2);
            seg.addView(s, lp);
        }
        seg.setBackground(Ui.round(c, Ui.line(c), 7));
        seg.setClipToOutline(true);
        seg.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 14));
        sp.topMargin = Ui.dp(c, 12);
        sp.bottomMargin = Ui.dp(c, 10);
        card.addView(seg, sp);

        for (int i = 0; i < r.byCat.length; i += 2) {
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.addView(legend(i, r.byCat[i]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            View gap = new View(c);
            line.addView(gap, new LinearLayout.LayoutParams(Ui.dp(c, 16), 1));
            if (i + 1 < r.byCat.length) {
                line.addView(legend(i + 1, r.byCat[i + 1]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            line.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 3));
            card.addView(line);
        }

        String when = r.cancelled ? "Abgebrochen, das Ergebnis ist unvollständig." : "Stand: " + Ui.time(r.finishedAt);
        long gap = used - r.bytes;
        if (!r.cancelled && total > 0 && gap > 1L << 30 && gap > used / 20) {
            when += "\n" + Ui.size(gap) + " sind für diese App nicht lesbar (System und Daten anderer Apps).";
        }
        if (r.unreadable > 0) {
            when += "\n" + r.unreadable + (r.unreadable == 1 ? " Ordner" : " Ordner") + " nicht lesbar.";
        }
        TextView stand = Ui.label(c, when, 12, Ui.dim(c), false);
        stand.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 12));
        card.addView(stand);
        card.addView(Ui.actionButton(c, "Dateien ansehen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                opener.openFolder(root);
            }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));
        content.addView(card);

        if (!r.topFolders.isEmpty()) {
            content.addView(Ui.heading(c, "GRÖSSTE ORDNER"));
            LinearLayout g = Ui.group(c);
            long max = Math.max(1, r.topFolders.get(0).bytes);
            for (final StorageScan.Item it : r.topFolders) {
                Ui.addRow(c, g, bounded(R.drawable.ic_folder, Ui.folder(c), it.file.getName(), Ui.size(it.bytes), it.bytes, max,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                                opener.openFolder(it.file);
                            }
                        }));
            }
            content.addView(g);
        }
        if (!r.topFiles.isEmpty()) {
            content.addView(Ui.heading(c, "GRÖSSTE DATEIEN"));
            LinearLayout g = Ui.group(c);
            long max = Math.max(1, r.topFiles.get(0).bytes);
            for (final StorageScan.Item it : r.topFiles) {
                File parent = it.file.getParentFile();
                String where = parent == null ? "" : parent.getName();
                Ui.addRow(c, g, bounded(R.drawable.ic_file, Ui.iconTint(c), it.file.getName(), Ui.size(it.bytes) + " · " + where, it.bytes, max,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                File p = it.file.getParentFile();
                                if (p != null) {
                                    dialog.dismiss();
                                    opener.openFolder(p);
                                }
                            }
                        }));
            }
            content.addView(g);
        }
    }

    private View legend(int cat, long bytes) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(Ui.round(c, CAT_COLORS[cat], 2));
        l.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)));
        TextView n = Ui.label(c, StorageScan.CATS[cat], 13, Ui.text(c), false);
        n.setPadding(Ui.dp(c, 8), 0, 0, 0);
        n.setSingleLine(true);
        l.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        l.addView(Ui.label(c, Ui.size(bytes), 13, Ui.text(c), true));
        return l;
    }

    /** Zeile mit schmalem Anteilsbalken darunter. */
    private View bounded(int icon, int tint, String title, String sub, long bytes, long max, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.row(c, icon, tint, title, sub, null, click));
        LinearLayout track = new LinearLayout(c);
        track.setWeightSum(100);
        track.setBackground(Ui.round(c, Ui.line(c), 2));
        View fill = new View(c);
        fill.setBackground(Ui.round(c, Ui.primary(c), 2));
        int pct = (int) Math.max(2, Math.round(bytes * 100.0 / max));
        track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 4));
        tp.leftMargin = Ui.dp(c, 56);
        tp.rightMargin = Ui.dp(c, 16);
        tp.bottomMargin = Ui.dp(c, 10);
        box.addView(track, tp);
        box.setClickable(false);
        return box;
    }
}
