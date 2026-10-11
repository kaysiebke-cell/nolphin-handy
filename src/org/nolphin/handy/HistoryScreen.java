package org.nolphin.handy;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Verlauf: alle Übertragungen in beide Richtungen, neueste zuerst. */
final class HistoryScreen implements Screen {
    private final Context c;
    private final LinearLayout root;
    private final ListView list;
    private final TextView empty;
    private List<Store.Entry> data = new ArrayList<>();
    private String signature = "";

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int i) {
            return data.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            return row(data.get(i));
        }
    };

    HistoryScreen(final Context c, Host host) {
        this.c = c;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int p = Ui.dp(c, 16);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(p, p, p, Ui.dp(c, 6));
        head.addView(Ui.label(c, "Verlauf", 22, Ui.text(c), true));
        head.addView(Ui.label(c, "Dateien vom PC und an den PC", 13, Ui.dim(c), false));
        root.addView(head);
        list = new ListView(c);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        empty = Ui.label(c, "Noch keine Übertragungen.", 15, Ui.dim(c), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(p, p * 3, p, p);
        root.addView(empty);
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
        List<Store.Entry> now = Store.history(c);
        String sig = now.size() + ":" + (now.isEmpty() ? 0 : now.get(0).time);
        if (!sig.equals(signature)) {
            signature = sig;
            data = now;
            adapter.notifyDataSetChanged();
        }
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View row(Store.Entry e) {
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(Ui.dp(c, 16), Ui.dp(c, 5), Ui.dp(c, 16), Ui.dp(c, 5));
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(c, Ui.surface(c), 14));
        int p = Ui.dp(c, 12);
        card.setPadding(p, p, p, p);
        int color = !e.ok ? Ui.danger(c) : (e.down ? Ui.accent(c) : Ui.primary(c));
        card.addView(Ui.badge(c, !e.ok ? R.drawable.ic_close : (e.down ? R.drawable.ic_download : R.drawable.ic_upload),
                Ui.dark(c) ? Ui.chip(c) : Ui.chip(c), color, 40));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 12), 0, 0, 0);
        TextView name = Ui.label(c, e.name, 15, Ui.text(c), false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(name);
        String dir = e.down ? "Vom PC" : "Zum PC";
        text.addView(Ui.label(c, dir + "  ·  " + Ui.size(e.size) + "  ·  " + Ui.time(e.time)
                + (e.ok ? "" : "  ·  " + e.note), 12, e.ok ? Ui.dim(c) : Ui.danger(c), false));
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        wrap.addView(card);
        return wrap;
    }
}
