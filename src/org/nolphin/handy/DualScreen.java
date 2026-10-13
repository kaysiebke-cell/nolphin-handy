package org.nolphin.handy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Zwei unabhängig navigierbare Dateifenster. Im Hochformat stehen sie untereinander, im Querformat nebeneinander.
 * Das Fenster mit der Auswahl ist die Quelle, das andere das Ziel. Kopiert und verschoben wird mit der vorhandenen
 * Dateilogik (Fortschritt, Abbrechen, Konfliktfrage, Ergebnisliste).
 */
final class DualScreen implements Screen, ToPcScreen.Partner {
    /** Ein Fenster samt Rahmen, der jede Berührung als "dieses Fenster ist aktiv" meldet. */
    private final class PaneFrame extends FrameLayout {
        final ToPcScreen pane;

        PaneFrame(Context c, ToPcScreen pane) {
            super(c);
            this.pane = pane;
            addView(pane.view(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                activate(pane);
            }
            return false;
        }
    }

    private final Context c;
    private final Host host;
    private final LinearLayout root;
    private final ToPcScreen first;
    private final ToPcScreen second;
    private final LinearLayout actionBar;
    private final TextView flow;
    private final ToPcScreen.DualSwitch sw;
    private ToPcScreen active;

    DualScreen(Context c, Host host, final ToPcScreen.DualSwitch sw) {
        this.c = c;
        this.host = host;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));

        this.sw = sw;

        first = new ToPcScreen(c, host, true, "1");
        second = new ToPcScreen(c, host, true, "2");
        first.setPartner(this);
        second.setPartner(this);

        boolean landscape = c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout panes = new LinearLayout(c);
        panes.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(landscape ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                landscape ? LinearLayout.LayoutParams.MATCH_PARENT : 0, 1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(landscape ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                landscape ? LinearLayout.LayoutParams.MATCH_PARENT : 0, 1);
        if (landscape) {
            lp2.leftMargin = Math.max(1, Ui.dp(c, 1));
        } else {
            lp2.topMargin = Math.max(1, Ui.dp(c, 1));
        }
        panes.setBackgroundColor(Ui.line(c));
        panes.addView(new PaneFrame(c, first), lp1);
        panes.addView(new PaneFrame(c, second), lp2);
        root.addView(panes, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Untere Leiste: von wo nach wo, mit Kopieren und Verschieben
        actionBar = new LinearLayout(c);
        actionBar.setOrientation(LinearLayout.VERTICAL);
        actionBar.setBackgroundColor(Ui.surface(c));
        actionBar.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        actionBar.setElevation(Ui.dp(c, 6));
        flow = Ui.label(c, "", 13, Ui.dim(c), false);
        flow.setPadding(0, 0, 0, Ui.dp(c, 6));
        actionBar.addView(flow);
        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1);
        bp.rightMargin = Ui.dp(c, 8);
        buttons.addView(Ui.actionButton(c, "Kopieren", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(false);
            }
        }), bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1);
        buttons.addView(Ui.actionButton(c, "Verschieben", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(true);
            }
        }), bp2);
        actionBar.addView(buttons);
        actionBar.setVisibility(View.GONE);
        root.addView(actionBar);

        activate(first);
    }

    private ToPcScreen other(ToPcScreen s) {
        return s == first ? second : first;
    }

    private void activate(ToPcScreen s) {
        active = s;
        first.setActive(s == first);
        second.setActive(s == second);
        updateLabels();
        updateBar();
    }

    private ToPcScreen source() {
        if (first.selectionCount() > 0 && second.selectionCount() == 0) {
            return first;
        }
        if (second.selectionCount() > 0 && first.selectionCount() == 0) {
            return second;
        }
        if (first.selectionCount() > 0 && second.selectionCount() > 0) {
            return active != null && active.selectionCount() > 0 ? active : first;
        }
        return null;
    }

    private void updateLabels() {
        ToPcScreen src = source();
        if (src == null) {
            first.setPaneLabel("Fenster 1");
            second.setPaneLabel("Fenster 2");
        } else {
            src.setPaneLabel("Quelle");
            other(src).setPaneLabel("Ziel");
        }
    }

    private void updateBar() {
        ToPcScreen src = source();
        actionBar.setVisibility(src == null ? View.GONE : View.VISIBLE);
        if (src == null) {
            return;
        }
        File target = other(src).currentDir();
        flow.setText(target == null ? "Im anderen Fenster zuerst den Zielordner öffnen"
                : "Von " + src.pathLabel() + "\nnach " + other(src).pathLabel());
    }

    @Override
    public void selectionChanged(ToPcScreen s) {
        if (s.selectionCount() > 0) {
            activate(s);
            if (other(s).selectionCount() > 0) {
                other(s).clearSelection();   // nur ein Fenster hat eine Auswahl: das ist die Quelle
            }
        }
        updateLabels();
        updateBar();
    }

    @Override
    public void opFinished(ToPcScreen s) {
        first.reloadList();
        second.reloadList();
        updateBar();
    }

    /** Vor dem Vorgang bestätigen lassen: was, wohin, mit der Möglichkeit, das Ziel zu wechseln. */
    private void confirm(final boolean move) {
        final ToPcScreen src = source();
        if (src == null) {
            return;
        }
        final ToPcScreen dst = other(src);
        final File target = dst.currentDir();
        if (target == null) {
            Toast.makeText(c, "Im anderen Fenster zuerst den Zielordner öffnen", Toast.LENGTH_LONG).show();
            activate(dst);
            return;
        }
        final List<File> items = src.selectionFiles();
        StringBuilder names = new StringBuilder();
        int shown = Math.min(items.size(), 3);
        for (int i = 0; i < shown; i++) {
            names.append(items.get(i).getName()).append(i < shown - 1 ? ", " : "");
        }
        if (items.size() > shown) {
            names.append(" und ").append(items.size() - shown).append(" weitere");
        }
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.dp(c, 24), Ui.dp(c, 4), Ui.dp(c, 24), 0);
        TextView what = Ui.label(c, names.toString(), 14, Ui.dim(c), false);
        what.setPadding(0, 0, 0, Ui.dp(c, 10));
        v.addView(what);
        TextView to = Ui.label(c, "Ziel: " + dst.pathLabel(), 14, Ui.text(c), true);
        to.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        to.setBackground(Ui.outlined(c, Ui.surface(c)));
        v.addView(to);
        String verb = move ? "verschieben" : "kopieren";
        new AlertDialog.Builder(host.activity(), R.style.NolphinDialog)
                .setTitle(items.size() + (items.size() == 1 ? " Element " : " Elemente ") + verb + "?")
                .setView(v)
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Ziel wechseln", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        activate(dst);
                        Toast.makeText(c, "Im markierten Fenster den Zielordner öffnen und dann erneut auf „"
                                + (move ? "Verschieben" : "Kopieren") + "“ tippen", Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton(move ? "Verschieben" : "Kopieren", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        src.transferSelection(target, move);
                    }
                }).show();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public String[] menuLabels() {
        return new String[] { "Ein Fenster" };
    }

    @Override
    public void menuPicked(int index) {
        if (index == 0) {
            sw.setDual(false);
        }
    }

    @Override
    public void onShow() {
        first.onShow();
        second.onShow();
        updateLabels();
        updateBar();
    }

    @Override
    public void refresh() {
        first.refresh();
        second.refresh();
    }

    @Override
    public boolean onBack() {
        if (active != null && active.onBack()) {
            return true;
        }
        return other(active != null ? active : first).onBack();
    }
}
