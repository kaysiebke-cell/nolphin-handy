package org.nolphin.handy;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Handy → PC: Fotos im Raster wählen und senden; andere Dateien über die System-Dateiauswahl. */
final class ToPcScreen implements Screen {
    private final Context c;
    private final Host host;
    private final LinearLayout root;
    private final FrameLayout body;
    private final LinearLayout footer;
    private final TextView footerText;
    private final List<Long> ids = new ArrayList<>();
    private final Set<Long> selected = new LinkedHashSet<>();
    private GridView grid;
    private String state = "";
    private boolean loaded;

    /** Quadratische Zelle: Vorschau mit Haken, wenn gewählt. */
    private final class Cell extends FrameLayout {
        final ImageView image;
        final View mark;
        final LinearLayout check;

        Cell(Context c) {
            super(c);
            image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Ui.chip(c));
            addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            mark = new View(c);
            mark.setBackgroundColor(0x661C5F93);
            mark.setVisibility(GONE);
            addView(mark, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            check = Ui.badge(c, R.drawable.ic_check, Ui.accent(c), 0xFFFFFFFF, 26);
            LayoutParams lp = new LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.TOP | Gravity.END);
            lp.setMargins(0, Ui.dp(c, 6), Ui.dp(c, 6), 0);
            check.setLayoutParams(lp);
            check.setVisibility(GONE);
            addView(check);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, w);
        }
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return ids.size();
        }

        @Override
        public Object getItem(int i) {
            return ids.get(i);
        }

        @Override
        public long getItemId(int i) {
            return ids.get(i);
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Cell cell = convert instanceof Cell ? (Cell) convert : new Cell(c);
            long id = ids.get(i);
            Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            cell.image.setImageDrawable(null);
            Thumbs.load(c, uri, 256, cell.image, new Runnable() {
                @Override
                public void run() {
                }
            });
            boolean on = selected.contains(id);
            cell.mark.setVisibility(on ? View.VISIBLE : View.GONE);
            cell.check.setVisibility(on ? View.VISIBLE : View.GONE);
            return cell;
        }
    };

    ToPcScreen(Context c, final Host host) {
        this.c = c;
        this.host = host;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int p = Ui.dp(c, 16);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(p, p, p, Ui.dp(c, 6));
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.label(c, "Zum PC", 22, Ui.text(c), true));
        titles.addView(Ui.label(c, "Fotos wählen oder andere Dateien suchen", 13, Ui.dim(c), false));
        head.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button other = Ui.button(c, "Dateien …", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Conn.connected(ToPcScreen.this.c)) {
                    host.pickFiles();
                } else {
                    host.show(Host.TAB_HOME);
                }
            }
        });
        other.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 112), Ui.dp(c, 40)));
        head.addView(other);
        root.addView(head);

        body = new FrameLayout(c);
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        footer = new LinearLayout(c);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(Ui.surface(c));
        footer.setElevation(Ui.dp(c, 6));
        footer.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        footerText = Ui.label(c, "", 15, Ui.text(c), true);
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
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        state = "";
        refresh();
    }

    @Override
    public void refresh() {
        boolean connected = Conn.connected(c);
        boolean access = host.hasMediaAccess();
        String now = connected + "|" + access;
        if (!now.equals(state)) {
            state = now;
            rebuild(connected, access);
        }
        footer.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        footerText.setText(selected.size() + (selected.size() == 1 ? " Foto" : " Fotos"));
    }

    private void rebuild(boolean connected, boolean access) {
        body.removeAllViews();
        grid = null;
        if (!connected) {
            body.addView(message("Nicht mit dem PC verbunden", "Verbinde dich zuerst auf der Startseite.",
                    "Zur Startseite", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            host.show(Host.TAB_HOME);
                        }
                    }));
            return;
        }
        if (!access) {
            body.addView(message("Zugriff auf Fotos", "Damit die App deine Fotos hier zeigen kann, braucht sie Zugriff auf die "
                    + "Fotos. Die Fotos bleiben auf dem Handy, bis du sie sendest.", "Zugriff erlauben",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            host.requestMediaAccess();
                        }
                    }));
            return;
        }
        grid = new GridView(c);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(Ui.dp(c, 3));
        grid.setVerticalSpacing(Ui.dp(c, 3));
        grid.setPadding(Ui.dp(c, 3), 0, Ui.dp(c, 3), Ui.dp(c, 3));
        grid.setClipToPadding(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                Long key = ids.get(position);
                if (!selected.remove(key)) {
                    selected.add(key);
                }
                adapter.notifyDataSetChanged();
                refresh();
            }
        });
        body.addView(grid);
        TextView none = Ui.label(c, "Keine Fotos gefunden.", 15, Ui.dim(c), false);
        none.setGravity(Gravity.CENTER);
        none.setVisibility(View.GONE);
        none.setTag("none");
        body.addView(none, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        loadImages(none);
    }

    private View message(String title, String text, String button, View.OnClickListener l) {
        LinearLayout card = Ui.card(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = Ui.label(c, title, 18, Ui.text(c), true);
        card.addView(t);
        TextView d = Ui.label(c, text, 14, Ui.dim(c), false);
        d.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        card.addView(d);
        card.addView(Ui.button(c, button, true, l));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(c, 16);
        lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);
        return card;
    }

    /** Die neuesten Fotos (höchstens 300), im Hintergrund gelesen. */
    private void loadImages(final TextView none) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Long> found = new ArrayList<>();
                Bundle args = new Bundle();
                args.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 300);
                args.putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        new String[] { MediaStore.Images.Media.DATE_ADDED });
                args.putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
                try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[] { MediaStore.Images.Media._ID }, args, null)) {
                    while (cur != null && cur.moveToNext()) {
                        found.add(cur.getLong(0));
                    }
                } catch (Exception ignored) {
                }
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        ids.clear();
                        ids.addAll(found);
                        selected.clear();
                        adapter.notifyDataSetChanged();
                        none.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }, "nolphin-images").start();
    }

    private void sendSelected() {
        List<Uri> uris = new ArrayList<>();
        for (Long id : selected) {
            uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
        }
        selected.clear();
        adapter.notifyDataSetChanged();
        refresh();
        host.send(uris);
    }
}
