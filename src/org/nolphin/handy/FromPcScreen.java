package org.nolphin.handy;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** PC → Handy: die Dateien im Ordner „Handy“ am PC; geladene lassen sich öffnen und teilen. */
final class FromPcScreen implements Screen {
    private final Context c;
    private final Host host;
    private final LinearLayout root;
    private final ListView list;
    private final TextView empty;
    private RemoteFile[] data = new RemoteFile[0];
    private String signature = "";

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return data.length;
        }

        @Override
        public Object getItem(int i) {
            return data[i];
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            return row(data[i]);
        }
    };

    FromPcScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int p = Ui.dp(c, 16);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(p, p, p, Ui.dp(c, 6));
        head.addView(Ui.label(c, "Vom PC", 22, Ui.text(c), true));
        head.addView(Ui.label(c, "Dateien aus dem Ordner „Handy“ am PC", 13, Ui.dim(c), false));
        root.addView(head);
        list = new ListView(c);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                open(data[position]);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        empty = Ui.label(c, "Noch nichts vom PC.\nLege am PC Dateien in den Ordner „Handy“ oder ziehe sie in Nolphin auf „Handy“.",
                15, Ui.dim(c), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(p * 2, p * 3, p * 2, p);
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
        RemoteFile[] now = SyncService.files;
        StringBuilder sb = new StringBuilder();
        for (RemoteFile f : now) {
            sb.append(f.key()).append(Store.uri(c, f.key()) != null ? '+' : '-').append(';');
        }
        sb.append(Conn.connected(c));
        if (!sb.toString().equals(signature)) {
            signature = sb.toString();
            data = now;
            adapter.notifyDataSetChanged();
        }
        boolean connected = Conn.connected(c);
        empty.setVisibility(data.length == 0 ? View.VISIBLE : View.GONE);
        list.setVisibility(data.length == 0 ? View.GONE : View.VISIBLE);
        if (!connected) {
            empty.setText("Nicht mit dem PC verbunden.\nVerbinde dich zuerst auf der Startseite.");
        }
    }

    private View row(final RemoteFile f) {
        final String uri = Store.uri(c, f.key());
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(c, Ui.surface(c), 14));
        card.setElevation(Ui.dp(c, 1));
        int p = Ui.dp(c, 12);
        card.setPadding(p, p, p, p);

        final ImageView thumb = new ImageView(c);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
        GradientDrawable bg = Ui.round(c, Ui.chip(c), 10);
        thumb.setBackground(bg);
        thumb.setClipToOutline(true);
        thumb.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        final Runnable icon = new Runnable() {
            @Override
            public void run() {
                thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                thumb.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
                thumb.setImageResource(R.drawable.ic_file);
                thumb.setColorFilter(Ui.primary(c));
            }
        };
        icon.run();
        if (uri != null) {
            thumb.setPadding(0, 0, 0, 0);
            Thumbs.load(c, Uri.parse(uri), 168, thumb, icon);
        }
        card.addView(thumb);

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        TextView name = Ui.label(c, f.name, 15, Ui.text(c), false);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(name);
        text.addView(Ui.label(c, Ui.size(f.size) + (uri != null ? "  ·  auf dem Handy" : "  ·  noch nicht geladen"), 12,
                uri != null ? Ui.accent(c) : Ui.dim(c), false));
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (uri != null) {
            ImageView share = Ui.icon(c, R.drawable.ic_share, Ui.primary(c), 24);
            share.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
            share.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
            share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    share(f, uri);
                }
            });
            card.addView(share);
        } else {
            ImageView load = Ui.icon(c, R.drawable.ic_download, Ui.primary(c), 24);
            load.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
            load.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
            load.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.fetch(f);
                    Toast.makeText(c, "Wird geladen …", Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(load);
        }
        wrap.addView(card);
        return wrap;
    }

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
            Toast.makeText(c, "Keine App zum Öffnen gefunden. Du kannst die Datei teilen.", Toast.LENGTH_LONG).show();
        }
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
