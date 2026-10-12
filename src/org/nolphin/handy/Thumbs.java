package org.nolphin.handy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.os.ParcelFileDescriptor;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.File;
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
        run(key, view, fallback, new Source() {
            @Override
            public Bitmap get() throws Exception {
                return c.getContentResolver().loadThumbnail(uri, new Size(px, px), null);
            }
        });
    }

    private interface Source {
        Bitmap get() throws Exception;
    }

    /** Vorschaubild einer Datei im Dateisystem. @p kind: image, video, audio (Cover), pdf (erste Seite) oder apk (App-Symbol). */
    static void loadFile(final Context c, final File file, final String kind, final int px, final ImageView view,
                         final Runnable fallback) {
        final String key = file.getPath() + "#" + file.lastModified() + "#" + px;
        view.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            show(view, key, hit, fallback);
            return;
        }
        run(key, view, fallback, new Source() {
            @Override
            public Bitmap get() throws Exception {
                switch (kind) {
                    case "video":
                        return ThumbnailUtils.createVideoThumbnail(file, new Size(px, px), null);
                    case "audio":
                        return cover(file, px);
                    case "pdf":
                        return firstPage(file, px);
                    case "apk":
                        return appIcon(c, file, px);
                    default:
                        return ThumbnailUtils.createImageThumbnail(file, new Size(px, px), null);
                }
            }
        });
    }

    private static Bitmap cover(File file, int px) throws Exception {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getPath());
            byte[] pic = r.getEmbeddedPicture();
            if (pic == null) {
                throw new Exception("kein Cover");
            }
            Bitmap b = BitmapFactory.decodeByteArray(pic, 0, pic.length);
            if (b == null) {
                throw new Exception("kein Cover");
            }
            return Bitmap.createScaledBitmap(b, px, px, true);
        } finally {
            r.release();
        }
    }

    private static Bitmap firstPage(File file, int px) throws Exception {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer r = new PdfRenderer(fd)) {
            if (r.getPageCount() == 0) {
                throw new Exception("leer");
            }
            try (PdfRenderer.Page page = r.openPage(0)) {
                Bitmap b = Bitmap.createBitmap(px, Math.max(1, px * page.getHeight() / page.getWidth()),
                        Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return b;
            }
        }
    }

    private static Bitmap appIcon(Context c, File file, int px) throws Exception {
        PackageManager pm = c.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(file.getPath(), 0);
        if (info == null || info.applicationInfo == null) {
            throw new Exception("keine App");
        }
        info.applicationInfo.sourceDir = file.getPath();
        info.applicationInfo.publicSourceDir = file.getPath();
        Drawable d = info.applicationInfo.loadIcon(pm);
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(canvas);
        return b;
    }

    private static void run(final String key, final ImageView view, final Runnable fallback, final Source source) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b;
                try {
                    b = source.get();
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
            view.setPadding(0, 0, 0, 0);
            // Symbole mit Transparenz (App-Symbole) ganz zeigen, Fotos füllen die Fläche
            view.setScaleType(b.hasAlpha() ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(b);
        }
    }
}
