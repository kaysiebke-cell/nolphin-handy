package org.nolphin.handy;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lädt Vorschaubilder im Hintergrund und merkt sie sich. Das Framework erzeugt sie (ContentResolver.loadThumbnail). */
final class Thumbs {
    private Thumbs() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Bitmap NONE = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Zeigt das Vorschaubild von @p uri in @p view; ohne Vorschau bleibt @p view unverändert. */
    static void load(final Context c, final Uri uri, final int px, final ImageView view,
                     final Runnable fallback) {
        final String key = uri + "#" + px;
        view.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            show(view, key, hit, fallback);
            return;
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b;
                try {
                    b = c.getContentResolver().loadThumbnail(uri, new Size(px, px), null);
                } catch (Exception e) {
                    b = NONE;
                }
                CACHE.put(key, b);
                final Bitmap result = b;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        show(view, key, result, fallback);
                    }
                });
            }
        });
    }

    private static void show(ImageView view, String key, Bitmap b, Runnable fallback) {
        if (!key.equals(view.getTag())) {
            return;   // die Zeile wurde inzwischen für eine andere Datei verwendet
        }
        if (b == NONE) {
            fallback.run();
        } else {
            view.clearColorFilter();
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(b);
        }
    }
}
