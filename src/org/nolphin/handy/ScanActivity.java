package org.nolphin.handy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scannt den QR-Code von Nolphin mit der Kamera. Der Code wird mit dem eigenen
 * QR-Leser (QrDecoder) gelesen, ohne Bibliothek und ohne Google-Dienste.
 */
@SuppressWarnings("deprecation")
public class ScanActivity extends Activity implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    private static final int REQUEST_CAMERA = 11;

    private TextureView texture;
    private TextView hint;
    private TextView diag;
    private int frames;
    private Camera camera;
    private Camera.Size previewSize;
    private String focusMode;
    private final android.os.Handler focusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable focusTick = new Runnable() {
        @Override
        public void run() {
            focusNow();
            focusHandler.postDelayed(this, 2500);
        }
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean done;
    private String lastRejected = "";
    private long rejectedAt;
    private volatile boolean verifying;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        texture = new TextureView(this);
        texture.setSurfaceTextureListener(this);
        texture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                focusNow();
                Toast.makeText(ScanActivity.this, "Scharfstellen …", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(texture, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        hint = new TextView(this);
        hint.setText("Richte die Kamera auf den QR-Code am PC\n(Nolphin, Seitenleiste „Handy“)");
        hint.setTextColor(0xFFFFFFFF);
        hint.setBackgroundColor(0x99000000);
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(24, 24, 24, 24);
        root.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        diag = new TextView(this);
        diag.setTextColor(0xFFAAAAAA);
        diag.setTextSize(12);
        diag.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        dlp.bottomMargin = 200;
        root.addView(diag, dlp);

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        if (request == REQUEST_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                if (texture.isAvailable()) {
                    openCamera(texture.getSurfaceTexture());
                }
            } else {
                Toast.makeText(this, "Ohne Kamera kann der QR-Code nicht gescannt werden. Du kannst den Link auch einfügen.",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ---- Kamera ----------------------------------------------------------------------------

    private void openCamera(SurfaceTexture surface) {
        if (camera != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            camera = Camera.open();
            Camera.Parameters p = camera.getParameters();
            previewSize = bestSize(p.getSupportedPreviewSizes());
            p.setPreviewSize(previewSize.width, previewSize.height);
            // Barcode-Szene (wenn vorhanden): Nahfokus und Kontrast für Codes
            List<String> scenes = p.getSupportedSceneModes();
            if (scenes != null && scenes.contains(Camera.Parameters.SCENE_MODE_BARCODE)) {
                p.setSceneMode(Camera.Parameters.SCENE_MODE_BARCODE);
            }
            List<String> focus = p.getSupportedFocusModes();
            focusMode = null;
            if (focus != null) {
                if (focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                } else if (focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                } else if (focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
                }
            }
            if (focusMode != null) {
                p.setFocusMode(focusMode);
            }
            if (p.getMaxNumFocusAreas() > 0) {
                java.util.ArrayList<Camera.Area> areas = new java.util.ArrayList<>();
                areas.add(new Camera.Area(new android.graphics.Rect(-300, -300, 300, 300), 1000));   // Bildmitte
                p.setFocusAreas(areas);
            }
            p.setPreviewFormat(android.graphics.ImageFormat.NV21);
            camera.setParameters(p);
            previewSize = camera.getParameters().getPreviewSize();   // was die Kamera wirklich liefert
            camera.setDisplayOrientation(displayOrientation());
            camera.setPreviewTexture(surface);
            camera.setPreviewCallback(this);
            fitPreview();
            camera.startPreview();
            camera.cancelAutoFocus();   // startet den fortlaufenden Fokus
            startFocusLoop();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, "Kamera nicht verfügbar", Toast.LENGTH_LONG).show();
            closeCamera();
            finish();
        }
    }

    /** Vorschaugröße nahe 1920x1080: genug Auflösung auch für einen kleinen Code in größerem Abstand. */
    private static Camera.Size bestSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        long bestScore = Long.MAX_VALUE;
        for (Camera.Size s : sizes) {
            long score = Math.abs((long) s.width * s.height - 1920L * 1080L);
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private int displayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation * 90;
        return (info.orientation - degrees + 360) % 360;
    }

    /** Das Bild nicht verzerren: Vorschau im Seitenverhältnis der Kamera in die Mitte setzen. */
    private void fitPreview() {
        final int pw = previewSize.width, ph = previewSize.height;
        texture.post(new Runnable() {
            @Override
            public void run() {
                View parent = (View) texture.getParent();
                int w = parent.getWidth(), h = parent.getHeight();
                boolean portrait = h >= w;
                double aspect = portrait ? (double) pw / ph : (double) ph / pw;   // Höhe / Breite im Bild
                int viewW = w;
                int viewH = (int) Math.round(w * (portrait ? aspect : 1 / aspect));
                if (viewH > h) {
                    viewH = h;
                    viewW = (int) Math.round(h / (portrait ? aspect : 1 / aspect));
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(viewW, viewH, Gravity.CENTER);
                texture.setLayoutParams(lp);
            }
        });
    }

    /** Löst den Autofokus aus (alle paar Sekunden und beim Antippen des Bildes). */
    private void focusNow() {
        Camera c = camera;
        if (c == null || focusMode == null) {
            return;
        }
        try {
            c.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera cam) {
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void startFocusLoop() {
        focusHandler.removeCallbacks(focusTick);
        // Bei festem Auto-Modus muss der Fokus regelmäßig neu angestoßen werden; fortlaufende Modi auch zur Sicherheit
        focusHandler.postDelayed(focusTick, 1500);
    }

    private void closeCamera() {
        focusHandler.removeCallbacks(focusTick);
        if (camera != null) {
            camera.setPreviewCallback(null);
            try {
                camera.stopPreview();
            } catch (RuntimeException ignored) {
            }
            camera.release();
            camera = null;
        }
    }

    // ---- Bilder lesen ----------------------------------------------------------------------

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        if (done || previewSize == null || !busy.compareAndSet(false, true)) {
            return;   // läuft noch ein Lesevorgang: dieses Bild überspringen
        }
        final int w = previewSize.width, h = previewSize.height;
        if (data.length < w * h) {
            busy.set(false);
            return;
        }
        // NV21: die ersten w*h Bytes sind die Helligkeit, also schon ein Graustufenbild
        final byte[] gray = Arrays.copyOf(data, w * h);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final long t0 = System.nanoTime();
                    final String text = QrDecoder.decodeMultiScale(gray, w, h);
                    final long ms = (System.nanoTime() - t0) / 1000000;
                    final int seen = QrDecoder.lastFinderCount;
                    frames++;
                    final int count = frames;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            diag.setText("Kamerabild " + w + "×" + h + " · gelesen: " + count + " · Suchmuster: " + seen + " · " + ms + " ms");
                        }
                    });
                    if (text == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showProgress(seen);
                            }
                        });
                    }
                    if (text != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                accept(text);
                            }
                        });
                    }
                } finally {
                    busy.set(false);
                }
            }
        });
    }

    /** Sagt dem Nutzer, was die Kamera sieht, damit er das Handy richtig halten kann. */
    private void showProgress(int finders) {
        if (done || verifying || !lastRejected.isEmpty()) {
            return;
        }
        if (QrDecoder.lastTriple) {
            hint.setText("QR-Code im Bild, aber noch unscharf.\nAbstand ändern (etwa 20 bis 30 cm), ruhig halten, aufs Bild tippen zum Scharfstellen");
        } else {
            hint.setText("Richte die Kamera auf den QR-Code am PC\n(Nolphin, Seitenleiste „Handy“). Zum Scharfstellen aufs Bild tippen");
        }
    }

    private void accept(final String text) {
        if (done || verifying) {
            return;
        }
        if (text.equals(lastRejected) && System.currentTimeMillis() - rejectedAt < 4000) {
            return;   // derselbe abgelehnte Code: nicht bei jedem Bild erneut prüfen
        }
        verifying = true;
        hint.setText("QR-Code gelesen. Prüfe die Verbindung zum PC …");
        Connector.connect(this, text, new Connector.Listener() {
            @Override
            public void onSuccess(Link link) {
                done = true;
                Toast.makeText(ScanActivity.this, "Mit dem PC verbunden", Toast.LENGTH_SHORT).show();
                startForegroundService(new Intent(ScanActivity.this, SyncService.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                verifying = false;
                lastRejected = text;
                rejectedAt = System.currentTimeMillis();
                hint.setText(message + "\n\nRichte die Kamera auf den aktuellen QR-Code am PC.");
            }
        });
    }

    // ---- Lebenszyklus -----------------------------------------------------------------------

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        closeCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (texture.isAvailable() && camera == null) {
            openCamera(texture.getSurfaceTexture());
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
