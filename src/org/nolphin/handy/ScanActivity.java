package org.nolphin.handy;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
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
    private Camera camera;               // Rückfall: alte Kamera-Schnittstelle
    private int pw, ph;                  // Größe der gelesenen Bilder
    private CameraDevice cam2;
    private CameraCaptureSession session;
    private CaptureRequest.Builder builder;
    private ImageReader reader;
    private HandlerThread camThread;
    private Handler camHandler;
    private CameraCharacteristics chars;
    private boolean opening;
    private float lastTapX = 0.5f, lastTapY = 0.5f;
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
        texture.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN && v.getWidth() > 0 && v.getHeight() > 0) {
                    lastTapX = e.getX() / v.getWidth();
                    lastTapY = e.getY() / v.getHeight();
                }
                return false;
            }
        });
        texture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ScanActivity.this, "Scharfstellen …", Toast.LENGTH_SHORT).show();
                if (cam2 != null) {
                    focusAt(lastTapX, lastTapY);
                } else {
                    focusNow();
                }
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
        if (camera != null || cam2 != null || opening
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!openCamera2(surface)) {
            openCameraLegacy(surface);
        }
    }

    /** Camera2: fortlaufender Autofokus und Scharfstellen an der angetippten Stelle. Gibt false zurück, wenn es nicht geht. */
    private boolean openCamera2(final SurfaceTexture surface) {
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            String id = null;
            for (String candidate : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(candidate);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    id = candidate;
                    chars = c;
                    break;
                }
            }
            if (id == null) {
                return false;
            }
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return false;
            }
            Size size = bestSize2(map.getOutputSizes(ImageFormat.YUV_420_888), map.getOutputSizes(SurfaceTexture.class));
            if (size == null) {
                return false;
            }
            pw = size.getWidth();
            ph = size.getHeight();
            camThread = new HandlerThread("scan-camera");
            camThread.start();
            camHandler = new Handler(camThread.getLooper());
            opening = true;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    cam2 = device;
                    startSession(surface);
                }

                @Override
                public void onDisconnected(CameraDevice device) {
                    device.close();
                    cam2 = null;
                    opening = false;
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    device.close();
                    cam2 = null;
                    opening = false;
                    fallBack(surface);
                }
            }, camHandler);
            return true;
        } catch (CameraAccessException | RuntimeException e) {
            opening = false;
            closeCamera2();
            return false;
        }
    }

    private void fallBack(final SurfaceTexture surface) {
        closeCamera2();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!done) {
                    openCameraLegacy(surface);
                }
            }
        });
    }

    private void startSession(final SurfaceTexture surface) {
        try {
            surface.setDefaultBufferSize(pw, ph);
            final Surface preview = new Surface(surface);
            reader = ImageReader.newInstance(pw, ph, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader r) {
                    Image img = r.acquireLatestImage();
                    if (img == null) {
                        return;
                    }
                    try {
                        if (done || !busy.compareAndSet(false, true)) {
                            return;
                        }
                        final int w = img.getWidth(), h = img.getHeight();
                        Image.Plane plane = img.getPlanes()[0];   // Y = Helligkeit = Graustufenbild
                        ByteBuffer buf = plane.getBuffer();
                        int stride = plane.getRowStride();
                        byte[] gray = new byte[w * h];
                        for (int y = 0; y < h; y++) {
                            buf.position(y * stride);
                            buf.get(gray, y * w, w);
                        }
                        submit(gray, w, h);
                    } catch (RuntimeException e) {
                        busy.set(false);
                    } finally {
                        img.close();
                    }
                }
            }, camHandler);
            cam2.createCaptureSession(Arrays.asList(preview, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession s) {
                    if (cam2 == null) {
                        return;
                    }
                    session = s;
                    try {
                        builder = cam2.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(preview);
                        builder.addTarget(reader.getSurface());
                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        // Kurze Belichtung gegen Verwackeln: Bildrate möglichst fest auf 30 (Handy in der Hand, dunkler Raum)
                        android.util.Range<Integer>[] fps = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                        android.util.Range<Integer> pick = null;
                        if (fps != null) {
                            for (android.util.Range<Integer> r : fps) {
                                if (r.getUpper() >= 30 && (pick == null || r.getLower() > pick.getLower()
                                        || r.getLower() == pick.getLower() && r.getUpper() < pick.getUpper())) {
                                    pick = r;
                                }
                            }
                        }
                        if (pick != null) {
                            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, pick);
                        }
                        if (hasMode(chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES), CaptureRequest.EDGE_MODE_HIGH_QUALITY)) {
                            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                        }
                        int[] modes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                        if (hasMode(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        } else if (hasMode(modes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        }
                        s.setRepeatingRequest(builder.build(), null, camHandler);
                        opening = false;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fitPreview();
                            }
                        });
                    } catch (CameraAccessException | RuntimeException e) {
                        fallBack(surface);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession s) {
                    fallBack(surface);
                }
            }, camHandler);
        } catch (CameraAccessException | RuntimeException e) {
            fallBack(surface);
        }
    }

    private static boolean hasMode(int[] modes, int mode) {
        if (modes != null) {
            for (int m : modes) {
                if (m == mode) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Eine Größe nahe 1920x1080, die Bild und Vorschau beide liefern können. */
    private static Size bestSize2(Size[] yuv, Size[] preview) {
        Size best = null;
        long bestScore = Long.MAX_VALUE;
        if (yuv == null) {
            return null;
        }
        for (Size s : yuv) {
            boolean ok = preview == null;
            if (preview != null) {
                for (Size p : preview) {
                    if (p.equals(s)) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok || s.getWidth() * 9L != s.getHeight() * 16L && s.getWidth() * 3L != s.getHeight() * 4L) {
                continue;   // nur 16:9 oder 4:3
            }
            long score = Math.abs((long) s.getWidth() * s.getHeight() - 1920L * 1080L);
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** Scharfstellen an der angetippten Stelle (Koordinaten 0 bis 1 in der Ansicht, Hochformat). */
    private void focusAt(float nx, float ny) {
        final CameraCaptureSession s = session;
        final CaptureRequest.Builder b = builder;
        if (s == null || b == null || chars == null) {
            return;
        }
        try {
            Rect active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer o = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (active == null) {
                return;
            }
            int orientation = o == null ? 90 : o;
            float fx, fy;   // Punkt im Sensor, 0 bis 1
            switch (orientation) {
                case 0:
                    fx = nx; fy = ny; break;
                case 180:
                    fx = 1 - nx; fy = 1 - ny; break;
                case 270:
                    fx = 1 - ny; fy = nx; break;
                default:
                    fx = ny; fy = 1 - nx; break;
            }
            int half = Math.max(active.width(), active.height()) / 14;
            int cx = active.left + Math.round(fx * active.width());
            int cy = active.top + Math.round(fy * active.height());
            Rect r = new Rect(Math.max(active.left, cx - half), Math.max(active.top, cy - half),
                    Math.min(active.right - 1, cx + half), Math.min(active.bottom - 1, cy + half));
            MeteringRectangle[] area = { new MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX - 1) };
            Integer maxAf = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            if (maxAf != null && maxAf > 0) {
                b.set(CaptureRequest.CONTROL_AF_REGIONS, area);
            }
            Integer maxAe = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            if (maxAe != null && maxAe > 0) {
                b.set(CaptureRequest.CONTROL_AE_REGIONS, area);
            }
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            s.capture(b.build(), null, camHandler);
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            if (hasMode(chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), CaptureRequest.CONTROL_AF_MODE_AUTO)
                    && b.get(CaptureRequest.CONTROL_AF_MODE) == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                s.capture(b.build(), null, camHandler);
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            }
            s.setRepeatingRequest(b.build(), null, camHandler);
        } catch (CameraAccessException | RuntimeException ignored) {
        }
    }

    private void closeCamera2() {
        try {
            if (session != null) {
                session.close();
            }
            if (cam2 != null) {
                cam2.close();
            }
            if (reader != null) {
                reader.close();
            }
        } catch (RuntimeException ignored) {
        }
        session = null;
        cam2 = null;
        reader = null;
        builder = null;
        if (camThread != null) {
            camThread.quitSafely();
            camThread = null;
            camHandler = null;
        }
    }

    private void openCameraLegacy(SurfaceTexture surface) {
        if (camera != null) {
            return;
        }
        try {
            camera = Camera.open();
            Camera.Parameters p = camera.getParameters();
            Camera.Size chosen = bestSize(p.getSupportedPreviewSizes());
            p.setPreviewSize(chosen.width, chosen.height);
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
            Camera.Size real = camera.getParameters().getPreviewSize();   // was die Kamera wirklich liefert
            pw = real.width;
            ph = real.height;
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
        final int pw = this.pw, ph = this.ph;
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
        closeCamera2();
        opening = false;
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
        if (done || pw == 0 || !busy.compareAndSet(false, true)) {
            return;   // läuft noch ein Lesevorgang: dieses Bild überspringen
        }
        final int w = pw, h = ph;
        if (data.length < w * h) {
            busy.set(false);
            return;
        }
        // NV21: die ersten w*h Bytes sind die Helligkeit, also schon ein Graustufenbild
        submit(Arrays.copyOf(data, w * h), w, h);
    }

    /** Liest ein Graustufenbild im Hintergrund; der Aufrufer hat busy schon gesetzt. */
    private void submit(final byte[] gray, final int w, final int h) {
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
        if (texture.isAvailable() && camera == null && cam2 == null) {
            openCamera(texture.getSurfaceTexture());
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
