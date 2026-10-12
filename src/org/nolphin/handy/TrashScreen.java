package org.nolphin.handy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Das Papierkorb-Fenster: Liste, Zurückholen, endgültig Löschen, Leeren. */
final class TrashScreen {
    interface Changed {
        /** Etwas wurde zurückgeholt oder gelöscht: Listen neu laden, Mediensuche anstoßen. */
        void changed(List<File> touched);
    }

    private final Activity activity;
    private final Context c;
    private final List<File> roots;
    private final Changed callback;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Dialog dialog;
    private final LinearLayout content;
    private final TextView selLabel;
    private final android.widget.Button backBtn;
    private final android.widget.Button delBtn;
    private List<TrashOps.Entry> entries = new ArrayList<>();
    private long[] bytes = new long[0];
    private long[] counts = new long[0];
    private final Set<String> picked = new HashSet<>();
    private boolean loading = true;
    private boolean busy;

    static void show(Activity activity, List<File> roots, Changed callback) {
        new TrashScreen(activity, roots, callback);
    }

    private TrashScreen(Activity activity, List<File> roots, Changed callback) {
        this.activity = activity;
        this.c = activity;
        this.roots = roots;
        this.callback = callback;
        dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.bg(c));
        page.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 52)));
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(Ui.surface(c));
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        selLabel = Ui.label(c, "", 15, Ui.text(c), true);
        bottom.addView(selLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        delBtn = Ui.actionButton(c, "Löschen", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askDeleteForever(pickedEntries());
            }
        });
        backBtn = Ui.actionButton(c, "Zurück", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restore(pickedEntries());
            }
        });
        bottom.addView(delBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44));
        bp.leftMargin = Ui.dp(c, 8);
        bottom.addView(backBtn, bp);
        page.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(page);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.bg(c)));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setStatusBarColor(Ui.bar(c));
            w.setNavigationBarColor(Ui.surface(c));
            if (!Ui.dark(c)) {
                w.getDecorView().setSystemUiVisibility(w.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        }
        dialog.show();
        reload();
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
        TextView t = Ui.label(c, "Papierkorb", 18, Ui.text(c), true);
        t.setPadding(Ui.dp(c, 8), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(iconButton(R.drawable.ic_more, "Mehr", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu();
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

    private void menu() {
        final String[] items = { "Alle auswählen", "Papierkorb leeren" };
        new AlertDialog.Builder(activity, R.style.NolphinDialog)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            for (TrashOps.Entry e : entries) {
                                picked.add(e.stored.getPath());
                            }
                            render();
                        } else if (!entries.isEmpty()) {
                            askDeleteForever(new ArrayList<>(entries));
                        }
                    }
                }).show();
    }

    private List<TrashOps.Entry> pickedEntries() {
        List<TrashOps.Entry> l = new ArrayList<>();
        for (TrashOps.Entry e : entries) {
            if (picked.contains(e.stored.getPath())) {
                l.add(e);
            }
        }
        return l;
    }

    // ---- Laden -----------------------------------------------------------------------------------

    private void reload() {
        loading = true;
        render();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<TrashOps.Entry> list = TrashOps.list(roots);
                final long[] b = new long[list.size()];
                final long[] n = new long[list.size()];
                for (int i = 0; i < b.length; i++) {
                    long[] m = FileOps.measure(list.get(i).stored);
                    b[i] = m[0];
                    n[i] = m[1];
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        entries = list;
                        bytes = b;
                        counts = n;
                        Set<String> still = new HashSet<>();
                        for (TrashOps.Entry e : list) {
                            still.add(e.stored.getPath());
                        }
                        picked.retainAll(still);
                        loading = false;
                        busy = false;
                        render();
                    }
                });
            }
        }, "nolphin-papierkorb").start();
    }

    private void render() {
        content.removeAllViews();
        long total = 0;
        for (long b : bytes) {
            total += b;
        }
        LinearLayout note = Ui.card(c);
        note.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        String head = loading ? "Wird gelesen …" : entries.isEmpty() ? "Der Papierkorb ist leer."
                : entries.size() + (entries.size() == 1 ? " Element · " : " Elemente · ") + Ui.size(total) + ".";
        TextView t = Ui.label(c, head + " Gelöschtes bleibt 30 Tage hier und belegt weiter Platz. Das gilt nur für Löschen in dieser App.",
                13, Ui.dim(c), false);
        note.addView(t);
        content.addView(note);
        long now = System.currentTimeMillis();
        if (!entries.isEmpty()) {
            LinearLayout g = Ui.group(c);
            for (int i = 0; i < entries.size(); i++) {
                Ui.addRow(c, g, row(entries.get(i), i, now));
            }
            content.addView(g);
        }
        int n = picked.size();
        selLabel.setText(n == 0 ? "Nichts gewählt" : n + " gewählt");
        boolean on = n > 0 && !busy;
        delBtn.setEnabled(on);
        backBtn.setEnabled(on);
        delBtn.setAlpha(on ? 1f : 0.4f);
        backBtn.setAlpha(on ? 1f : 0.4f);
    }

    private View row(final TrashOps.Entry e, int i, long now) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, 60));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        boolean on = picked.contains(e.stored.getPath());
        r.setBackground(Ui.pressable(c));
        if (on) {
            r.setBackgroundColor(Ui.chip(c));
        }
        CheckBox cb = new CheckBox(c);
        cb.setChecked(on);
        cb.setClickable(false);
        cb.setFocusable(false);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.primary(c)));
        r.addView(cb);
        android.widget.ImageView icon = Ui.icon(c, e.dir ? R.drawable.ic_folder : R.drawable.ic_file, e.dir ? Ui.folder(c) : Ui.iconTint(c), 24);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        ip.leftMargin = Ui.dp(c, 8);
        r.addView(icon, ip);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 4), 0);
        TextView name = Ui.label(c, e.name, 15, Ui.text(c), true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(name);
        File parent = e.originalParent();
        String where = parent == null ? "" : parent.getName();
        String size = e.dir ? counts[i] + (counts[i] == 1 ? " Datei" : " Dateien") : Ui.size(bytes[i]);
        TextView sub = Ui.label(c, where + " · " + size + " · " + TrashOps.age(e.when, now), 12, Ui.dim(c), false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setPadding(0, Ui.dp(c, 2), 0, 0);
        text.addView(sub);
        r.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String k = e.stored.getPath();
                if (!picked.remove(k)) {
                    picked.add(k);
                }
                render();
            }
        });
        return r;
    }

    // ---- Aktionen ---------------------------------------------------------------------------------

    private void askDeleteForever(final List<TrashOps.Entry> list) {
        if (list.isEmpty()) {
            return;
        }
        boolean all = list.size() == entries.size();
        String what = list.size() == 1 ? "„" + list.get(0).name + "“ wird" : list.size() + " Elemente werden";
        new AlertDialog.Builder(activity, R.style.NolphinDialog)
                .setTitle(all && list.size() > 1 ? "Papierkorb leeren?" : "Endgültig löschen?")
                .setMessage(what + " endgültig gelöscht. Das lässt sich nicht rückgängig machen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        run(list, false);
                    }
                }).show();
    }

    private void restore(List<TrashOps.Entry> list) {
        if (!list.isEmpty()) {
            run(list, true);
        }
    }

    private void run(final List<TrashOps.Entry> list, final boolean restore) {
        busy = true;
        render();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FileOps.Result r = restore ? TrashOps.restore(list, null) : TrashOps.deleteForever(list, null);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        String msg = r.summary(restore ? "zurückgeholt" : "endgültig gelöscht");
                        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
                        if (!r.failed.isEmpty()) {
                            StringBuilder b = new StringBuilder();
                            for (int i = 0; i < Math.min(8, r.failed.size()); i++) {
                                b.append("• ").append(r.failed.get(i)).append("\n");
                            }
                            new AlertDialog.Builder(activity, R.style.NolphinDialog)
                                    .setTitle("Nicht alles hat geklappt")
                                    .setMessage(b.toString().trim())
                                    .setPositiveButton("Schließen", null).show();
                        }
                        callback.changed(r.touched);
                        picked.clear();
                        reload();
                    }
                });
            }
        }, "nolphin-papierkorb-op").start();
    }
}
