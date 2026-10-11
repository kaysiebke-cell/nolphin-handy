package org.nolphin.handy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Start: Verbindungsstatus, laufende Übertragung, Abkürzungen und die letzten Übertragungen. */
final class HomeScreen implements Screen {
    private final Context c;
    private final Host host;
    private final ScrollView root;
    private final LinearLayout col;
    private String signature = "";
    private ProgressBar bar;
    private TextView percent;
    private TextView title;

    HomeScreen(Context c, Host host) {
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
        boolean connected = Conn.connected(c);
        boolean active = !SyncService.transferName.isEmpty();
        List<Store.Entry> recent = Store.history(c);
        String sig = connected + "|" + Conn.pcName(c) + "|" + Conn.host(c) + "|" + host.error() + "|" + active
                + "|" + SyncService.files.length + "|" + (recent.isEmpty() ? 0 : recent.get(0).time) + "|"
                + SyncService.status + "|" + Updater.availableName + "|" + Updater.message;
        if (!sig.equals(signature)) {
            signature = sig;
            build(connected, active, recent);
        }
        if (active && bar != null) {
            long total = SyncService.transferTotal;
            int value = total > 0 ? (int) (SyncService.transferDone * 1000 / total) : 0;
            bar.setIndeterminate(total <= 0);
            bar.setProgress(value);
            percent.setText(total > 0 ? (value / 10) + " %  ·  " + Ui.size(SyncService.transferDone) + " von "
                    + Ui.size(total) : Ui.size(SyncService.transferDone));
            title.setText((SyncService.transferDown ? "Lade vom PC: " : "Sende an den PC: ") + SyncService.transferName);
        }
    }

    private void build(boolean connected, boolean active, List<Store.Entry> recent) {
        col.removeAllViews();
        bar = null;

        // Status
        LinearLayout status = Ui.card(c);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.badge(c, R.drawable.ic_phone, connected ? Ui.accent(c) : Ui.line(c),
                connected ? 0xFFFFFFFF : Ui.dim(c), 56));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 14), 0, 0, 0);
        if (connected) {
            String st = SyncService.status;
            boolean problem = st.startsWith("PC nicht erreichbar") || st.startsWith("Schlüssel");
            text.addView(Ui.label(c, problem ? "Verbindung gestört" : "Verbunden", 20,
                    problem ? Ui.danger(c) : Ui.text(c), true));
            String name = Conn.pcName(c);
            text.addView(Ui.label(c, name.isEmpty() ? Conn.host(c) : "PC: " + name, 14, Ui.dim(c), false));
            if (problem) {
                text.addView(Ui.label(c, st, 13, Ui.danger(c), false));
            } else if (!name.isEmpty()) {
                text.addView(Ui.label(c, Conn.host(c), 12, Ui.dim(c), false));
            }
        } else {
            text.addView(Ui.label(c, "Nicht verbunden", 20, Ui.text(c), true));
            text.addView(Ui.label(c, "Mit deinem PC verbinden, um Dateien auszutauschen", 14, Ui.dim(c), false));
        }
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        status.addView(row);
        if (host.error() != null && !connected) {
            TextView e = Ui.label(c, host.error(), 13, Ui.danger(c), false);
            e.setPadding(0, Ui.dp(c, 12), 0, 0);
            status.addView(e);
        }
        if (connected) {
            status.addView(Ui.button(c, "Verbindung trennen", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.disconnect();
                }
            }));
        } else {
            status.addView(Ui.button(c, "Mit dem PC verbinden (QR-Code scannen)", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.scan();
                }
            }));
        }
        col.addView(status);

        if (!connected) {
            LinearLayout steps = Ui.card(c);
            steps.addView(Ui.label(c, "So geht es", 16, Ui.text(c), true));
            String[] lines = {
                "1. Am PC in Nolphin links auf „Handy“ klicken",
                "2. Hier auf „Mit dem PC verbinden“ tippen",
                "3. Die Kamera auf den QR-Code halten, zum Scharfstellen aufs Bild tippen",
            };
            for (String l : lines) {
                TextView t = Ui.label(c, l, 14, Ui.dim(c), false);
                t.setPadding(0, Ui.dp(c, 6), 0, 0);
                steps.addView(t);
            }
            col.addView(steps);
            return;
        }

        // Neue Version der App am PC
        if (Updater.availableName != null) {
            LinearLayout card = Ui.card(c);
            card.addView(Ui.label(c, "Neue Version " + Updater.availableName, 16, Ui.text(c), true));
            card.addView(Ui.label(c, Updater.message.isEmpty()
                    ? "Nolphin am PC hat eine neuere Handy-App. Deine Verbindung bleibt erhalten." : Updater.message,
                    13, Ui.dim(c), false));
            card.addView(Ui.button(c, "Aktualisieren", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Updater.install(host.activity());
                }
            }));
            col.addView(card);
        }

        // Laufende Übertragung
        if (active) {
            LinearLayout card = Ui.card(c);
            title = Ui.label(c, "", 15, Ui.text(c), true);
            card.addView(title);
            bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000);
            bar.setProgressTintList(ColorStateList.valueOf(Ui.accent(c)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 10));
            lp.topMargin = Ui.dp(c, 10);
            card.addView(bar, lp);
            percent = Ui.label(c, "", 13, Ui.dim(c), false);
            percent.setPadding(0, Ui.dp(c, 6), 0, 0);
            card.addView(percent);
            card.addView(Ui.button(c, "Abbrechen", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.cancelTransfer();
                }
            }));
            col.addView(card);
        }

        // Abkürzungen: beide Richtungen
        LinearLayout tiles = new LinearLayout(c);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        int n = SyncService.files.length;
        tiles.addView(tile(R.drawable.ic_download, "Vom PC", n == 0 ? "Noch nichts da" : n + (n == 1 ? " Datei" : " Dateien"),
                Host.TAB_FROM_PC, true));
        tiles.addView(tile(R.drawable.ic_upload, "Zum PC", "Fotos und Dateien senden", Host.TAB_TO_PC, false));
        col.addView(tiles);

        // Zuletzt
        LinearLayout last = Ui.card(c);
        last.addView(Ui.label(c, "Zuletzt", 16, Ui.text(c), true));
        if (recent.isEmpty()) {
            TextView t = Ui.label(c, "Noch keine Übertragungen.", 14, Ui.dim(c), false);
            t.setPadding(0, Ui.dp(c, 8), 0, 0);
            last.addView(t);
        }
        for (int i = 0; i < Math.min(3, recent.size()); i++) {
            Store.Entry e = recent.get(i);
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(c, 10), 0, 0);
            r.addView(Ui.icon(c, e.down ? R.drawable.ic_download : R.drawable.ic_upload,
                    e.ok ? (e.down ? Ui.accent(c) : Ui.primary(c)) : Ui.danger(c), 20));
            LinearLayout t = new LinearLayout(c);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(Ui.dp(c, 12), 0, 0, 0);
            TextView name = Ui.label(c, e.name, 14, Ui.text(c), false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            t.addView(name);
            t.addView(Ui.label(c, Ui.time(e.time) + (e.ok ? "" : "  ·  " + e.note), 12,
                    e.ok ? Ui.dim(c) : Ui.danger(c), false));
            r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            last.addView(r);
        }
        if (!recent.isEmpty()) {
            last.addView(Ui.button(c, "Alle anzeigen", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.show(Host.TAB_HISTORY);
                }
            }));
        }
        col.addView(last);
    }

    private View tile(int iconRes, String title, String sub, final int tab, boolean left) {
        LinearLayout t = Ui.card(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.bottomMargin = Ui.dp(c, 12);
        if (left) {
            lp.rightMargin = Ui.dp(c, 6);
        } else {
            lp.leftMargin = Ui.dp(c, 6);
        }
        t.setLayoutParams(lp);
        t.addView(Ui.badge(c, iconRes, Ui.chip(c), Ui.primary(c), 44));
        TextView a = Ui.label(c, title, 16, Ui.text(c), true);
        a.setPadding(0, Ui.dp(c, 10), 0, 0);
        t.addView(a);
        t.addView(Ui.label(c, sub, 12, Ui.dim(c), false));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.show(tab);
            }
        });
        return t;
    }
}
